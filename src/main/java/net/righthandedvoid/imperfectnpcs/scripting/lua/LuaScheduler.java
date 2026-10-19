package net.righthandedvoid.imperfectnpcs.scripting.lua;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps scripts that called wait() and resumes each one on the right server
 * tick. Everything here happens on the server thread.
 */
final class LuaScheduler {
    private static final Logger logger = LogUtils.getLogger();

    /** How many scripts may be waiting at once, so a command spammer can't pile up thousands. */
    private static final int MAX_WAITING_SCRIPTS = 256;

    private record Waiting(LuaScriptTask task, int resumeAtTick) {
    }

    private final List<Waiting> waiting = new ArrayList<>();

    /** The last tick tick() was called on, or -1 if it has never been called. */
    private int lastTick = -1;

    /**
     * True if tick() is being called every server tick. If it isn't, waiting
     * scripts would silently never resume.
     */
    boolean isTicking(int currentTick) {
        return lastTick >= currentTick - 1;
    }

    boolean isFull() {
        return waiting.size() >= MAX_WAITING_SCRIPTS;
    }

    /** Queues a task that just called wait(). */
    void add(LuaScriptTask task, int currentTick) {
        waiting.add(new Waiting(task, currentTick + task.getRequestedWaitTicks()));
    }

    void tick(int currentTick) {
        lastTick = currentTick;

        if (waiting.isEmpty()) return;

        // Take the due tasks out first, then resume them. A resumed task that
        // waits again is re-added, which would break an iterator mid-loop.
        List<LuaScriptTask> due = new ArrayList<>();
        Iterator<Waiting> iterator = waiting.iterator();

        while (iterator.hasNext()) {
            Waiting entry = iterator.next();

            if (entry.resumeAtTick() <= currentTick) {
                iterator.remove();
                due.add(entry.task());
            }
        }

        for (LuaScriptTask task : due) {
            LuaScriptTask.Result result = task.resume();

            switch (result.status()) {
                case WAITING -> add(task, currentTick);
                case FAILED -> task.reportFailure(result.message());
                case FINISHED -> logger.debug("{} finished", task.getScriptName());
            }
        }
    }

    /** Drops every waiting script. There are no threads to stop, so forgetting them is enough. */
    int cancelAll() {
        int count = waiting.size();

        waiting.clear();

        return count;
    }
}