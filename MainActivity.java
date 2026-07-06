package com.example.lazymanager;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lazymanagerv21.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/**
 * The Today screen.
 *
 * Timeline behavior (matches the request):
 *  - Tap an hour column → the screen switches to "at that time" mode and shows ONLY
 *    the associates working then (on floor / on break / on lunch).
 *  - Tap the SAME highlighted column again → snaps back to the current time,
 *    scrolls the timeline home, and restores the live view.
 *
 * The live view shows who is out right now (with a "Back on floor" button that
 * updates the graphic), the "Lazy says" suggestion, and the minors-first Up-next queue.
 */
public class MainActivity extends AppCompatActivity {

    private ScheduleStore store;

    private TimelineView timeline;
    private HorizontalScrollView timelineScroll;
    private TextView headerDate, statLabel, statOn, statBreak, statLunch, timelineHint;
    private View panelNow, panelAt, emptyState, outCard, lazyCard;
    private LinearLayout outRows;
    private TextView lazyTitle, lazyWhy, lazySend, lazyMinorBadge;
    private TextView upNextCount, upNextEmpty, atTitle;
    private TextView laneOnCount, laneBreakCount, laneLunchCount;
    private ChipGroup laneOnGroup, laneBreakGroup, laneLunchGroup;
    private RecyclerView upNextList;
    private UpNextAdapter upNextAdapter;

    private int nowTick;
    private int selectedTick;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Keeps the clock header + now-line fresh while the app is open. */
    private final Runnable minutePulse = new Runnable() {
        @Override
        public void run() {
            int fresh = Ticks.nowTick();
            if (fresh != nowTick) {
                if (selectedTick == nowTick) selectedTick = fresh;   // follow "now" in live mode
                nowTick = fresh;
            }
            refresh();
            handler.postDelayed(this, 30_000);
        }
    };

    /** Clears the schedule at midnight even if the app is left open overnight. */
    private final Runnable midnightClear = new Runnable() {
        @Override
        public void run() {
            store.workers();   // the date check inside wipes yesterday's data
            nowTick = Ticks.nowTick();
            selectedTick = nowTick;
            refresh();
            Toast.makeText(MainActivity.this, "Fresh day — yesterday's schedule cleared.", Toast.LENGTH_LONG).show();
            handler.postDelayed(this, Ticks.millisUntilMidnight());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        store = ScheduleStore.get(this);
        nowTick = Ticks.nowTick();
        selectedTick = nowTick;

        // ── find views ──
        timeline = findViewById(R.id.timeline);
        timelineScroll = findViewById(R.id.timelineScroll);
        headerDate = findViewById(R.id.headerDate);
        timelineHint = findViewById(R.id.timelineHint);
        statLabel = findViewById(R.id.statLabel);
        statOn = findViewById(R.id.statOn);
        statBreak = findViewById(R.id.statBreak);
        statLunch = findViewById(R.id.statLunch);
        panelNow = findViewById(R.id.panelNow);
        panelAt = findViewById(R.id.panelAt);
        emptyState = findViewById(R.id.emptyState);
        outCard = findViewById(R.id.outCard);
        outRows = findViewById(R.id.outRows);
        lazyCard = findViewById(R.id.lazyCard);
        lazyTitle = findViewById(R.id.lazyTitle);
        lazyWhy = findViewById(R.id.lazyWhy);
        lazySend = findViewById(R.id.lazySend);
        lazyMinorBadge = findViewById(R.id.lazyMinorBadge);
        upNextCount = findViewById(R.id.upNextCount);
        upNextEmpty = findViewById(R.id.upNextEmpty);
        upNextList = findViewById(R.id.upNextList);
        atTitle = findViewById(R.id.atTitle);
        laneOnCount = findViewById(R.id.laneOnCount);
        laneBreakCount = findViewById(R.id.laneBreakCount);
        laneLunchCount = findViewById(R.id.laneLunchCount);
        laneOnGroup = findViewById(R.id.laneOnGroup);
        laneBreakGroup = findViewById(R.id.laneBreakGroup);
        laneLunchGroup = findViewById(R.id.laneLunchGroup);

        upNextAdapter = new UpNextAdapter((worker, kind) -> showConfirm(worker, kind));
        upNextList.setLayoutManager(new LinearLayoutManager(this));
        upNextList.setAdapter(upNextAdapter);

        findViewById(R.id.btnRoster).setOnClickListener(v ->
                startActivity(new Intent(this, RosterActivity.class)));
        findViewById(R.id.btnEmptyAdd).setOnClickListener(v ->
                startActivity(new Intent(this, RosterActivity.class)));

        // Tap an hour to inspect it; tap the same hour again to jump back to now.
        timeline.setOnTickTapListener(tick -> {
            if (tick == selectedTick && tick != nowTick) {
                selectedTick = nowTick;      // second tap → back to the current time
            } else {
                selectedTick = tick;
            }
            refresh();
            scrollToTick(selectedTick);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        nowTick = Ticks.nowTick();
        if (selectedTick == 0) selectedTick = nowTick;
        refresh();
        scrollToTick(selectedTick);
        handler.postDelayed(minutePulse, 30_000);
        handler.postDelayed(midnightClear, Ticks.millisUntilMidnight());
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minutePulse);
        handler.removeCallbacks(midnightClear);
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    private void refresh() {
        List<Worker> workers = store.workers();
        if (selectedTick < Ticks.DAY_START || selectedTick >= Ticks.DAY_END) selectedTick = nowTick;

        headerDate.setText(Ticks.headerLine());
        timeline.setData(workers, nowTick, selectedTick);

        Worker.Coverage cov = Worker.coverageAt(workers, selectedTick, nowTick);
        boolean nowMode = selectedTick == nowTick;
        statLabel.setText(nowMode ? "Right now" : "At " + Ticks.fmt(selectedTick));
        statOn.setText(String.valueOf(cov.on.size()));
        statBreak.setText(String.valueOf(cov.brk.size()));
        statLunch.setText(String.valueOf(cov.lunch.size()));
        timelineHint.setText(nowMode ? "tap an hour" : "tap it again for now");

        if (workers.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            panelNow.setVisibility(View.GONE);
            panelAt.setVisibility(View.GONE);
            return;
        }
        emptyState.setVisibility(View.GONE);
        panelNow.setVisibility(nowMode ? View.VISIBLE : View.GONE);
        panelAt.setVisibility(nowMode ? View.GONE : View.VISIBLE);

        if (nowMode) {
            buildOutNow(workers);
            buildLazySays(workers);
            List<Worker.Pending> queue = Worker.pendingQueue(workers, nowTick, 6);
            upNextAdapter.submit(queue);
            upNextCount.setText(queue.size() + " pending");
            upNextEmpty.setVisibility(queue.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            buildAtPanel(cov);
        }
    }

    /** Everyone currently out, each with a "Back on floor" button. */
    private void buildOutNow(List<Worker> workers) {
        outRows.removeAllViews();
        int shown = 0;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Worker w : workers) {
            if (w.live == Worker.LIVE_WORKING) continue;
            shown++;
            View row = inflater.inflate(R.layout.row_out_now, outRows, false);
            Ui.bindAvatar(row.findViewById(R.id.outAvatar), w.name, w.id);
            ((TextView) row.findViewById(R.id.outName)).setText(w.name);
            row.findViewById(R.id.outMinorBadge).setVisibility(w.minor ? View.VISIBLE : View.GONE);

            boolean onBreak = w.live == Worker.LIVE_BREAK;
            int sentTick = onBreak
                    ? (w.breaksDone < w.breakTicks.size() ? w.breakTicks.get(w.breaksDone) : nowTick)
                    : w.lunchTick;
            String status = (onBreak ? "On break" : "On lunch")
                    + (sentTick >= 0 ? " · sent " + Ticks.fmt(sentTick) : "");
            ((TextView) row.findViewById(R.id.outStatus)).setText(status);

            row.findViewById(R.id.outBack).setOnClickListener(v -> {
                store.markReturned(w);
                refresh();
                Toast.makeText(this, firstName(w.name) + " is back on the floor.", Toast.LENGTH_SHORT).show();
            });
            outRows.addView(row);
        }
        outCard.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    /** The top suggestion, minors first, from the pending queue. */
    @SuppressLint("SetTextI18n")
    private void buildLazySays(List<Worker> workers) {
        List<Worker.Pending> queue = Worker.pendingQueue(workers, nowTick, 4);
        Worker.Pending top = null;
        for (Worker.Pending p : queue) {
            if (p.overdue || p.tick <= nowTick + 2) {
                top = p;
                break;
            }
        }
        if (top == null) {
            lazyTitle.setText("Quiet. Nobody needs you for a bit.");
            lazyWhy.setVisibility(View.GONE);
            lazySend.setVisibility(View.GONE);
            lazyMinorBadge.setVisibility(View.GONE);
            return;
        }
        final Worker w = top.worker;
        final String kind = top.kind;
        boolean isBreak = Worker.ST_BREAK.equals(kind);

        lazyMinorBadge.setVisibility(w.minor ? View.VISIBLE : View.GONE);
        lazyTitle.setText("Send " + firstName(w.name) + " for " + (isBreak ? "a coffee." : "lunch."));

        int floorNow = Worker.coverageAt(workers, nowTick, nowTick).on.size();
        String why;
        if (w.minor) {
            why = "Minor — they go first. Scheduled for " + Ticks.fmt(top.tick) + ".";
        } else if (top.overdue) {
            why = "Overdue — was scheduled for " + Ticks.fmt(top.tick) + ".";
        } else {
            why = "Due at " + Ticks.fmt(top.tick) + ". " + floorNow + " on the floor right now.";
        }
        lazyWhy.setText(why);
        lazyWhy.setVisibility(View.VISIBLE);
        lazySend.setText("Send " + firstName(w.name));
        lazySend.setVisibility(View.VISIBLE);
        lazySend.setOnClickListener(v -> showConfirm(w, kind));
    }

    /** Time-travel mode: only the associates working at the selected tick. */
    private void buildAtPanel(Worker.Coverage cov) {
        atTitle.setText(Ticks.fmt(selectedTick));
        laneOnCount.setText(String.valueOf(cov.on.size()));
        laneBreakCount.setText(String.valueOf(cov.brk.size()));
        laneLunchCount.setText(String.valueOf(cov.lunch.size()));
        fillLane(laneOnGroup, cov.on, Worker.ST_ON);
        fillLane(laneBreakGroup, cov.brk, Worker.ST_BREAK);
        fillLane(laneLunchGroup, cov.lunch, Worker.ST_LUNCH);
    }

    private void fillLane(ChipGroup group, List<Worker> workers, String status) {
        group.removeAllViews();
        if (workers.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("nobody");
            tv.setTextSize(12);
            tv.setTextColor(getColor(R.color.sub));
            group.addView(tv);
            return;
        }
        for (Worker w : workers) {
            group.addView(Ui.makeWorkerPill(this, w, status));
        }
    }

    // ── Confirm + send ──────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private void showConfirm(final Worker w, final String kind) {
        boolean isBreak = Worker.ST_BREAK.equals(kind);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_confirm, null);
        dialog.setContentView(v);

        ((TextView) v.findViewById(R.id.confirmTitle)).setText(
                "Send " + firstName(w.name) + " for " + (isBreak ? "a 15-min break?" : "30 min of lunch?"));
        Ui.bindAvatar(v.findViewById(R.id.confirmAvatar), w.name, w.id);
        ((TextView) v.findViewById(R.id.confirmName)).setText(w.name);
        v.findViewById(R.id.confirmMinorBadge).setVisibility(w.minor ? View.VISIBLE : View.GONE);
        ((TextView) v.findViewById(R.id.confirmSub)).setText(
                capitalize(w.role) + " · on since " + Ticks.fmt(w.shiftStart));
        ((TextView) v.findViewById(R.id.confirmWhen)).setText(Ticks.fmt(nowTick));

        int floorNow = Worker.coverageAt(store.workers(), nowTick, nowTick).on.size();
        int after = Math.max(0, floorNow - 1);
        ((TextView) v.findViewById(R.id.confirmFloor)).setText(
                "Floor goes from " + floorNow + " to " + after + (after >= 3 ? ". Still fine." : ". Keep an eye out."));

        TextView send = v.findViewById(R.id.btnConfirmSend);
        send.setText("Send " + firstName(w.name));
        send.setOnClickListener(x -> {
            store.sendOn(w, kind, Ticks.nowTick());
            dialog.dismiss();
            refresh();
            Toast.makeText(this, firstName(w.name) + " is on it.", Toast.LENGTH_SHORT).show();
        });
        v.findViewById(R.id.btnNotYet).setOnClickListener(x -> dialog.dismiss());
        dialog.show();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void scrollToTick(final int tick) {
        timelineScroll.post(() -> {
            int colW = timeline.columnWidthPx();
            int target = (tick - Ticks.DAY_START) * colW - timelineScroll.getWidth() / 2 + colW / 2;
            timelineScroll.smoothScrollTo(Math.max(0, target), 0);
        });
    }

    private static String firstName(String name) {
        int i = name.indexOf(' ');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
