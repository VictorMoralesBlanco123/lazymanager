package com.example.lazymanager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One associate on today's schedule.
 * <p>
 * Two layers of state:
 *  - The PLAN: shift window, scheduled break tick(s) and lunch tick (computed by {@link BreakRules},
 *    editable by the manager).
 *  - The LIVE state: whether they are on the floor / on break / on lunch RIGHT NOW, plus how many
 *    of their scheduled breaks and lunch they've already taken. Sending someone snaps the scheduled
 *    tick to "now", so past ticks on the timeline stay historically accurate.
 */
public class Worker {

    // Live status
    public static final int LIVE_WORKING = 0;
    public static final int LIVE_BREAK = 1;
    public static final int LIVE_LUNCH = 2;

    // Status strings used for rendering a given tick
    public static final String ST_OFF = "off";
    public static final String ST_ON = "on";
    public static final String ST_BREAK = "break";
    public static final String ST_LUNCH = "lunch";

    public static final String[] ROLES = {"cashier", "floor", "stock", "fitting", "greeter", "lead"};

    public String id;
    public String name;
    public String role = "floor";
    public boolean minor;

    public int shiftStart;               // tick, inclusive
    public int shiftEnd;                 // tick, exclusive
    public List<Integer> breakTicks = new ArrayList<>();   // sorted, up to 2
    public int lunchTick = -1;           // -1 = none scheduled

    public int breaksDone = 0;           // how many of breakTicks have been taken
    public boolean lunchDone = false;
    public int live = LIVE_WORKING;      // what they are doing right now

    public Worker() {}

    public Worker(String id, String name, String role, boolean minor, int shiftStart, int shiftEnd) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.minor = minor;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
    }

    /**
     * What this worker is doing at tick {@code t}, given the current tick {@code now}.
     * At the current tick the LIVE state is the truth (so "mark returned" updates the
     * graphic immediately); every other tick renders from the schedule.
     */
    public String statusAt(int t, int now) {
        if (t < shiftStart || t >= shiftEnd) return ST_OFF;
        if (t == now) {
            if (live == LIVE_BREAK) return ST_BREAK;
            if (live == LIVE_LUNCH) return ST_LUNCH;
            return ST_ON;
        }
        if (lunchTick >= 0 && t == lunchTick) return ST_LUNCH;
        if (breakTicks.contains(t)) return ST_BREAK;
        return ST_ON;
    }

    /** The next scheduled break they haven't taken yet, or -1. */
    public int nextBreakTick() {
        return breaksDone < breakTicks.size() ? breakTicks.get(breaksDone) : -1;
    }

    public boolean onTheClock(int now) {
        return now >= shiftStart && now < shiftEnd;
    }

    public double shiftHours() {
        return (shiftEnd - shiftStart) / 2.0;
    }

    // ── JSON persistence (org.json, no extra dependencies) ─────────────────

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("role", role);
        o.put("minor", minor);
        o.put("start", shiftStart);
        o.put("end", shiftEnd);
        o.put("lunch", lunchTick);
        o.put("breaksDone", breaksDone);
        o.put("lunchDone", lunchDone);
        o.put("live", live);
        JSONArray b = new JSONArray();
        for (int t : breakTicks) b.put(t);
        o.put("breaks", b);
        return o;
    }

    public static Worker fromJson(JSONObject o) {
        Worker w = new Worker();
        w.id = o.optString("id", "W" + System.nanoTime());
        w.name = o.optString("name", "Unknown");
        w.role = o.optString("role", "floor");
        w.minor = o.optBoolean("minor", false);
        w.shiftStart = o.optInt("start", Ticks.DAY_START);
        w.shiftEnd = o.optInt("end", Ticks.DAY_START + 16);
        w.lunchTick = o.optInt("lunch", -1);
        w.breaksDone = o.optInt("breaksDone", 0);
        w.lunchDone = o.optBoolean("lunchDone", false);
        w.live = o.optInt("live", LIVE_WORKING);
        JSONArray b = o.optJSONArray("breaks");
        if (b != null) {
            for (int i = 0; i < b.length(); i++) w.breakTicks.add(b.optInt(i));
        }
        return w;
    }

    // ── Coverage: who is where at a given tick ─────────────────────────────

    public static class Coverage {
        public final List<Worker> on = new ArrayList<>();
        public final List<Worker> brk = new ArrayList<>();
        public final List<Worker> lunch = new ArrayList<>();

        public int total() { return on.size() + brk.size() + lunch.size(); }
    }

    public static Coverage coverageAt(List<Worker> list, int t, int now) {
        Coverage c = new Coverage();
        for (Worker w : list) {
            String s = w.statusAt(t, now);
            if (ST_ON.equals(s)) c.on.add(w);
            else if (ST_BREAK.equals(s)) c.brk.add(w);
            else if (ST_LUNCH.equals(s)) c.lunch.add(w);
        }
        return c;
    }

    // ── "Up next" queue: pending breaks/lunches, minors first ──────────────

    public static class Pending {
        public final Worker worker;
        public final String kind;   // ST_BREAK or ST_LUNCH
        public final int tick;      // when it's scheduled
        public final boolean overdue;

        Pending(Worker w, String kind, int tick, boolean overdue) {
            this.worker = w;
            this.kind = kind;
            this.tick = tick;
            this.overdue = overdue;
        }
    }

    /**
     * Everyone who still has a break or lunch to take within {@code horizonTicks} of now
     * (overdue ones included). Minors always jump to the front of the queue.
     * Workers who are already out on a break/lunch are excluded.
     */
    public static List<Pending> pendingQueue(List<Worker> list, int now, int horizonTicks) {
        List<Pending> out = new ArrayList<>();
        for (Worker w : list) {
            if (!w.onTheClock(now)) continue;
            if (w.live != LIVE_WORKING) continue;
            int nb = w.nextBreakTick();
            if (nb >= 0 && nb <= now + horizonTicks) {
                out.add(new Pending(w, ST_BREAK, nb, nb <= now));
            }
            if (w.lunchTick >= 0 && !w.lunchDone && w.lunchTick <= now + horizonTicks) {
                out.add(new Pending(w, ST_LUNCH, w.lunchTick, w.lunchTick <= now));
            }
        }
        Collections.sort(out, (a, b) -> {
            int m = (b.worker.minor ? 1 : 0) - (a.worker.minor ? 1 : 0);   // minors first
            if (m != 0) return m;
            return Integer.compare(a.tick, b.tick);                        // then soonest
        });
        return out;
    }
}
