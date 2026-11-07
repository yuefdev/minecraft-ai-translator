package me.langyue.autotranslation.mc.fabric;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Tasks run at the end of every client tick (called from {@code MinecraftMixin}); stands in for Fabric API's event. */
public final class ClientTicks {
    private static final List<Runnable> TASKS = new CopyOnWriteArrayList<>();

    private ClientTicks() {
    }

    public static void add(Runnable task) {
        TASKS.add(task);
    }

    public static void run() {
        for (Runnable task : TASKS) {
            task.run();
        }
    }
}
