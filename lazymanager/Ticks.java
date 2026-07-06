package com.example.lazymanager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Time model: the day is a row of half-hour "ticks" counted from midnight.
 * Tick 12 = 6:00 AM, tick 27 = 1:30 PM, tick 44 = 10:00 PM.
 *
 * A break occupies one tick (15 min inside that half hour), a lunch occupies
 * one tick (the full 30 min) — same as the design prototype.
 */
public final class Ticks {

    /** First tick shown on the timeline (midnight). */
    public static final int DAY_START = 0;
    /** One past the last tick shown (11:30 PM). Total 48 ticks. */
    public static final int DAY_END = 48;

    private Ticks() {}

    /** "1:30 PM" */
    public static String fmt(int tick) {
        int h24 = tick / 2;
        int m = (tick % 2) * 30;
        int h12 = ((h24 + 11) % 12) + 1;
        String ap = (h24 < 12 || h24 >= 24) ? "AM" : "PM";
        return String.format(Locale.US, "%d:%02d %s", h12, m, ap);
    }

    /** "1p" — used for the timeline hour labels */
    public static String fmtShort(int tick) {
        int h24 = tick / 2;
        int h12 = ((h24 + 11) % 12) + 1;
        return h12 + (h24 < 12 ? "a" : "p");
    }

    /** The current half-hour tick, clamped into the visible day window. */
    public static int nowTick() {
        Calendar c = Calendar.getInstance();
        int t = c.get(Calendar.HOUR_OF_DAY) * 2 + (c.get(Calendar.MINUTE) >= 30 ? 1 : 0);
        return Math.max(DAY_START, Math.min(DAY_END - 1, t));
    }

    /** "2026-07-05" — the key used to detect a new day and wipe yesterday's data. */
    public static String todayKey() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Milliseconds until just past the next midnight (used to self-clear while the app is open). */
    public static long millisUntilMidnight() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 2);
        c.set(Calendar.MILLISECOND, 0);
        return Math.max(1000, c.getTimeInMillis() - System.currentTimeMillis());
    }

    /** "Sunday, Jul 5 · 2:07 PM" — header line on the Today screen. */
    public static String headerLine() {
        return new SimpleDateFormat("EEEE, MMM d · h:mm a", Locale.US).format(new Date());
    }
}
