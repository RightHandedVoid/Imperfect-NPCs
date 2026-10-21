package net.righthandedvoid.imperfectnpcs.scripting.lua;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptManager;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptRecord;
import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public final class LuaScriptRunner {
    // I wonder how much of this I'll have to rewrite bc I don't understand why any of this has to be done
    private static final Logger logger = LogUtils.getLogger();
    private static final Map<String, Prototype> script_cache = new HashMap<>();
    private static final int MAX_INSTRUCTION_COUNT = 100000;
    private static Globals server_globals;

    static class ReadOnlyLuaTable extends LuaTable {
        public ReadOnlyLuaTable(LuaValue table) {
            presize(table.length(), 0);

            for (Varargs n = table.next(LuaValue.NIL); !n.arg1().isnil(); n = table.next(n.arg1())) {
                LuaValue key = n.arg1();
                LuaValue value = n.arg(2);

                super.rawset(key, value.istable() ? new ReadOnlyLuaTable(value) : value);
            }
        }
        public LuaValue setmetatable(LuaValue metatable) { return error("table is read-only"); }
        public void set(int key, LuaValue value) { error("table is read-only"); }
        public void rawset(int key, LuaValue value) { error("table is read-only"); }
        public void rawset(LuaValue key, LuaValue value) { error("table is read-only"); }
        public LuaValue remove(int pos) { return error("table is read-only"); }
    }

    public LuaScriptRunner() {
        server_globals = new Globals();
        server_globals.load(new JseBaseLib());
        server_globals.load(new PackageLib());
        server_globals.load(new StringLib());
        server_globals.load(new JseMathLib());

        LoadState.install(server_globals);
        LuaC.install(server_globals);

        server_globals.get("string").set("rep", new LuaStringRepFunction());

        LuaString.s_metatable = new ReadOnlyLuaTable(LuaString.s_metatable);
    }

    public void clearScriptCache() {
        script_cache.clear();
    }

    public ScriptRecord cacheScript(String scriptName) {
        Prototype scriptPrototype;

        try (StringReader reader = new StringReader(ScriptManager.getScriptSource(scriptName))) {
            scriptPrototype = server_globals.compilePrototype(reader, scriptName);

            script_cache.put(scriptName, scriptPrototype);

        } catch (IOException | LuaError e) {
            logger.error("Error while compiling script {}", scriptName, e);

            return new ScriptRecord(false, e.getMessage());
        }

        return new ScriptRecord(true, (scriptName + " compiled successfully"));
    }

    public ScriptRecord run(String scriptName, CommandSourceStack source) {
        Globals user_globals = new Globals();

        user_globals.load(new JseBaseLib());
        user_globals.load(new PackageLib());
        user_globals.load(new Bit32Lib());
        user_globals.load(new TableLib());
        user_globals.load(new StringLib());
        user_globals.load(new JseMathLib());
        user_globals.load(new DebugLib());

        LoadState.install(user_globals);
        LuaC.install(user_globals);

        LuaValue sethook = user_globals.get("debug").get("sethook");

        user_globals.set("dofile", LuaValue.NIL);
        user_globals.set("loadfile",  LuaValue.NIL);
        user_globals.set("debug", LuaValue.NIL);
        user_globals.set("require", LuaValue.NIL);
        user_globals.set("package", LuaValue.NIL);
        user_globals.set("collectgarbage", LuaValue.NIL);

        user_globals.set("print",  new LuaPrintFunction(source));
        user_globals.get("string").set("rep", new LuaStringRepFunction());

        try {
            LuaClosure chunk;

            if (script_cache.containsKey(scriptName)) {
                chunk = new LuaClosure(
                        script_cache.get(scriptName),
                        user_globals
                );

            } else {
                return new ScriptRecord(
                        false,
                        (scriptName + " failed to previously compile during reload.")
                );
            }



            LuaThread thread = new LuaThread(user_globals, chunk);

            LuaValue hookFunction = new ZeroArgFunction() {
                public LuaValue call() {
                    throw new Error(scriptName + " overran resource limits.");
                }
            };

            sethook.invoke(
                    LuaValue.varargsOf(
                            new LuaValue[] {
                                    thread,
                                    hookFunction,
                                    LuaValue.EMPTYSTRING,
                                    LuaValue.valueOf(MAX_INSTRUCTION_COUNT)
                            }
                    )
            );

            Varargs result = thread.resume(LuaValue.NIL);
            boolean resultSuccess = result.arg(1).checkboolean();
            String resultErrorMessage = result.arg(2).toString().split("\n")[0]; // python reference?

            if (!resultSuccess) return new ScriptRecord(false, resultErrorMessage);

        } catch (LuaError e) {
            logger.warn("Lua script error: {}", e.getMessage());

            return new ScriptRecord(false, "Lua script error: " + e.getMessage());
        }

        return new ScriptRecord(true, "Successfully ran " + scriptName);
    }
}
