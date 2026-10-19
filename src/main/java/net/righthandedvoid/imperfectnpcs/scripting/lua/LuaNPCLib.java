package net.righthandedvoid.imperfectnpcs.scripting.lua;

import net.minecraft.world.phys.Vec3;
import net.righthandedvoid.imperfectnpcs.api.NpcAPI;
import net.righthandedvoid.imperfectnpcs.api.ScriptNPC;
import net.righthandedvoid.imperfectnpcs.entities.NPCEntity;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.LibFunction;

import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

/** Lua binding for the NPC API: the global `npcs` table and NPC objects. */
public final class LuaNPCLib {
    private static final double MAX_SEARCH_RADIUS = 128.0;

    /** One NPC method: gets the NPC (`self`) and the full argument list. */
    @FunctionalInterface
    private interface NpcMethod {
        Varargs call(ScriptNPC npc, Varargs args) throws LuaError;
    }

    /** Like Supplier, but allowed to throw LuaError (which Cobalt makes a checked exception). */
    @FunctionalInterface
    private interface LuaAction<T> {
        T run() throws LuaError;
    }

    private LuaNPCLib() {
    }

    /**
     * Builds the global `npcs` table for one environment. `meta` is the NPC
     * metatable from createMetatable(); the environment keeps it so it can
     * wrap NPCs it passes to scripts itself, such as event.npc.
     */
    static LuaTable create(ScriptContext context, LuaTable meta) {
        LuaTable lib = new LuaTable();

        // npcs.spawn(x, y, z) -> npc
        lib.rawset("spawn", LibFunction.createV((state, args) -> {
            Vec3 position = new Vec3(args.arg(1).checkDouble(), args.arg(2).checkDouble(), args.arg(3).checkDouble());

            return guard(() -> wrap(new ScriptNPC(NpcAPI.spawn(context.getLevel(), position)), meta));
        }));

        // npcs.get(uuid) -> npc, or nil if no loaded NPC has that UUID
        lib.rawset("get", LibFunction.create((state, uuid) -> {
            String text = uuid.checkString();

            return guard(() -> {
                NPCEntity npc = NpcAPI.get(context.getLevel(), UUID.fromString(text));

                return npc == null ? Constants.NIL : wrap(new ScriptNPC(npc), meta);
            });
        }));

        // npcs.findNearby(x, y, z, radius) -> list of npcs, closest first
        lib.rawset("findNearby", LibFunction.createV((state, args) -> {
            Vec3 center = new Vec3(args.arg(1).checkDouble(), args.arg(2).checkDouble(), args.arg(3).checkDouble());
            double radius = args.arg(4).checkDouble();

            if (radius <= 0 || radius > MAX_SEARCH_RADIUS) {
                throw new LuaError("radius must be greater than 0 and at most " + MAX_SEARCH_RADIUS);
            }

            return guard(() -> {
                List<NPCEntity> found = NpcAPI.findNearby(context.getLevel(), center, radius);
                LuaTable list = new LuaTable(found.size(), 0);

                for (int i = 0; i < found.size(); i++) {
                    list.rawset(i + 1, wrap(new ScriptNPC(found.get(i)), meta));
                }

                return list;
            });
        }));

        return lib;
    }

    /**
     * The metatable every NPC object in one environment shares. It's built per
     * environment rather than once globally, so nothing is shared between
     * LuaStates.
     */
    static LuaTable createMetatable() {
        LuaTable methods = new LuaTable();

        // Arguments start at 2 because argument 1 is always `self` (npc:method()).
        method(methods, "isValid", (npc, args) -> valueOf(npc.isValid()));
        method(methods, "getUUID", (npc, args) -> valueOf(npc.getUUID()));
        method(methods, "getName", (npc, args) -> valueOf(npc.getName()));

        method(methods, "setName", (npc, args) -> {
            npc.setName(args.arg(2).checkString());
            return Constants.NONE;
        });

        method(methods, "getSkin", (npc, args) -> valueOf(npc.getSkin()));

        method(methods, "setSkin", (npc, args) -> {
            npc.setSkin(args.arg(2).checkString());
            return Constants.NONE;
        });

        // local x, y, z = npc:getPosition()
        method(methods, "getPosition", (npc, args) ->
                varargsOf(valueOf(npc.getX()), valueOf(npc.getY()), valueOf(npc.getZ())));

        // npc:teleport(x, y, z)
        method(methods, "teleport", (npc, args) -> {
            npc.teleport(args.arg(2).checkDouble(), args.arg(3).checkDouble(), args.arg(4).checkDouble());
            return Constants.NONE;
        });

        // npc:walkTo(x, y, z [, speed]) -> true if a path was found
        method(methods, "walkTo", (npc, args) -> valueOf(npc.walkTo(
                args.arg(2).checkDouble(), args.arg(3).checkDouble(), args.arg(4).checkDouble(),
                args.arg(5).optDouble(1.0)
        )));

        method(methods, "getHealth", (npc, args) -> valueOf(npc.getHealth()));

        method(methods, "setHealth", (npc, args) -> {
            npc.setHealth((float) args.arg(2).checkDouble());
            return Constants.NONE;
        });

        method(methods, "remove", (npc, args) -> {
            npc.remove();
            return Constants.NONE;
        });

        LuaTable meta = new LuaTable();

        // npc.anything looks the name up in methods. Asking for a name that
        // doesn't exist is an error that lists what does, instead of
        // returning nil and failing later with "attempt to call a nil value".
        meta.rawset("__index", LibFunction.create((state, self, key) -> {
            LuaValue method = methods.rawget(key);

            if (!method.isNil()) return method;

            throw new LuaError("NPCs have no method called '" + key + "'. Available methods: " + listKeys(methods));
        }));

        // print(npc) shows "NPC[<uuid>]" instead of "userdata: 1a2b3c".
        meta.rawset("__tostring", LibFunction.create((state, self) ->
                valueOf("NPC[" + checkNpc(self, "__tostring").getUUID() + "]")));

        // getmetatable(npc) returns false, so scripts can't reach the methods table.
        meta.rawset("__metatable", Constants.FALSE);

        // No __eq needed: Cobalt compares userdata with ScriptNPC.equals()
        // when they share a metatable, so npc == otherNpc already works,
        // and NPCs can be used as table keys.
        return meta;
    }

    /**
     * Turns a ScriptNPC into a Lua object. It's userdata rather than a table,
     * so scripts can't overwrite its methods.
     */
    static LuaUserdata wrap(ScriptNPC npc, LuaTable meta) {
        return ValueFactory.userdataOf(npc, meta);
    }

    private static ScriptNPC checkNpc(LuaValue value, String methodName) throws LuaError {
        if (value instanceof LuaUserdata userdata && userdata.instance instanceof ScriptNPC npc) return npc;

        throw new LuaError("NPC methods need a colon: npc:" + methodName + "(...)");
    }

    /** Registers one NPC method, checking that it was called with a colon. */
    private static void method(LuaTable methods, String name, NpcMethod body) {
        methods.rawset(name, LibFunction.createV((state, args) -> {
            ScriptNPC npc = checkNpc(args.first(), name);

            return guard(() -> body.call(npc, args));
        }));
    }

    /** A sorted, comma-separated list of a table's keys, for error messages. */
    private static String listKeys(LuaTable table) throws LuaError {
        TreeSet<String> names = new TreeSet<>();

        for (Varargs entry = table.next(Constants.NIL); !entry.first().isNil(); entry = table.next(entry.first())) {
            names.add(entry.first().toString());
        }

        return String.join(", ", names);
    }

    /** Converts Java exceptions into clean Lua errors that scripts can pcall. */
    private static <T> T guard(LuaAction<T> action) throws LuaError {
        try {
            return action.run();
        } catch (RuntimeException e) {
            throw new LuaError(e.getMessage() == null ? e.toString() : e.getMessage());
        } catch (LinkageError e) {
            // The Lua binding called a Java method that doesn't exist at
            // runtime, e.g. a jar built from mismatched source files. The
            // compiler normally catches this, but if it ever slips through,
            // say so clearly instead of crashing the server tick.
            throw new LuaError("This function isn't implemented on the Java side ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }
}