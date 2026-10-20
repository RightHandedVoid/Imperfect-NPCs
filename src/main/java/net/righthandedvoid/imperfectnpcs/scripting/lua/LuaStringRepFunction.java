package net.righthandedvoid.imperfectnpcs.scripting.lua;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public class LuaStringRepFunction extends ThreeArgFunction {

    @Override
    public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
        if (!arg1.isstring()) throw new LuaError("String argument must be a string");
        if (!arg2.isint()) throw new LuaError("Number argument must be an integer");
        if (!arg3.isnil() && !arg3.isstring()) throw new LuaError("Separator argument must be a string");

        StringBuilder builder = new StringBuilder();

        String copied_string =  arg1.toString();
        int copy_times =  arg2.toint();
        String separator_string = null;

        long sizeLimit = 1 << 10;
        long size;

        if (!arg3.isnil()) {
            separator_string = arg3.toString();

            size = (
                (long) (
                        copied_string.length() + separator_string.length()
                ) * copy_times
            ) - separator_string.length();

        } else {
            size = (long) copied_string.length() * copy_times;
        }

        if (size > sizeLimit) throw new LuaError("Repetition exceeds size limit");

        if (separator_string != null) {
            for (int i = 0; i < copy_times; i++) {
                builder.append(copied_string);

                if (i != (copy_times - 1)) builder.append(separator_string);
            }
        } else {
            builder.repeat(copied_string, Math.max(0, copy_times));
        }

        return LuaValue.valueOf(builder.toString());
    }
}
