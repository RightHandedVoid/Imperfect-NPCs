package net.righthandedvoid.imperfectnpcs.scripting;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.Hash;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.righthandedvoid.imperfectnpcs.scripting.lua.LuaScriptRunner;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ScriptManager {
    private static final Logger logger = LogUtils.getLogger();
    private static final Map<String, String> scriptSources = new HashMap<>();
    private static final Map<String, ScriptRecord> scriptRecords = new HashMap<>();
    private static final LuaScriptRunner luaScriptRunner = new LuaScriptRunner();
    private static Path scriptDirectory = null;

    public static void onServerStart(ServerStartedEvent event) {
        seekAndWorkScripts(event);
    }

    public static void onServerStop(ServerStoppedEvent event) {
        scriptDirectory = null;
        scriptSources.clear();
        scriptRecords.clear();

        luaScriptRunner.clearScriptCache();
    }


    public static ScriptRecord runScript(String scriptName) {
        return switch (scriptName.substring(scriptName.lastIndexOf('.') + 1)) {
            case "lua" -> luaScriptRunner.run(scriptName);

            // there's definitely a better way to do this

            case "py" -> {
                logger.warn("Python Engine not implemented yet");

                yield new ScriptRecord(false, "Python Engine not implemented yet");
            }

            case "js" -> {
                logger.warn("Javascript Engine not implemented yet");

                yield  new ScriptRecord(false,  "Javascript Engine not implemented yet");
            }

            default -> {
                logger.warn("Unsupported script name: {}", scriptName);

                yield new ScriptRecord(false,  "Unsupported script name: " + scriptName);
            }
        };
    }

    public static List<String> getFoundScripts() {
        return List.copyOf(scriptSources.keySet()); // this might need changing // nope works fine
    }

    public static String getScriptSource(String scriptName) {
        return scriptSources.get(scriptName);
    }

    public static String getScriptName(Path scriptPath) {
        return scriptDirectory
                .relativize(scriptPath).normalize()
                .toString().toLowerCase(Locale.ROOT);
    }

    public static String getScriptExtension(Path scriptPath) {
        String scriptName = scriptPath.normalize().toString().toLowerCase(Locale.ROOT);
        return scriptName.substring(scriptName.lastIndexOf('.') + 1);
    }

    public static String getReloadLog() {
        StringBuilder log = new StringBuilder();

        scriptRecords.forEach((name, scriptRecord) -> {
            if (!scriptRecord.scriptSuccess()) {
                log.append(name)
                    .append(" failed: ")
                    .append(scriptRecord.message())
                    .append("\n");
            };
        });

        return log.toString();
    }

    public static String getReloadLogSlim() {
        return "Reloaded " + scriptRecords.size() + " scripts with " + getReloadErrorCount() + " errors.";
    }

    public static int getReloadErrorCount() {
        int reloadErrors = 0;

        for (ScriptRecord scriptRecord : scriptRecords.values()) {
            if (!scriptRecord.scriptSuccess()) ++reloadErrors;
        }

        return reloadErrors;
    }

    private static void seekAndWorkScripts(ServerStartedEvent event) {
        scriptDirectory = makeScriptDirectories(event);

        reloadScripts();
    }

    public static void reloadScripts() {
        scriptSources.clear();
        scriptRecords.clear();
        luaScriptRunner.clearScriptCache();

        try (Stream<Path> stream = Files.walk(scriptDirectory)) {
            stream
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    computeScriptSource(path);
                    String scriptName = getScriptName(path);

                    if (scriptSources.get(scriptName) == null) return;

                    if (getScriptExtension(path).equals("lua")) {
                        ScriptRecord result = luaScriptRunner.cacheScript(scriptName);

                        scriptRecords.put(scriptName, result);
                    }

                });
        } catch (IOException e) {
            logger.error("Unable to read script directory: {}", scriptDirectory, e);
        }
    }

    private static void computeScriptSource(Path scriptPath) {
        if (!List.of("lua", "js", "py").contains(getScriptExtension(scriptPath))) return;

        String scriptSource = null;

        try {
            scriptSource = Files.readString(scriptPath, StandardCharsets.UTF_8);

        } catch (IOException e) {
            logger.warn("Could not read script {}", scriptPath, e);
        }

        if  (scriptSource != null) {
            scriptSources.put(
                    getScriptName(scriptPath),
                    scriptSource
            );
        }
    }

    private static Path makeScriptDirectories(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Path worldFolder = server.getWorldPath(LevelResource.ROOT);

        Path scriptsFolder = worldFolder
                .resolve("imperfectnpcs")
                .resolve("scripts");

        try {
            Files.createDirectories(scriptsFolder);

        } catch (IOException e) {
            logger.error("could not create scripts folder", e);
        }

        return scriptsFolder;
    }
}
