package net.righthandedvoid.imperfectnpcs.scripting;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ScriptManager {
    private Path activeScriptPath;

    public void onServerStart(ServerStartedEvent event) throws IOException {
        Path scriptPath = getActiveScriptPath(event);

        System.out.println("Active script path: " + scriptPath);
    }

    public void onServerStop(ServerStoppedEvent event) {
        activeScriptPath = null;
    }


    private Path getActiveScriptPath(ServerStartedEvent event) throws IOException {
        if (activeScriptPath != null) return activeScriptPath;

        List<Path> scriptDirectories = makeScriptDirectories(event);

        for (Path scriptDirectory : scriptDirectories) {
            try (Stream<Path> stream = Files.walk(scriptDirectory)) {
                stream
                        .filter(Files::isRegularFile)
                        .findFirst()
                            .ifPresent(path -> activeScriptPath = scriptDirectory);
            }
        }

        return activeScriptPath;
    }

    private List<Path> makeScriptDirectories(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Path worldFolder = server.getWorldPath(LevelResource.ROOT);

        Path scriptsFolder = worldFolder
                .resolve("imperfectnpcs")
                .resolve("scripts");

        Path luaScriptsFolder = scriptsFolder.resolve("lua");
        Path pyScriptsFolder = scriptsFolder.resolve("py");
        Path jsScriptsFolder = scriptsFolder.resolve("js");

        try {
            Files.createDirectories(scriptsFolder);

            Files.createDirectories(luaScriptsFolder);
            Files.createDirectories(pyScriptsFolder);
            Files.createDirectories(jsScriptsFolder);
        } catch (IOException e) {
            // Log error
        }

        return List.of(luaScriptsFolder, pyScriptsFolder, jsScriptsFolder);
    }
}
