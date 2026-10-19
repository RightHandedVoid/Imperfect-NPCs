package net.righthandedvoid.imperfectnpcs.scripting.lua;

import com.mojang.logging.LogUtils;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import org.slf4j.Logger;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.interrupt.InterruptAction;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One running Lua script: its LuaState, the coroutine its code runs in, and
 * where its output goes. A task is resumed once when the script starts, and
 * again each time a wait() it called is over.
 */
final class LuaScriptTask {
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

    record Result(Status status, String message) {
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
    private final LuaThread thread;

    /**
     * wait() yields this table so the scheduler can tell a real wait() apart
     * from a script calling coroutine.yield() by hand. Only the wait()
     * function holds a reference to it, so scripts can't fake it.
     */
    private final LuaTable waitToken = new LuaTable();

    /** How many times this task has been resumed. */
    private long resumeCount = 0;

    /**
     * Which resume the watchdog ran out of time on. The watchdog thread
     * writes it, hence volatile. Comparing it with resumeCount means a late
     * interrupt left over from an earlier resume can't stop the current one.
     */
    private volatile long timedOutResume = -1;

    /** Ticks the script asked to wait for in its latest wait(). */
    private int requestedWaitTicks = 0;

    LuaScriptTask(String scriptName, Prototype prototype, ScriptContext context) throws LuaError {
        this.scriptName = scriptName;
        this.context = context;
        this.state = LuaState.builder()
                .interruptHandler(this::onInterrupt)
                .build();

        LuaTable globals = LuaScriptRunner.createGlobals(state, context, waitToken);

        // The script runs as a coroutine (a LuaThread). In Cobalt that's NOT a
        // Java thread; it runs on whichever thread calls resume(), which is
        // always the server thread. Being a coroutine is what lets wait()
        // pause it partway through.
        this.thread = new LuaThread(state, LoadState.interpretedFunction(prototype, globals));
    }

    String getScriptName() { return scriptName; }

    int getRequestedWaitTicks() { return requestedWaitTicks; }

    private InterruptAction onInterrupt() {
        if (timedOutResume == resumeCount) throw new ScriptTimeoutError(scriptName);

        // A stale interrupt from an earlier resume: ignore it.
        return InterruptAction.CONTINUE;
    }

    /** Runs the script until it finishes, fails, or calls wait(). */
    Result resume() {
        long thisResume = ++resumeCount;

        ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
            timedOutResume = thisResume;
            state.interrupt();
        }, TIME_LIMIT_MS, TimeUnit.MILLISECONDS);

        try {
            Varargs yielded = LuaThread.run(thread, Constants.NONE);

            if (!thread.isAlive()) return new Result(Status.FINISHED, "");

            if (yielded != null && yielded.first() == waitToken) {
                requestedWaitTicks = yielded.arg(2).toInteger();

                return new Result(Status.WAITING, "");
            }

            return fail("Lua script error: scripts can only pause with wait(), not coroutine.yield()");

        } catch (ScriptTimeoutError e) {
            logger.warn("Lua script timeout: {}", e.getMessage());

            return new Result(Status.FAILED, e.getMessage());

        } catch (LuaError e) {
            String message = String.valueOf(e.getMessage()).split("\n")[0];

            logger.warn("Lua script error in {}: {}", scriptName, message);

            return new Result(Status.FAILED, "Lua script error: " + message);

        } catch (RuntimeException e) {
            logger.error("Unexpected error in {}", scriptName, e);

            return new Result(Status.FAILED, "Internal error: " + e);

        } finally {
            watchdog.cancel(false);
        }
    }

    /** Shows an error from a later resume, when there's no command to report it to. */
    void reportFailure(String message) {
        context.printError(message);
    }

    private Result fail(String message) {
        logger.warn("{}: {}", scriptName, message);

        return new Result(Status.FAILED, message);
    }
}