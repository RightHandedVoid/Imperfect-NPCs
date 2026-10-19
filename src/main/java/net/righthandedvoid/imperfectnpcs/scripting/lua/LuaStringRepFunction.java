package net.righthandedvoid.imperfectnpcs.scripting.lua;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.LibFunction;

/** A string.rep with a size cap, so scripts can't build huge strings. */
public final class LuaStringRepFunction {
    private static final long SIZE_LIMIT = 1 << 10;

    private LuaStringRepFunction() {
    }

    public static LibFunction create() {
        return LibFunction.create(LuaStringRepFunction::rep);
    }

    // string.rep(s, n [, separator])
    private static LuaValue rep(LuaState state, LuaValue stringArg, LuaValue countArg, LuaValue separatorArg) throws LuaError {
        // LuaString rather than String: Lua strings are raw bytes and can
        // hold binary data that a Java String would mangle.
        LuaString string = stringArg.checkLuaString();
        int count = countArg.checkInteger();
        LuaString separator = separatorArg.optLuaString(Constants.EMPTYSTRING);

        if (count <= 0) return Constants.EMPTYSTRING;

        long size = (long) string.length() * count + (long) separator.length() * (count - 1);

        if (size > SIZE_LIMIT) throw new LuaError("string.rep result would be longer than " + SIZE_LIMIT + " bytes");

        byte[] bytes = new byte[(int) size];
        int position = 0;

        for (int i = 0; i < count; i++) {
            if (i > 0) position = separator.copyTo(bytes, position);

            position = string.copyTo(bytes, position);
        }

        return ValueFactory.valueOf(bytes);
    }
}