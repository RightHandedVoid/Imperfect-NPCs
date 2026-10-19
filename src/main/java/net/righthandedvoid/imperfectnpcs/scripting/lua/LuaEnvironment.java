package net.righthandedvoid.imperfectnpcs.scripting.lua;

import com.mojang.logging.LogUtils;
import net.righthandedvoid.imperfectnpcs.api.ScriptNPC;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import org.slf4j.Logger;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.interrupt.InterruptAction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One LuaState and everything that belongs to it: its globals, where its
 * output goes, and the time limit.
 *
 * A script run from a command gets its own environment. A script attached to
 * an NPC gets one environment that lives as long as the NPC is loaded, and
 * every event handler call runs as a separate coroutine inside it, so local
 * variables at the top of the script are shared between events.
 */
final class LuaEnvironment {
    private static final Logger logger = LogUtils.getLogger();

    /**
     * How long one stretch of a script (from its start or a wait() until its
     * next wait() or the end) may run, in milliseconds. The server has 50ms
     * per tick in total, so this should stay small.
     */
    static final long TIME_LIMIT_MS = 50;

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

    enum Status { FINISHED, WAITING, FAILED }

    /** What happened during one resume. waitTicks is only meaningful for WAITING. */
    record Result(Status status, String message, int waitTicks) {
        static final Result FINISHED = new Result(Status.FINISHED, "", 0);
    }

    /**
     * Thrown when a script runs out of time. It extends Error rather than
     * LuaError on purpose: Cobalt's pcall catches LuaError (and stack
     * overflows), but not other Errors, so a script can't wrap an infinite
     * loop in pcall to escape the limit.
     */
    static final class ScriptTimeoutError extends Error {
        ScriptTimeoutError(String scriptName) {
            super(scriptName + " ran for longer than " + TIME_LIMIT_MS + "ms without calling wait() and was stopped.");
        }
    }

    private final String scriptName;
    private final ScriptContext context;
    private final LuaState state;
    private final LuaTable globals;
    private final LuaTable npcMetatable;

    /**
     * wait() yields this table so we can tell a real wait() apart from a
     * script calling coroutine.yield() by hand. Only the wait() function
     * holds a reference to it, so scripts can't fake it.
     */
    private final LuaTable waitToken = new LuaTable();

    /** How many times anything in this environment has been resumed. */
    private long resumeCount = 0;

    /**
     * Which resume the watchdog ran out of time on. The watchdog thread
     * writes it, hence volatile. Comparing it with resumeCount means a late
     * interrupt left over from an earlier resume can't stop the current one.
     */
    private volatile long timedOutResume = -1;

    /** True while Lua code from this environment is running. A LuaState must never be resumed from inside itself. */
    private boolean running = false;

    /** Set once the environment is thrown away, e.g. because its NPC was removed. */
    private boolean closed = false;

    LuaEnvironment(String scriptName, ScriptContext context) throws LuaError {
        this.scriptName = scriptName;
        this.context = context;
        this.state = LuaState.builder()
                .interruptHandler(this::onInterrupt)
                .build();
        this.npcMetatable = LuaNPCLib.createMetatable();
        this.globals = LuaScriptRunner.createGlobals(state, context, waitToken, npcMetatable);
    }

    String getScriptName() { return scriptName; }

    ScriptContext getContext() { return context; }

    LuaTable getGlobals() { return globals; }

    boolean isRunning() { return running; }

    boolean isClosed() { return closed; }

    /** Marks the environment as dead. Anything still scheduled in it quietly stops. */
    void close() { closed = true; }

    /** A coroutine that runs a compiled script's top level. */
    LuaThread newThread(Prototype prototype) {
        return new LuaThread(state, LoadState.interpretedFunction(prototype, globals));
    }

    /** A coroutine that runs one function, such as an event handler. */
    LuaThread newThread(LuaFunction function) {
        return new LuaThread(state, function);
    }

    /**
     * Converts a value from the language-neutral API (ScriptEvent data and
     * the like) into a Lua value.
     */
    LuaValue toLua(Object value) {
        if (value == null) return Constants.NIL;
        if (value instanceof LuaValue lua) return lua;
        if (value instanceof String string) return ValueFactory.valueOf(string);
        if (value instanceof Boolean bool) return ValueFactory.valueOf(bool);
        if (value instanceof Number number) return ValueFactory.valueOf(number.doubleValue());
        if (value instanceof ScriptNPC npc) return LuaNPCLib.wrap(npc, npcMetatable);

        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                table.rawset(String.valueOf(entry.getKey()), toLua(entry.getValue()));
            }

            return table;
        }

        if (value instanceof List<?> list) {
            LuaTable table = new LuaTable(list.size(), 0);

            for (int i = 0; i < list.size(); i++) table.rawset(i + 1, toLua(list.get(i)));

            return table;
        }

        throw new IllegalArgumentException("Can't pass a " + value.getClass().getSimpleName() + " to Lua");
    }

    private InterruptAction onInterrupt() {
        if (timedOutResume == resumeCount) throw new ScriptTimeoutError(scriptName);

        // A stale interrupt from an earlier resume: ignore it.
        return InterruptAction.CONTINUE;
    }

    /** Runs a coroutine until it finishes, fails, or calls wait(). */
    Result resume(LuaThread thread, Varargs args) {
        // Its NPC is gone: let whatever was scheduled fade away silently.
        if (closed) return Result.FINISHED;

        if (running) throw new IllegalStateException(scriptName + " was resumed while it was already running");

        long thisResume = ++resumeCount;

        ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
            timedOutResume = thisResume;
            state.interrupt();
        }, TIME_LIMIT_MS, TimeUnit.MILLISECONDS);

        running = true;

        try {
            Varargs yielded = LuaThread.run(thread, args);

            if (!thread.isAlive() || closed) return Result.FINISHED;

            if (yielded != null && yielded.first() == waitToken) {
                return new Result(Status.WAITING, "", yielded.arg(2).toInteger());
            }

            return fail("Lua script error: scripts can only pause with wait(), not coroutine.yield()");

        } catch (ScriptTimeoutError e) {
            logger.warn("Lua script timeout: {}", e.getMessage());

            return new Result(Status.FAILED, e.getMessage(), 0);

        } catch (LuaError e) {
            String message = String.valueOf(e.getMessage()).split("\n")[0];

            logger.warn("Lua script error in {}: {}", scriptName, message);

            return new Result(Status.FAILED, "Lua script error: " + message, 0);

        } catch (RuntimeException | LinkageError e) {
            logger.error("Unexpected error in {}", scriptName, e);

            return new Result(Status.FAILED, "Internal error: " + e, 0);

        } finally {
            running = false;
            watchdog.cancel(false);
        }
    }

    private Result fail(String message) {
        logger.warn("{}: {}", scriptName, message);

        return new Result(Status.FAILED, message, 0);
    }
}