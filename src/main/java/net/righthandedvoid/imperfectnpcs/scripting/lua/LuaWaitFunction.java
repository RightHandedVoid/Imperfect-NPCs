package net.righthandedvoid.imperfectnpcs.scripting.lua;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;

/**
 * Lua's wait(ticks): pauses the script for that many server ticks
 * (20 ticks = 1 second). With no argument it waits a single tick.
 *
 * It's written in Lua because all it does is coroutine.yield() a private
 * token plus the tick count; LuaScriptTask sees the token and hands the
 * task to the scheduler.
 */
public final class LuaWaitFunction {
    /** The longest a single wait() may be: one in-game day, about 20 minutes. */
    private static final int MAX_WAIT_TICKS = 24000;

    private static final String WAIT_SOURCE = """
            local yield, token, floor, type, error, maxTicks = ...

            return function(ticks)
                if ticks == nil then
                    ticks = 1
                elseif type(ticks) ~= "number" then
                    error("bad argument #1 to 'wait' (number expected, got " .. type(ticks) .. ")", 2)
                end

                -- ticks ~= ticks catches NaN.
                if ticks ~= ticks or ticks < 1 then
                    ticks = 1
                elseif ticks > maxTicks then
                    error("wait() can't wait longer than " .. maxTicks .. " ticks at once", 2)
                end

                yield(token, floor(ticks))
            end
            """;

    private static final Prototype WAIT_PROTOTYPE;

    static {
        try {
            WAIT_PROTOTYPE = LuaScriptRunner.compile(WAIT_SOURCE, "=wait");
        } catch (CompileException | LuaError e) {
            throw new IllegalStateException("Built-in wait failed to compile", e);
        }
    }

    private LuaWaitFunction() {
    }

    /** Adds wait to a script's globals. */
    public static void install(LuaState state, LuaTable globals, LuaTable waitToken) throws LuaError {
        LuaValue wait = LuaThread.runMain(
                state,
                LoadState.interpretedFunction(WAIT_PROTOTYPE, globals),
                ValueFactory.varargsOf(
                        globals.rawget("coroutine").checkTable().rawget("yield"),
                        waitToken,
                        globals.rawget("math").checkTable().rawget("floor"),
                        globals.rawget("type"),
                        globals.rawget("error"),
                        ValueFactory.valueOf(MAX_WAIT_TICKS)
                )
        ).first();

        globals.rawset("wait", wait);
    }
}