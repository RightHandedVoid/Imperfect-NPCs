package net.righthandedvoid.imperfectnpcs.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LuaScriptRunner {

    private LuaScriptRunner() {

    }

    public static void run(Path scriptPath) {
        try {
            String source = Files.readString(
                    scriptPath,
                    StandardCharsets.UTF_8
            );

            Globals globals = JsePlatform.standardGlobals();

            LuaValue script = globals.load(
                    source,
                    scriptPath.getFileName().toString()
            );

            script.call();

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to read Lua script: " + scriptPath,
                    e
            );
        }
    }
}
