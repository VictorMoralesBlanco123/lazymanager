package com.example.lazymanager;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.lazymanagerv21.R;

import java.util.ArrayList;
import java.util.List;

/** Small shared UI helpers so every screen renders workers the same way. */
public final class Ui {

    private Ui() {}

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** "Maria Santos" → "MS" */
    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && i < 2; i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    /** Deterministic soft pastel from the worker id — same hash as the prototype. */
    public static int avatarColor(String id) {
        int h = 0;
        for (int i = 0; i < id.length(); i++) {
            h = (h * 31 + id.charAt(i)) % 360;
        }
        return Color.HSVToColor(new float[]{Math.abs(h), 0.26f, 0.90f});
    }

    /** Fills a TextView-as-circle with the worker's initials and hue. */
    public static void bindAvatar(TextView tv, String name, String id) {
        tv.setText(initials(name));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(avatarColor(id));
        tv.setBackground(g);
    }

    // ── Tick spinners (clock-in / clock-out / break / lunch pickers) ────────

    /**
     * Fills a spinner with formatted half-hour times between {@code fromTick} and
     * {@code toTickInclusive}. When {@code allowNone} is true the first entry is "—".
     * Read back with {@link #getSpinnerTick}, write with {@link #setSpinnerTick}.
     */
    public static void setupTickSpinner(Spinner sp, boolean allowNone, int fromTick, int toTickInclusive) {
        List<String> labels = new ArrayList<>();
        if (allowNone) labels.add("—");
        for (int t = fromTick; t <= toTickInclusive; t++) labels.add(Ticks.fmt(t));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                sp.getContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setTag(new int[]{allowNone ? 1 : 0, fromTick, toTickInclusive});
    }

    /** -1 means "none". */
    public static void setSpinnerTick(Spinner sp, int tick) {
        int[] cfg = (int[]) sp.getTag();
        boolean none = cfg[0] == 1;
        if (tick < 0) {
            if (none) sp.setSelection(0);
            return;
        }
        int clamped = Math.max(cfg[1], Math.min(cfg[2], tick));
        sp.setSelection((none ? 1 : 0) + (clamped - cfg[1]));
    }

    /** Returns the selected tick, or -1 for "none". */
    public static int getSpinnerTick(Spinner sp) {
        int[] cfg = (int[]) sp.getTag();
        boolean none = cfg[0] == 1;
        int pos = sp.getSelectedItemPosition();
        if (none) {
            if (pos <= 0) return -1;
            return cfg[1] + pos - 1;
        }
        return cfg[1] + Math.max(0, pos);
    }

    // ── Worker pills (used in the hour-detail lanes) ────────────────────────

    /** A rounded name chip like the prototype's WorkerChip; status picks the tint. */
    public static TextView makeWorkerPill(Context c, Worker w, String status) {
        TextView tv = new TextView(c);
        String label = w.name + (w.minor ? "  · M" : "");
        tv.setText(label);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#161E26"));
        int ph = dp(c, 12);
        int pv = dp(c, 7);
        tv.setPadding(ph, pv, ph, pv);
        if (Worker.ST_BREAK.equals(status)) {
            tv.setBackgroundResource(R.drawable.bg_pill_sage);
        } else if (Worker.ST_LUNCH.equals(status)) {
            tv.setBackgroundResource(R.drawable.bg_pill_peach);
        } else {
            tv.setBackgroundResource(R.drawable.bg_pill_outline);
        }
        tv.setLayoutParams(new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }
}
