package com.example.lazymanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The break policy, in half-hour ticks (L = shift length in ticks, so 12 ticks = 6 hours).
 *
 * ADULTS
 *   under 6 h  (L < 12)  → one 15-min break, in the middle of the shift
 *   exactly 6 h (L = 12) → one break (1/3 in) and one lunch (2/3 in)
 *   over 6 h   (L > 12)  → a lunch in the middle and two breaks, all spread
 *                          evenly: break at 1/4, lunch at 1/2, break at 3/4
 *
 * MINORS
 *   4–5.5 h  (8 ≤ L ≤ 11) → one 30-min lunch in the middle of the shift
 *   exactly 6 h (L = 12)  → lunch in the middle plus a break at 3/4
 *   over 6 h  (L > 12)    → same as an adult (lunch + 2 breaks, evenly spread)
 *   under 4 h (L < 8)     → not covered by the stated policy; the app gives
 *                           one break in the middle (adjust in the Edit sheet if needed)
 */
public final class BreakRules {

    private BreakRules() {}

    /** Recomputes breakTicks + lunchTick from the shift window and minor flag. */
    public static void apply(Worker w) {
        w.breakTicks.clear();
        w.lunchTick = -1;
        w.breaksDone = 0;
        w.lunchDone = false;

        int s = w.shiftStart;
        int L = w.shiftEnd - w.shiftStart;
        if (L < 2) return;   // under an hour: nothing to schedule

        if (w.minor) {
            if (L > 12) {
                longShift(w, s, L);
            } else if (L == 12) {
                w.lunchTick = s + Math.round(L / 2f);
                w.breakTicks.add(s + Math.round(3 * L / 4f));
            } else if (L >= 8) {
                w.lunchTick = s + Math.round(L / 2f);
            } else {
                w.breakTicks.add(s + Math.round(L / 2f));
            }
        } else {
            if (L > 12) {
                longShift(w, s, L);
            } else if (L == 12) {
                w.breakTicks.add(s + Math.round(L / 3f));
                w.lunchTick = s + Math.round(2 * L / 3f);
            } else {
                w.breakTicks.add(s + Math.round(L / 2f));
            }
        }
        normalize(w);
    }

    /** Lunch + 2 breaks, spread evenly: 1/4, 1/2 (lunch), 3/4. */
    private static void longShift(Worker w, int s, int L) {
        w.breakTicks.add(s + Math.round(L / 4f));
        w.lunchTick = s + Math.round(L / 2f);
        w.breakTicks.add(s + Math.round(3 * L / 4f));
    }

    /** Keeps every event strictly inside the shift and on its own tick. */
    private static void normalize(Worker w) {
        int lo = w.shiftStart + 1;
        int hi = w.shiftEnd - 1;
        if (hi < lo) { w.breakTicks.clear(); w.lunchTick = -1; return; }

        if (w.lunchTick >= 0) w.lunchTick = clamp(w.lunchTick, lo, hi);

        List<Integer> cleaned = new ArrayList<>();
        for (int raw : w.breakTicks) {
            int t = clamp(raw, lo, hi);
            while ((t == w.lunchTick || cleaned.contains(t)) && t < hi) t++;
            while ((t == w.lunchTick || cleaned.contains(t)) && t > lo) t--;
            if (t != w.lunchTick && !cleaned.contains(t)) cleaned.add(t);
        }
        Collections.sort(cleaned);
        w.breakTicks.clear();
        w.breakTicks.addAll(cleaned);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
