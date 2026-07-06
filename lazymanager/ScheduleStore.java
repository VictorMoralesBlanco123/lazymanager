package com.example.lazymanager;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * All storage is LOCAL: a JSON blob in SharedPreferences, tagged with today's date.
 * Any time the stored date doesn't match today, the whole schedule is wiped —
 * so the app self-clears at midnight (a timer in MainActivity handles the case
 * where the app is left open across midnight; this date check handles every
 * other case). Nothing ever leaves the device.
 */
public class ScheduleStore {

    private static final String PREFS = "lazy_manager_store";
    private static final String KEY_DAY = "day";
    private static final String KEY_WORKERS = "workers";

    private static ScheduleStore instance;

    private final SharedPreferences prefs;
    private List<Worker> cache;

    public static synchronized ScheduleStore get(Context c) {
        if (instance == null) instance = new ScheduleStore(c.getApplicationContext());
        return instance;
    }

    private ScheduleStore(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Today's roster. Anything saved on a previous day is cleared automatically. */
    public List<Worker> workers() {
        String storedDay = prefs.getString(KEY_DAY, "");
        if (!Ticks.todayKey().equals(storedDay)) {
            cache = new ArrayList<>();
            persist();
        } else if (cache == null) {
            load();
        }
        return cache;
    }

    private void load() {
        cache = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_WORKERS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                cache.add(Worker.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            cache = new ArrayList<>();
        }
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        if (cache != null) {
            try {
                for (Worker w : cache) arr.put(w.toJson());
            } catch (JSONException ignored) { }
        }
        prefs.edit()
                .putString(KEY_DAY, Ticks.todayKey())
                .putString(KEY_WORKERS, arr.toString())
                .apply();
    }

    /** Call after mutating a Worker you got from {@link #workers()}. */
    public void save() {
        if (cache != null) persist();
    }

    public void add(Worker w) {
        workers().add(w);
        persist();
    }

    public void remove(String id) {
        List<Worker> ws = workers();
        Iterator<Worker> it = ws.iterator();
        while (it.hasNext()) {
            if (it.next().id.equals(id)) it.remove();
        }
        persist();
    }

    public Worker byId(String id) {
        for (Worker w : workers()) {
            if (w.id.equals(id)) return w;
        }
        return null;
    }

    public void clearAll() {
        cache = new ArrayList<>();
        persist();
    }

    // ── Day operations ──────────────────────────────────────────────────────

    /**
     * Sends a worker on a break or lunch RIGHT NOW: flips their live status and
     * snaps the scheduled tick to the current tick, so the timeline stays honest
     * about when it actually happened.
     */
    public void sendOn(Worker w, String kind, int now) {
        if (Worker.ST_LUNCH.equals(kind)) {
            w.lunchTick = now;
            w.lunchDone = false;
            w.live = Worker.LIVE_LUNCH;
        } else {
            if (w.breaksDone < w.breakTicks.size()) {
                w.breakTicks.set(w.breaksDone, now);   // snap the next scheduled break to now
            } else {
                w.breakTicks.add(now);                 // extra, unscheduled break — manager's call
            }
            w.live = Worker.LIVE_BREAK;
        }
        persist();
    }

    /** Marks a worker back on the floor after a break or lunch; the graphic updates from this. */
    public void markReturned(Worker w) {
        if (w.live == Worker.LIVE_BREAK) {
            w.breaksDone = Math.min(w.breaksDone + 1, w.breakTicks.size());
        } else if (w.live == Worker.LIVE_LUNCH) {
            w.lunchDone = true;
        }
        w.live = Worker.LIVE_WORKING;
        persist();
    }
}
