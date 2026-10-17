package net.righthandedvoid.imperfectnpcs.scripting.lua;

import net.minecraft.world.phys.Vec3;
import net.righthandedvoid.imperfectnpcs.api.NpcAPI;
import net.righthandedvoid.imperfectnpcs.api.ScriptNPC;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.luaj.vm2.LuaValue.NONE;
import static org.luaj.vm2.LuaValue.valueOf;
import static org.luaj.vm2.LuaValue.varargsOf;

/** Lua binding for the NPC API: the global `npcs` table and NPC objects. */
public final class LuaNPCLib {
    /** Methods every NPC object has. Built once and shared by all scripts. */
    private static final LuaTable METHODS = new LuaTable();

    /** Metatable for NPC userdata: method lookup, printing and comparison. */
    private static final LuaTable META = new LuaTable();

    private static final double MAX_SEARCH_RADIUS = 128.0;

    /** One NPC method: gets the NPC (`self`) and the full argument list. */
    @FunctionalInterface
    private interface NpcMethod {
        Varargs call(ScriptNPC npc, Varargs args);
    }

    // Arguments start at 2 because argument 1 is always `self` (npc:method()).
    static {
        method("isValid", (npc, args) -> valueOf(npc.isValid()));
        method("getUUID", (npc, args) -> valueOf(npc.getUUID()));
        method("getName", (npc, args) -> valueOf(npc.getName()));

        method("setName", (npc, args) -> {
            npc.setName(args.checkjstring(2));
            return NONE;
        });

        method("getSkin", (npc, args) -> valueOf(npc.getSkin()));

        method("setSkin", (npc, args) -> {
            npc.setSkin(args.checkjstring(2));
            return NONE;
        });

        // local x, y, z = npc:getPosition()
        method("getPosition", (npc, args) -> varargsOf(new LuaValue[] {
                valueOf(npc.getX()), valueOf(npc.getY()), valueOf(npc.getZ())
        }));

        // npc:teleport(x, y, z)
        method("teleport", (npc, args) -> {
            npc.teleport(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            return NONE;
        });

        // npc:walkTo(x, y, z [, speed]) -> true if a path was found
        method("walkTo", (npc, args) -> valueOf(npc.walkTo(
                args.checkdouble(2), args.checkdouble(3), args.checkdouble(4),
                args.optdouble(5, 1.0)
        )));

        method("getHealth", (npc, args) -> valueOf(npc.getHealth()));

        method("setHealth", (npc, args) -> {
            npc.setHealth((float) args.checkdouble(2));
            return NONE;
        });

        method("remove", (npc, args) -> {
            npc.remove();
            return NONE;
        });

        // npc.anything looks the name up in METHODS.
        META.set(LuaValue.INDEX, METHODS);

        // print(npc) shows "NPC[<uuid>]" instead of "userdata: 1a2b3c".
        META.set(LuaValue.TOSTRING, new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue self) {
                ScriptNPC npc = (ScriptNPC) self.checkuserdata(ScriptNPC.class);

                return valueOf("NPC[" + npc.getUUID() + "]");
            }
        });

        // npc == otherNpc is true when both point at the same NPC.
        META.set(LuaValue.EQ, new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue a, LuaValue b) {
                return valueOf(a.touserdata().equals(b.touserdata()));
            }
        });

        // getmetatable(npc) returns false, so scripts can't reach METHODS.
        META.set(LuaValue.METATABLE, LuaValue.FALSE);
    }

    private LuaNPCLib() {
    }

    /** Builds the global `npcs` table for one script run. */
    public static LuaTable create(ScriptContext context) {
        LuaTable lib = new LuaTable();

        // npcs.spawn(x, y, z) -> npc
        lib.set("spawn", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Vec3 position = new Vec3(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));

                return guard(() -> wrap(new ScriptNPC(NpcAPI.spawn(context.getLevel(), position))));
            }
        });

        // npcs.get(uuid) -> npc, or nil if no loaded NPC has that UUID
        lib.set("get", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue uuid) {
                String text = uuid.checkjstring();

                return guard(() -> {
                    NPCEntity npc = NpcAPI.get(context.getLevel(), UUID.fromString(text));

                    return npc == null ? NIL : wrap(new ScriptNPC(npc));
                });
            }
        });

        // npcs.findNearby(x, y, z, radius) -> list of npcs, closest first
        lib.set("findNearby", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Vec3 center = new Vec3(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));
                double radius = args.checkdouble(4);

                if (radius <= 0 || radius > MAX_SEARCH_RADIUS) {
                    throw new LuaError("radius must be greater than 0 and at most " + MAX_SEARCH_RADIUS);
                }

                return guard(() -> {
                    List<NPCEntity> found = NpcAPI.findNearby(context.getLevel(), center, radius);
                    LuaTable list = new LuaTable();

                    for (int i = 0; i < found.size(); i++) {
                        list.set(i + 1, wrap(new ScriptNPC(found.get(i))));
                    }

                    return list;
                });
            }
        });

        return lib;
    }

    /**
     * Turns a ScriptNPC into a Lua object. It's userdata rather than a table,
     * so scripts can't overwrite its methods, and every NPC shares one
     * metatable instead of building its own set of functions.
     */
    public static LuaValue wrap(ScriptNPC npc) {
        return LuaValue.userdataOf(npc, META);
    }

    /** Registers one NPC method, checking that it was called with a colon. */
    private static void method(String name, NpcMethod body) {
        METHODS.set(name, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (!args.arg1().isuserdata(ScriptNPC.class)) {
                    throw new LuaError("NPC methods need a colon: npc:" + name + "(...)");
                }

                ScriptNPC npc = (ScriptNPC) args.arg1().touserdata(ScriptNPC.class);

                return guard(() -> body.call(npc, args));
            }
        });
    }

    /** Converts Java exceptions into clean Lua errors that scripts can pcall. */
    private static <T> T guard(Supplier<T> action) {
        try {
            return action.get();
        } catch (LuaError e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LuaError(e.getMessage());
        }
    }
}