package net.righthandedvoid.imperfectnpcs.scripting.lua;

import com.mojang.logging.LogUtils;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptManager;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptRecord;
import org.slf4j.Logger;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.compiler.LuaC;
import org.squiddev.cobalt.lib.Bit32Lib;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Compiles and runs Lua scripts with Cobalt.
 *
 * Every run gets its own LuaState, so scripts can't leave anything behind
 * for each other. Scripts run on the server thread; a watchdog thread stops
 * any run that takes longer than TIME_LIMIT_MS.
 */
public final class LuaScriptRunner {
    private static final Logger logger = LogUtils.getLogger();

    /**
     * How long one script run may take, in milliseconds. The server has
     * 50ms per tick in total, so this should stay small.
     */
    private static final long TIME_LIMIT_MS = 50;

    /**
     * One background thread whose only job is to interrupt scripts that run
     * too long. It never runs Lua itself: LuaState.interrupt() just sets a
     * flag that the script notices at its next safe point.
     */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ImperfectNPCs Lua watchdog");
        thread.setDaemon(true);

        return thread;
    });

    /** Compiled scripts. A Prototype is only bytecode, so every run can reuse it. */
    private final Map<String, Prototype> scriptCache = new HashMap<>();

    /**
     * Thrown when a script runs out of time. It extends Error rather than
     * LuaError on purpose: Cobalt's pcall catches LuaError (and stack
     * overflows), but not other Errors, so a script can't wrap an infinite
     * loop in pcall to escape the limit.
     */
    static final class ScriptTimeoutError extends Error {
        ScriptTimeoutError(String scriptName) {
            super(scriptName + " took longer than " + TIME_LIMIT_MS + "ms and was stopped.");
        }
    }

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

        // The watchdog only calls interrupt() once time is up, so the handler
        // can stop the script unconditionally.
        LuaState state = LuaState.builder()
                .interruptHandler(() -> {
                    throw new ScriptTimeoutError(scriptName);
                })
                .build();

        ScheduledFuture<?> watchdog = WATCHDOG.schedule(state::interrupt, TIME_LIMIT_MS, TimeUnit.MILLISECONDS);

        try {
            LuaTable globals = createGlobals(state, context);

            // The script runs as a coroutine (a LuaThread). In Cobalt that's
            // NOT a Java thread; it runs right here on the server thread.
            // Running scripts as coroutines is what will let wait() pause
            // them in the next step.
            LuaThread thread = new LuaThread(state, LoadState.interpretedFunction(prototype, globals));
            LuaThread.run(thread, Constants.NONE);

            if (thread.isAlive()) {
                // The script called coroutine.yield() at its top level.
                // There's nothing to resume it yet, so report that.
                return new ScriptRecord(false, scriptName + " yielded, but pausing scripts isn't supported yet");
            }

        } catch (ScriptTimeoutError e) {
            logger.warn("Lua script timeout: {}", e.getMessage());

            return new ScriptRecord(false, e.getMessage());

        } catch (LuaError e) {
            String message = String.valueOf(e.getMessage()).split("\n")[0];

            logger.warn("Lua script error: {}", message);

            return new ScriptRecord(false, "Lua script error: " + message);

        } catch (RuntimeException e) {
            logger.error("Unexpected error in {}", scriptName, e);

            return new ScriptRecord(false, "Internal error: " + e);

        } finally {
            watchdog.cancel(false);
        }

        return new ScriptRecord(true, "Successfully ran " + scriptName);
    }

    /** Builds the global environment a script sees. */
    private static LuaTable createGlobals(LuaState state, ScriptContext context) throws LuaError {
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