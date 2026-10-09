package me.langyue.autotranslation.core.smoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.langyue.autotranslation.core.util.AtomicFiles;
import me.langyue.autotranslation.core.util.CoreLog;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Collects the results of the automated start-up check of one game version. Checks are either immediate
 * ({@link #check}) or polled once per client tick until they hold or time out ({@link #waitFor}).
 */
public final class SmokeReport {
    private final List<JsonObject> checks = new ArrayList<JsonObject>();
    private final List<Pending> pending = new ArrayList<Pending>();

    private static final class Pending {
        final String name;
        final BooleanSupplier condition;
        final Supplier<String> timeoutDetail;
        int remaining;

        Pending(String name, BooleanSupplier condition, int ticks, Supplier<String> timeoutDetail) {
            this.name = name;
            this.condition = condition;
            this.remaining = ticks;
            this.timeoutDetail = timeoutDetail;
        }
    }

    public synchronized void check(String name, boolean ok, String detail) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("ok", ok);
        if (detail != null && !detail.isEmpty()) o.addProperty("detail", detail);
        checks.add(o);
        CoreLog.info("[smoke] " + (ok ? "PASS " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : " :: " + detail));
    }

    public synchronized void waitFor(String name, BooleanSupplier condition, int ticks, Supplier<String> timeoutDetail) {
        pending.add(new Pending(name, condition, ticks, timeoutDetail));
    }

    /** Polls the pending checks; returns true once none are left. */
    public synchronized boolean tickPending() {
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            boolean done;
            try {
                done = p.condition.getAsBoolean();
            } catch (Throwable t) {
                done = false;
            }
            if (done) {
                check(p.name, true, "");
                it.remove();
            } else if (--p.remaining <= 0) {
                String detail;
                try {
                    detail = p.timeoutDetail.get();
                } catch (Throwable t) {
                    detail = t.toString();
                }
                check(p.name, false, detail);
                it.remove();
            }
        }
        return pending.isEmpty();
    }

    /** How many polled checks are still waiting (a check that must run last can wait until this is 1). */
    public synchronized int pendingCount() {
        return pending.size();
    }

    public synchronized boolean allOk() {
        for (JsonObject c : checks) {
            if (!c.get("ok").getAsBoolean()) return false;
        }
        return true;
    }

    /** The class exists (without initialising it) and declares a field with this name (a merged @Unique field). */
    public void expectField(ClassLoader loader, String className, String field, String name) {
        try {
            Class.forName(className, false, loader).getDeclaredField(field);
            check(name, true, "");
        } catch (ReflectiveOperationException e) {
            check(name, false, e.toString());
        } catch (LinkageError e) {
            check(name, false, e.toString());
        }
    }

    /** The class declares a method whose name contains the fragment (a merged mixin handler). */
    public void expectMethod(ClassLoader loader, String className, String fragment, String name) {
        try {
            Class<?> cls = Class.forName(className, false, loader);
            boolean found = false;
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().contains(fragment)) found = true;
            }
            check(name, found, found ? "" : "no merged handler containing '" + fragment + "' in " + className);
        } catch (ReflectiveOperationException e) {
            check(name, false, e.toString());
        } catch (LinkageError e) {
            check(name, false, e.toString());
        }
    }

    public synchronized JsonObject toJson(FakeOpenAiServer server) {
        JsonObject report = new JsonObject();
        report.addProperty("ok", allOk());
        report.addProperty("providerRequests", server == null ? 0 : server.requests.get());
        report.addProperty("authorizationHeaderSent", server != null && server.lastAuthorization.startsWith("Bearer "));
        JsonArray array = new JsonArray();
        for (JsonObject c : checks) array.add(c);
        report.add("checks", array);
        return report;
    }

    public void write(Path out, JsonObject report) {
        try {
            AtomicFiles.write(out, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (IOException e) {
            CoreLog.warn("Could not write the smoke test report: " + e);
        }
    }
}
