package net.righthandedvoid.imperfectnpcs.scripting.lua;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public class LuaPrintFunction extends VarArgFunction {
    private final CommandSourceStack source;
    private static final String TAB_INDENT = "    ";

    public LuaPrintFunction(CommandSourceStack source) {
        this.source = source;
    }

    @Override
    public Varargs invoke(Varargs args) {
        StringBuilder builder = new StringBuilder();

        for (int index = 1; index <= args.narg(); index++) {
            if (index != 1) builder.append(TAB_INDENT);

            builder.append(args.arg(index));
        }

        source.sendSystemMessage(Component.literal(builder.toString()));

        return LuaValue.NONE;
    }
}
