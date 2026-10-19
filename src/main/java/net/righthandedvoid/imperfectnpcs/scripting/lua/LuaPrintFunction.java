package net.righthandedvoid.imperfectnpcs.scripting.lua;

import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LibFunction;

/**
 * Lua's print.
 *
 * The formatting half is written in Lua, the same way CC: Tweaked does it.
 * Calling tostring() on a value can run a __tostring metamethod, and in
 * Cobalt a metamethod is allowed to yield, which plain Java functions can't
 * cope with. Lua code handles that for free. Java only does the last step:
 * sending the finished line to ScriptContext.
 */
public final class LuaPrintFunction {
    /**
     * Receives the output function and the library functions it needs, and
     * returns print. Grabbing tostring, table.pack and table.concat up front
     * means a script that reassigns them can't break print.
     */
    private static final String PRINT_SOURCE = """
            local output, tostring, pack, concat = ...

            return function(...)
                local args = pack(...)
                local parts = {}

                for i = 1, args.n do
                    parts[i] = tostring(args[i])
                end

                output(concat(parts, "\\t", 1, args.n))
            end
            """;

    private static final Prototype PRINT_PROTOTYPE;

    static {
        try {
            PRINT_PROTOTYPE = LuaScriptRunner.compile(PRINT_SOURCE, "=print");
        } catch (CompileException | LuaError e) {
            throw new IllegalStateException("Built-in print failed to compile", e);
        }
    }

    private LuaPrintFunction() {
    }

    /** Adds print to a script's globals, with output going to the given context. */
    public static void install(LuaState state, LuaTable globals, ScriptContext context) throws LuaError {
        LibFunction output = LibFunction.create((callState, line) -> {
            context.print(line.checkString());

            return Constants.NIL;
        });

        LuaTable table = globals.rawget("table").checkTable();


        LuaValue print = LuaThread.runMain(
                state,
                LoadState.interpretedFunction(PRINT_PROTOTYPE, globals),
                ValueFactory.varargsOf(output, globals.rawget("tostring"), table.rawget("pack"), table.rawget("concat"))
        ).first();

        globals.rawset("print", print);
    }
}