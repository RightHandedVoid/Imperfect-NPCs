package net.righthandedvoid.imperfectnpcs.scripting;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Everything a single script run needs to know: which script it is, which
 * world it runs in, and where its output goes. This class is language-neutral,
 * so the Lua, JavaScript and Python runners can all share it.
 */
public final class ScriptContext {
    private static final Logger logger = LogUtils.getLogger();
    private static final int MAX_PRINT_LINES = 100;
    private static final int MAX_CHAT_LINE_LENGTH = 256;

    private final String scriptName;
    private final ServerLevel level;
    private final @Nullable CommandSourceStack source;
    private int printedLines = 0;

    public ScriptContext(String scriptName, ServerLevel level, @Nullable CommandSourceStack source) {
        this.scriptName = scriptName;
        this.level = level;
        this.source = source;
    }

    /** Context for a script started by a command; output goes to whoever ran it. */
    public static ScriptContext fromCommand(String scriptName, CommandSourceStack source) {
        return new ScriptContext(scriptName, source.getLevel(), source);
    }

    public String getScriptName() { return scriptName; }

    public ServerLevel getLevel() { return level; }

    /**
     * Shared output for every language's print / console.log.
     * Capped per run so a print inside a loop can't flood chat or the log.
     */
    public void print(String message) {
        printedLines++;

        if (printedLines > MAX_PRINT_LINES) {
            if (printedLines == MAX_PRINT_LINES + 1) {
                logger.info("[{}] output truncated after {} lines", scriptName, MAX_PRINT_LINES);

                if (source != null) {
                    source.sendSystemMessage(
                            Component.literal("[" + scriptName + "] output truncated")
                                    .withStyle(ChatFormatting.GRAY)
                    );
                }
            }

            return;
        }

        logger.info("[{}] {}", scriptName, message);

        if (source == null) return;

        // Minecraft's font has no tab glyph, so swap tabs for spaces in chat only.
        String shown = message.replace("\t", "    ");

        if (shown.length() > MAX_CHAT_LINE_LENGTH) {
            shown = shown.substring(0, MAX_CHAT_LINE_LENGTH) + "...";
        }

        source.sendSystemMessage(Component.literal(shown));
    }
}