package net.righthandedvoid.imperfectnpcs.scripting.lua;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public class LuaPrintFunction extends VarArgFunction {
    @Override
    public Varargs invoke(Varargs args) {
        StringBuilder builder = new StringBuilder();

        for (int index = 1; index <= args.narg(); index++) {
            if (index != 1) builder.append("\t");

            builder.append(args.arg(index));
        }

        builder.append("\n");

        System.out.print(builder);

        return LuaValue.NONE;
    }
}
