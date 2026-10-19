package net.righthandedvoid.imperfectnpcs.scripting.lua;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.Varargs;

/**
 * One coroutine that's running or waiting: a script's top level, or one call
 * to an event handler. Several tasks can share an environment (all of an
 * NPC's event handlers do).
 */
final class LuaScriptTask {
    private final LuaEnvironment environment;
    private final LuaThread thread;

    /** Arguments for the next resume: the event for a handler's first resume, nothing after that. */
    private Varargs nextArgs;

    private int requestedWaitTicks = 0;

    LuaScriptTask(LuaEnvironment environment, LuaThread thread, Varargs firstArgs) {
        this.environment = environment;
        this.thread = thread;
        this.nextArgs = firstArgs;
    }

    LuaEnvironment getEnvironment() { return environment; }

    String getScriptName() { return environment.getScriptName(); }

    int getRequestedWaitTicks() { return requestedWaitTicks; }

    /** Runs the coroutine until it finishes, fails, or calls wait(). */
    LuaEnvironment.Result resume() {
        LuaEnvironment.Result result = environment.resume(thread, nextArgs);

        nextArgs = Constants.NONE;

        if (result.status() == LuaEnvironment.Status.WAITING) requestedWaitTicks = result.waitTicks();

        return result;
    }

    /** Shows an error from a later resume, when there's no command to report it to. */
    void reportFailure(String message) {
        environment.getContext().printError(message);
    }
}