package net.righthandedvoid.imperfectnpcs.scripting.lua;

import net.righthandedvoid.imperfectnpcs.api.ScriptNPC;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptContext;
import net.righthandedvoid.imperfectnpcs.scripting.ScriptEvent;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.Prototype;
import org.squiddev.cobalt.function.LuaFunction;

import java.util.ArrayDeque;
import java.util.Map;

/**
 * A script attached to one NPC. The script's top level runs once when the
 * instance starts, defining handler functions like `function interact(event)`;
 * after that, each event calls the matching global function, if there is one,
 * in its own coroutine.
 */
final class LuaNPCInstance {
    private final LuaEnvironment environment;
    private final LuaScheduler scheduler;
    private final ScriptNPC npc;

    /**
     * Events that arrived while this NPC's Lua code was already running
     * (for example a handler that hurts its own NPC fires `damaged` from
     * inside itself). A LuaState can't be re-entered, so they wait here and
     * run as soon as the current code finishes, at the latest on the NPC's
     * next tick.
     */
    private final ArrayDeque<ScriptEvent> pending = new ArrayDeque<>();

    LuaNPCInstance(String scriptName, ScriptNPC npc, ScriptContext context, LuaScheduler scheduler) throws LuaError {
        this.environment = new LuaEnvironment(scriptName, context);
        this.scheduler = scheduler;
        this.npc = npc;

        // The NPC this script is attached to, available everywhere in the script.
        environment.getGlobals().rawset("npc", environment.toLua(npc));
    }

    String getScriptName() { return environment.getScriptName(); }

    LuaEnvironment getEnvironment() { return environment; }

    /**
     * Runs the script's top level, then fires `init`. Returns false if the top
     * level failed, in which case the instance shouldn't be used.
     */
    boolean start(Prototype prototype) {
        ScriptContext context = environment.getContext();
        LuaScriptTask topLevel = new LuaScriptTask(environment, environment.newThread(prototype), Constants.NONE);
        LuaEnvironment.Result result = topLevel.resume();

        switch (result.status()) {
            case FAILED -> {
                context.printError(result.message());
                return false;
            }
            // Handlers defined after a top-level wait() only exist once it
            // resumes; until then, events for them are skipped.
            case WAITING -> scheduler.add(topLevel, context.getServerTick());
            case FINISHED -> {
            }
        }

        fire(new ScriptEvent("init", Map.of("npc", npc)));

        return true;
    }

    /** Queues an event and runs it right away unless this NPC's code is already running. */
    void fire(ScriptEvent event) {
        pending.add(event);
        drainPending();
    }

    /** Runs queued events in order. Called after fire() and on every NPC tick. */
    void drainPending() {
        while (!pending.isEmpty() && !environment.isRunning() && !environment.isClosed()) {
            ScriptEvent event = pending.poll();
            LuaValue handler = environment.getGlobals().rawget(event.name());

            // No handler for this event: nothing to do.
            if (!(handler instanceof LuaFunction function)) continue;

            ScriptContext context = environment.getContext();

            // The print cap applies per event, not for the NPC's whole lifetime.
            context.resetPrintLimit();

            LuaScriptTask task = new LuaScriptTask(environment, environment.newThread(function), environment.toLua(event.data()));

            scheduler.runNow(task, context.getServerTick());
        }
    }

    /** Throws the instance away. Its waiting handlers stop. */
    void close() {
        environment.close();
        pending.clear();
        scheduler.cancelEnvironment(environment);
    }
}