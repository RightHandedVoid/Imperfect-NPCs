package net.righthandedvoid.imperfectnpcs.scripting.lua;

import com.mojang.logging.LogUtils;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptManager;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptRecord;
import org.slf4j.Logger;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.Prototype;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LuaC;
import org.squiddev.cobalt.lib.Bit32Lib;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Compiles Lua scripts and starts them.
 *
 * Every run gets its own LuaState (see LuaScriptTask), so scripts can't leave
 * anything behind for each other. A script that calls wait() is handed to the
 * scheduler, which resumes it on a later server tick.
 */
public final class LuaScriptRunner {
    private static final Logger logger = LogUtils.getLogger();

    /** Compiled scripts. A Prototype is only bytecode, so every run can reuse it. */
    private final Map<String, Prototype> scriptCache = new HashMap<>();

    private final LuaScheduler scheduler = new LuaScheduler();

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

        LuaScriptTask task;

        try {
            task = new LuaScriptTask(scriptName, prototype, context);

        } catch (LuaError e) {
            logger.error("Failed to set up Lua environment for {}", scriptName, e);

            return new ScriptRecord(false, "Internal error setting up " + scriptName + ": " + e.getMessage());
        }

        // The first stretch of the script runs right now, inside the command.
        LuaScriptTask.Result result = task.resume();

        return switch (result.status()) {
            case FINISHED -> new ScriptRecord(true, "Successfully ran " + scriptName);

            case WAITING -> {
                scheduler.add(task, context.getServerTick());

                yield new ScriptRecord(true, "Started " + scriptName + " (it's now waiting)");
            }

            case FAILED -> new ScriptRecord(false, result.message());
        };
    }

    /** Called once per server tick; resumes scripts whose wait() is over. */
    public void tick(int currentTick) {
        scheduler.tick(currentTick);
    }

    /** Stops every waiting script. Returns how many were stopped. */
    public int cancelAllTasks() {
        return scheduler.cancelAll();
    }

    /** Builds the global environment a script sees. */
    static LuaTable createGlobals(LuaState state, ScriptContext context, LuaTable waitToken) throws LuaError {
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
        globals.rawset("npcs", LuaNPCLib.create(context));

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