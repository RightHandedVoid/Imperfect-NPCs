package net.righthandedvoid.imperfectnpcs.scripting;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Everything a script needs to know about where it runs: which script it is,
 * which world it runs in, and where its output goes. This class is
 * language-neutral, so the Lua, JavaScript and Python runners can all share it.
 *
 * Output goes to whoever ran the command, for scripts started by a command.
 * Scripts attached to NPCs have no command, so their output goes to every
 * operator who's online instead (in singleplayer, that's you if cheats are on).
 */
public final class ScriptContext {
    private static final Logger logger = LogUtils.getLogger();
    private static final int MAX_PRINT_LINES = 100;
    private static final int MAX_CHAT_LINE_LENGTH = 256;

    /** Permission level of an operator, the same one the /npcs and /script commands require. */
    private static final int OPERATOR_PERMISSION_LEVEL = 2;

    private final String scriptName;
    private final ServerLevel level;
    private final @Nullable CommandSourceStack source;
    private final boolean notifyOperators;
    private int printedLines = 0;

    public ScriptContext(String scriptName, ServerLevel level, @Nullable CommandSourceStack source) {
        this(scriptName, level, source, false);
    }

    private ScriptContext(String scriptName, ServerLevel level, @Nullable CommandSourceStack source, boolean notifyOperators) {
        this.scriptName = scriptName;
        this.level = level;
        this.source = source;
        this.notifyOperators = notifyOperators;
    }

    /** Context for a script started by a command; output goes to whoever ran it. */
    public static ScriptContext fromCommand(String scriptName, CommandSourceStack source) {
        return new ScriptContext(scriptName, source.getLevel(), source, false);
    }

    /** Context for a script attached to an NPC; output goes to online operators. */
    public static ScriptContext forNpc(String scriptName, ServerLevel level) {
        return new ScriptContext(scriptName, level, null, true);
    }

    public String getScriptName() { return scriptName; }

    public ServerLevel getLevel() { return level; }

    /** The server's current tick number. The scheduler uses it to time wait(). */
    public int getServerTick() { return level.getServer().getTickCount(); }

    /** Starts the print cap over. NPC scripts call this at the start of every event. */
    public void resetPrintLimit() { printedLines = 0; }

    /**
     * Shared output for every language's print / console.log.
     * Capped so a print inside a loop can't flood chat or the log.
     */
    public void print(String message) {
        printedLines++;

        if (printedLines > MAX_PRINT_LINES) {
            if (printedLines == MAX_PRINT_LINES + 1) {
                logger.info("[{}] output truncated after {} lines", scriptName, MAX_PRINT_LINES);

                send(Component.literal("[" + scriptName + "] output truncated").withStyle(ChatFormatting.GRAY));
            }

            return;
        }

        logger.info("[{}] {}", scriptName, message);

        // Minecraft's font has no tab glyph, so swap tabs for spaces in chat only.
        String shown = message.replace("\t", "    ");

        if (shown.length() > MAX_CHAT_LINE_LENGTH) {
            shown = shown.substring(0, MAX_CHAT_LINE_LENGTH) + "...";
        }

        // Without a command, output could come from any NPC at any time, so
        // label it with the script's name.
        if (source == null) shown = "[" + scriptName + "] " + shown;

        send(Component.literal(shown));
    }

    /**
     * Reports an error that happens after the command that started the script
     * has already returned, or in an NPC's script. Errors always show, even
     * once the print cap has been reached.
     */
    public void printError(String message) {
        logger.warn("[{}] {}", scriptName, message);

        send(Component.literal("[" + scriptName + "] " + message).withStyle(ChatFormatting.RED));
    }

    private void send(Component message) {
        if (source != null) {
            source.sendSystemMessage(message);
            return;
        }

        if (!notifyOperators) return;

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.hasPermissions(OPERATOR_PERMISSION_LEVEL)) player.sendSystemMessage(message);
        }
    }
}