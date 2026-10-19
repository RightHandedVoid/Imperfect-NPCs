package net.righthandedvoid.imperfectnpcs.scripting.lua;

import com.mojang.logging.LogUtils;
import net.righthandedvoid.imperfectnpcs.api.ScriptNPC;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptEvent;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptManager;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptRecord;
import org.slf4j.Logger;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LuaC;
import org.squiddev.cobalt.lib.Bit32Lib;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Compiles Lua scripts and starts them.
 *
 * Every command run gets its own LuaState (see LuaEnvironment), so scripts
 * can't leave anything behind for each other. A script attached to an NPC
 * gets one LuaState for as long as the NPC is loaded (see LuaNpcInstance).
 * Anything that calls wait() is handed to the scheduler, which resumes it on
 * a later server tick.
 */
public final class LuaScriptRunner {
    private static final Logger logger = LogUtils.getLogger();

    /** Compiled scripts. A Prototype is only bytecode, so every run can reuse it. */
    private final Map<String, Prototype> scriptCache = new HashMap<>();

    private final LuaScheduler scheduler = new LuaScheduler();

    /** The running script instance of each NPC that has one, by NPC UUID. */
    private final Map<UUID, LuaNPCInstance> npcInstances = new HashMap<>();

    /** NPCs whose script failed to start, and which script it was. */
    private final Map<UUID, String> failedNpcScripts = new HashMap<>();

    public void clearScriptCache() {
        scriptCache.clear();
    }

    public ScriptRecord cacheScript(String scriptName) {
        String source = ScriptManager.getScriptSource(scriptName);

        if (source == null) return new ScriptRecord(false, scriptName + " has no source to compile");

        try {
            scriptCache.put(scriptName, compile(source, "@" + scriptName));

        } catch (CompileException | LuaError e) {
            logger.error("Error while compiling script {}: {}", scriptName, e.getMessage());

            return new ScriptRecord(false, e.getMessage());
        }

        return new ScriptRecord(true, scriptName + " compiled successfully");
    }

    public ScriptRecord run(String scriptName, ScriptContext context) {
        Prototype prototype = scriptCache.get(scriptName);

        if (prototype == null) {
            return new ScriptRecord(false, scriptName + " failed to previously compile during reload.");
        }

        if (scheduler.isFull()) {
            return new ScriptRecord(false, "Too many scripts are waiting already; not starting " + scriptName);
        }

        LuaEnvironment environment;

        try {
            environment = new LuaEnvironment(scriptName, context);

        } catch (LuaError e) {
            logger.error("Failed to set up Lua environment for {}", scriptName, e);

            return new ScriptRecord(false, "Internal error setting up " + scriptName + ": " + e.getMessage());
        }

        LuaScriptTask task = new LuaScriptTask(environment, environment.newThread(prototype), Constants.NONE);

        // The first stretch of the script runs right now, inside the command.
        LuaEnvironment.Result result = task.resume();

        return switch (result.status()) {
            case FINISHED -> new ScriptRecord(true, "Successfully ran " + scriptName);

            case WAITING -> {
                int currentTick = context.getServerTick();

                if (!scheduler.isTicking(currentTick)) {
                    // Without this check, the script would just never resume.
                    logger.error("The Lua scheduler isn't being ticked. Is ScriptManager.tick() called from ServerTickEvent.Post?");

                    yield new ScriptRecord(false, scriptName + " called wait(), but the script scheduler isn't running, so it could never resume.");
                }

                scheduler.add(task, currentTick);

                yield new ScriptRecord(true, "Started " + scriptName + " (it's now waiting)");
            }

            case FAILED -> new ScriptRecord(false, result.message());
        };
    }

    /** Called once per server tick; resumes scripts whose wait() is over. */
    public void tick(int currentTick) {
        scheduler.tick(currentTick);
    }

    /**
     * Stops every waiting script and throws away every NPC's script instance.
     * NPCs restart their scripts on their next tick, so after a reload they
     * pick up the new code. Returns how many waiting tasks were stopped.
     */
    public int cancelAllTasks() {
        npcInstances.values().forEach(LuaNPCInstance::close);
        npcInstances.clear();
        failedNpcScripts.clear();

        return scheduler.cancelAll();
    }

    // region NPC scripts

    /**
     * Called every tick for each NPC that has a script attached. Starts the
     * script the first time (running its top level and `init`), restarts it
     * if the NPC's script was changed, and runs any queued events.
     *
     * @param npcId      The NPC's UUID.
     * @param scriptName The script attached to the NPC.
     * @param npc        Makes the NPC handle; only called when starting.
     * @param context    Makes the script's context; only called when starting.
     */
    public void tickNpc(UUID npcId, String scriptName, Supplier<ScriptNPC> npc, Supplier<ScriptContext> context) {
        LuaNPCInstance instance = npcInstances.get(npcId);

        if (instance != null) {
            if (instance.getScriptName().equals(scriptName)) {
                instance.drainPending();
                return;
            }

            // The NPC was given a different script: start over.
            removeNpc(npcId);
        }

        // This script already failed to start on this NPC. Don't retry (and
        // spam the error) every tick; a reload or a new script resets this.
        if (scriptName.equals(failedNpcScripts.get(npcId))) return;

        ScriptContext scriptContext = context.get();
        Prototype prototype = scriptCache.get(scriptName);

        if (prototype == null) {
            failedNpcScripts.put(npcId, scriptName);

            scriptContext.printError(scriptName.endsWith(".lua")
                    ? scriptName + " couldn't start: it's missing or failed to compile (see /script errors)"
                    : scriptName + " couldn't start: only Lua scripts can be attached to NPCs so far");

            return;
        }

        try {
            instance = new LuaNPCInstance(scriptName, npc.get(), scriptContext, scheduler);

        } catch (LuaError e) {
            logger.error("Failed to set up Lua environment for {}", scriptName, e);
            failedNpcScripts.put(npcId, scriptName);

            return;
        }

        npcInstances.put(npcId, instance);

        if (!instance.start(prototype)) {
            removeNpc(npcId);
            failedNpcScripts.put(npcId, scriptName);
        }
    }

    /** Sends an event to an NPC's script. Ignored if the NPC has no running script. */
    public void fireNpcEvent(UUID npcId, ScriptEvent event) {
        LuaNPCInstance instance = npcInstances.get(npcId);

        if (instance != null) instance.fire(event);
    }

    /** Throws away an NPC's script instance, e.g. when the NPC unloads, dies or loses its script. */
    public void removeNpc(UUID npcId) {
        LuaNPCInstance instance = npcInstances.remove(npcId);

        if (instance != null) instance.close();

        failedNpcScripts.remove(npcId);
    }

    // endregion

    /** Builds the global environment a script sees. */
    static LuaTable createGlobals(LuaState state, ScriptContext context, LuaTable waitToken, LuaTable npcMetatable) throws LuaError {
        // Cobalt's standard libraries leave out io, os, print, dofile,
        // loadfile and collectgarbage entirely, and this LuaState has no
        // bytecode format set, so load() only accepts source code. That's
        // most of the sandboxing the LuaJ version had to do by hand.
        CoreLibraries.standardGlobals(state);
        Bit32Lib.add(state);

        LuaTable globals = state.globals();

        // Cobalt's own string.rep allows results up to 2 GB, which a script
        // could use to exhaust server memory. The string metatable points at
        // this same table, so ("x"):rep(n) uses the capped version too.
        globals.rawget("string").checkTable().rawset("rep", LuaStringRepFunction.create());

        LuaPrintFunction.install(state, globals, context);
        LuaWaitFunction.install(state, globals, waitToken);
        globals.rawset("npcs", LuaNPCLib.create(context, npcMetatable));

        return globals;
    }

    /** Compiles Lua source into a reusable Prototype. */
    static Prototype compile(String source, String chunkName) throws CompileException, LuaError {
        return LuaC.compile(
                new LuaState(),
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                chunkName
        );
    }
}