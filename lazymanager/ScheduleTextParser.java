package com.example.lazymanager;

import android.graphics.Rect;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns ML Kit Text Recognition output into schedule rows.
 *
 * Strategy:
 *  1. Flatten every recognized line, then cluster lines into VISUAL rows using their
 *     bounding-box vertical centers (schedules are tables; one employee per row).
 *  2. Within each row (sorted left→right), find the shift times:
 *       - Prefer explicit AM/PM times ("9:00 AM", "9a"). The earliest is the clock-in,
 *         the latest is the clock-out (columns between them — e.g. a printed lunch —
 *         are ignored, because this app schedules breaks with its own rules).
 *       - Otherwise fall back to a bare range ("9-5", "14:00-22:00") with sensible
 *         AM/PM inference.
 *  3. Whatever text sits before the first time is the name. "minor" / "(m)" anywhere
 *     in the row flags them as a minor (and can be toggled on the review screen).
 *
 * Times are rounded to the nearest half-hour tick, matching the rest of the app.
 */
public final class ScheduleTextParser {

    private ScheduleTextParser() {}

    /** One parsed schedule row, shown on the review checklist before being added. */
    public static class Row {
        public String name;
        public int start;        // tick
        public int end;          // tick
        public boolean minor;
        public String role = "floor";
        public boolean checked = true;
    }

    // "9", "9:30", "9 AM", "9:30pm", "9a", "9 a.m." — meridiem REQUIRED here
    private static final Pattern P_MERIDIEM = Pattern.compile(
            "(\\d{1,2})(?::([0-5]\\d))?\\s*([APap])(?:\\.?\\s*[Mm]\\.?)?(?![A-Za-z])");

    // "9-5", "9:30 - 17:00", "9 to 5" — no meridiem, so we infer it
    private static final Pattern P_RANGE = Pattern.compile(
            "(\\d{1,2})(?::([0-5]\\d))?\\s*(?:-|\u2013|\u2014|~|to)\\s*(\\d{1,2})(?::([0-5]\\d))?");

    private static final String[] SKIP_WORDS = {
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "week", "date", "total", "hours", "schedule", "page"
    };

    public static List<Row> parse(Text text) {
        // 1. Flatten lines that have bounding boxes
        List<Text.Line> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line l : block.getLines()) {
                if (l.getBoundingBox() != null) lines.add(l);
            }
        }
        List<Row> out = new ArrayList<>();
        if (lines.isEmpty()) return out;

        // Median line height → row clustering threshold
        List<Integer> heights = new ArrayList<>();
        for (Text.Line l : lines) heights.add(l.getBoundingBox().height());
        Collections.sort(heights);
        int medianH = Math.max(8, heights.get(heights.size() / 2));

        Collections.sort(lines, Comparator.comparingInt(l -> centerY(l.getBoundingBox())));

        // 2. Cluster into visual rows
        List<List<Text.Line>> rows = new ArrayList<>();
        List<Text.Line> current = new ArrayList<>();
        int lastY = Integer.MIN_VALUE;
        for (Text.Line l : lines) {
            int cy = centerY(l.getBoundingBox());
            if (!current.isEmpty() && cy - lastY > medianH * 0.7f) {
                rows.add(current);
                current = new ArrayList<>();
            }
            current.add(l);
            lastY = cy;
        }
        if (!current.isEmpty()) rows.add(current);

        // 3. Parse each row left→right
        for (List<Text.Line> rowLines : rows) {
            Collections.sort(rowLines, Comparator.comparingInt(l -> l.getBoundingBox().left));
            StringBuilder sb = new StringBuilder();
            for (Text.Line l : rowLines) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(l.getText());
            }
            Row r = parseRow(sb.toString());
            if (r != null) out.add(r);
        }
        return out;
    }

    private static int centerY(Rect r) {
        return r.top + r.height() / 2;
    }

    // Visible for straightforward testing
    static Row parseRow(String raw) {
        if (raw == null) return null;
        String rowText = raw.trim();
        if (rowText.length() < 3) return null;

        String lower = rowText.toLowerCase(Locale.US);
        for (String skip : SKIP_WORDS) {
            if (lower.contains(skip)) return null;
        }

        int startTick = -1, endTick = -1;
        int firstTimeIdx = -1, lastTimeEnd = -1;

        // Pass 1: explicit AM/PM times — take earliest as clock-in, latest as clock-out
        Matcher m = P_MERIDIEM.matcher(rowText);
        List<int[]> found = new ArrayList<>();   // {tick, matchStart, matchEnd}
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h < 1 || h > 12) continue;
            int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            boolean pm = "p".equalsIgnoreCase(m.group(3));
            int h24 = (h % 12) + (pm ? 12 : 0);
            found.add(new int[]{toTick(h24, min), m.start(), m.end()});
        }
        if (found.size() >= 2) {
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int[] f : found) {
                min = Math.min(min, f[0]);
                max = Math.max(max, f[0]);
            }
            startTick = min;
            endTick = max;
            firstTimeIdx = found.get(0)[1];
            lastTimeEnd = found.get(found.size() - 1)[2];
        } else {
            // Pass 2: bare range, infer AM/PM
            Matcher r = P_RANGE.matcher(rowText);
            if (r.find()) {
                int h1 = Integer.parseInt(r.group(1));
                int m1 = r.group(2) != null ? Integer.parseInt(r.group(2)) : 0;
                int h2 = Integer.parseInt(r.group(3));
                int m2 = r.group(4) != null ? Integer.parseInt(r.group(4)) : 0;
                int[] resolved = resolveBareRange(h1, m1, h2, m2);
                if (resolved != null) {
                    startTick = resolved[0];
                    endTick = resolved[1];
                    firstTimeIdx = r.start();
                    lastTimeEnd = r.end();
                }
            }
        }

        if (startTick < 0 || endTick < 0) return null;
        startTick = clampTick(startTick);
        endTick = clampTick(endTick);
        if (endTick <= startTick) return null;

        Row row = new Row();
        row.start = startTick;
        row.end = endTick;
        row.minor = lower.contains("minor") || lower.matches(".*\\((m|min)\\).*");
        for (String role : Worker.ROLES) {
            if (lower.contains(role)) {
                row.role = role;
                break;
            }
        }

        String namePart = firstTimeIdx > 0
                ? rowText.substring(0, firstTimeIdx)
                : (lastTimeEnd >= 0 && lastTimeEnd < rowText.length() ? rowText.substring(lastTimeEnd) : "");
        row.name = cleanName(namePart, row.role);
        if (row.name == null) return null;
        return row;
    }

    /** Infers AM/PM for a bare "9-5" style range; understands 24-hour times too. */
    private static int[] resolveBareRange(int h1, int m1, int h2, int m2) {
        // Obvious 24-hour clock ("14:00-22:00", "06:00-14:00")
        if (h1 >= 13 || h2 >= 13 || h1 == 0) {
            if (h1 > 23 || h2 > 23) return null;
            int a = toTick(h1, m1);
            int b = toTick(h2, m2);
            return b > a ? new int[]{a, b} : null;
        }
        if (h1 < 1 || h1 > 12 || h2 < 1 || h2 > 12) return null;

        // Clock-in: 6–11 reads as AM, 12 as noon, 1–5 as afternoon
        // (afternoon starts are far more common in retail than pre-dawn ones)
        int startH24 = (h1 >= 6 && h1 <= 11) ? h1 : (h1 % 12) + 12;
        int a = toTick(startH24, m1);

        // Clock-out: pick the AM/PM that lands after the clock-in with the sanest length
        int endAm = toTick(h2 % 12, m2);
        int endPm = toTick((h2 % 12) + 12, m2);
        int b = -1;
        if (endAm > a && endAm - a <= 24) b = endAm;
        if (endPm > a && endPm - a <= 24) {
            if (b < 0 || Math.abs((endPm - a) - 16) < Math.abs((b - a) - 16)) b = endPm;   // prefer ~8 h
        }
        if (b < 0) return null;
        return new int[]{a, b};
    }

    /** Hour+minute → half-hour tick, rounded to the nearest half hour. */
    private static int toTick(int h24, int min) {
        int t = h24 * 2;
        if (min >= 45) t += 2;
        else if (min >= 15) t += 1;
        return t;
    }

    private static int clampTick(int t) {
        return Math.max(Ticks.DAY_START, Math.min(Ticks.DAY_END, t));
    }

    private static String cleanName(String s, String detectedRole) {
        if (s == null) return null;
        String cleaned = s.replaceAll("(?i)\\bminor\\b", " ")
                .replaceAll("(?i)\\b" + detectedRole + "\\b", " ")
                .replaceAll("[^A-Za-z .,'\u2019-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = cleaned.replaceAll("^[.,'\u2019\\- ]+", "").replaceAll("[.,'\u2019\\- ]+$", "");

        int letters = 0;
        for (char c : cleaned.toCharArray()) {
            if (Character.isLetter(c)) letters++;
        }
        if (letters < 2) return null;

        String low = cleaned.toLowerCase(Locale.US);
        if (low.equals("name") || low.equals("employee") || low.equals("associate")
                || low.equals("shift") || low.equals("time") || low.equals("in") || low.equals("out")) {
            return null;
        }
        if (cleaned.length() > 28) cleaned = cleaned.substring(0, 28).trim();
        return cleaned;
    }
}
