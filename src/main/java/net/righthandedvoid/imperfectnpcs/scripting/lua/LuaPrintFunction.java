package net.righthandedvoid.imperfectnpcs.scripting.lua;

import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public class LuaPrintFunction extends VarArgFunction {
    private final LuaValue tostring;
    private final ScriptContext context;

    public LuaPrintFunction(Globals globals, ScriptContext context) {
        // Captured once, so a script that reassigns or nils `tostring` can't break print.
        this.tostring = globals.get("tostring");
        this.context = context;
    }

    @Override
    public Varargs invoke(Varargs args) {
        StringBuilder builder = new StringBuilder();

        for (int index = 1; index <= args.narg(); index++) {
            if (index != 1) builder.append('\t');

            // Going through tostring respects __tostring metamethods,
            // so wrapped objects like NPCs print as something readable.
            builder.append(tostring.call(args.arg(index)).tojstring());
        }

        context.print(builder.toString());

        return LuaValue.NONE;
    }
}