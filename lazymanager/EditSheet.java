package com.example.lazymanager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.example.lazymanagerv21.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Edit an associate from the roster: rename, move the shift, hand-place the
 * break/lunch ticks (or re-run the auto rules), toggle minor, quick-send them
 * on a break or lunch, mark them back on the floor, clock them out early —
 * or delete them from today entirely.
 */
public final class EditSheet {

    private EditSheet() {}

    @SuppressLint("SetTextI18n")
    public static void show(Activity activity, Worker w, ScheduleStore store, Runnable onChanged) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View v = LayoutInflater.from(activity).inflate(R.layout.sheet_edit, null);
        dialog.setContentView(v);

        EditText editName = v.findViewById(R.id.editName);
        TextView editSub = v.findViewById(R.id.editSub);
        Spinner spStart = v.findViewById(R.id.spEditStart);
        Spinner spEnd = v.findViewById(R.id.spEditEnd);
        Spinner spBreak1 = v.findViewById(R.id.spEditBreak1);
        Spinner spBreak2 = v.findViewById(R.id.spEditBreak2);
        Spinner spLunch = v.findViewById(R.id.spEditLunch);
        SwitchCompat switchMinor = v.findViewById(R.id.switchEditMinor);
        TextView editError = v.findViewById(R.id.editError);

        Ui.bindAvatar(v.findViewById(R.id.editAvatar), w.name, w.id);
        editName.setText(w.name);
        editSub.setText(capitalize(w.role) + " · "
                + String.format(Locale.US, "%.1f h shift", w.shiftHours()));
        switchMinor.setChecked(w.minor);
        v.findViewById(R.id.editMinorRow).setOnClickListener(x -> switchMinor.toggle());

        Ui.setupTickSpinner(spStart, false, Ticks.DAY_START, Ticks.DAY_END - 1);
        Ui.setupTickSpinner(spEnd, false, Ticks.DAY_START + 1, Ticks.DAY_END);
        Ui.setupTickSpinner(spBreak1, true, Ticks.DAY_START, Ticks.DAY_END - 1);
        Ui.setupTickSpinner(spBreak2, true, Ticks.DAY_START, Ticks.DAY_END - 1);
        Ui.setupTickSpinner(spLunch, true, Ticks.DAY_START, Ticks.DAY_END - 1);

        Ui.setSpinnerTick(spStart, w.shiftStart);
        Ui.setSpinnerTick(spEnd, w.shiftEnd);
        Ui.setSpinnerTick(spBreak1, w.breakTicks.size() > 0 ? w.breakTicks.get(0) : -1);
        Ui.setSpinnerTick(spBreak2, w.breakTicks.size() > 1 ? w.breakTicks.get(1) : -1);
        Ui.setSpinnerTick(spLunch, w.lunchTick);

        // Re-run the policy against whatever shift + minor flag is currently picked
        v.findViewById(R.id.btnAuto).setOnClickListener(x -> {
            Worker temp = new Worker("tmp", "tmp", w.role, switchMinor.isChecked(),
                    Ui.getSpinnerTick(spStart), Ui.getSpinnerTick(spEnd));
            if (temp.shiftEnd <= temp.shiftStart) {
                editError.setVisibility(View.VISIBLE);
                return;
            }
            editError.setVisibility(View.GONE);
            BreakRules.apply(temp);
            Ui.setSpinnerTick(spBreak1, temp.breakTicks.size() > 0 ? temp.breakTicks.get(0) : -1);
            if (temp.breakTicks.size() > 1) {
                Ui.setSpinnerTick(spBreak2, temp.breakTicks.get(1));
            } else {
                spBreak2.setSelection(0);   // "—"
            }
            if (temp.lunchTick >= 0) {
                Ui.setSpinnerTick(spLunch, temp.lunchTick);
            } else {
                spLunch.setSelection(0);
            }
        });

        // ── Quick actions ──
        v.findViewById(R.id.btnQBreak).setOnClickListener(x -> {
            store.sendOn(w, Worker.ST_BREAK, Ticks.nowTick());
            dialog.dismiss();
            onChanged.run();
            Toast.makeText(activity, firstName(w.name) + " is on break.", Toast.LENGTH_SHORT).show();
        });
        v.findViewById(R.id.btnQLunch).setOnClickListener(x -> {
            store.sendOn(w, Worker.ST_LUNCH, Ticks.nowTick());
            dialog.dismiss();
            onChanged.run();
            Toast.makeText(activity, firstName(w.name) + " is at lunch.", Toast.LENGTH_SHORT).show();
        });
        v.findViewById(R.id.btnQBackOnFloor).setOnClickListener(x -> {
            store.markReturned(w);
            dialog.dismiss();
            onChanged.run();
            Toast.makeText(activity, firstName(w.name) + " is back on the floor.", Toast.LENGTH_SHORT).show();
        });
        v.findViewById(R.id.btnQClockOut).setOnClickListener(x -> {
            if (w.live != Worker.LIVE_WORKING) store.markReturned(w);
            w.shiftEnd = Math.max(Ticks.nowTick(), w.shiftStart + 1);
            trimScheduleToShift(w);
            store.save();
            dialog.dismiss();
            onChanged.run();
            Toast.makeText(activity, firstName(w.name) + " is clocked out.", Toast.LENGTH_SHORT).show();
        });

        // ── Delete ──
        v.findViewById(R.id.btnDelete).setOnClickListener(x ->
                new AlertDialog.Builder(activity)
                        .setTitle("Remove " + firstName(w.name) + "?")
                        .setMessage("They come off today's schedule and timeline. This can't be undone (today, anyway).")
                        .setNegativeButton("Keep them", null)
                        .setPositiveButton("Remove", (d, which) -> {
                            store.remove(w.id);
                            dialog.dismiss();
                            onChanged.run();
                            Toast.makeText(activity, firstName(w.name) + " removed from today.", Toast.LENGTH_SHORT).show();
                        })
                        .show());

        // ── Save ──
        v.findViewById(R.id.btnSave).setOnClickListener(x -> {
            int start = Ui.getSpinnerTick(spStart);
            int end = Ui.getSpinnerTick(spEnd);
            if (end <= start) {
                editError.setVisibility(View.VISIBLE);
                return;
            }
            String name = editName.getText().toString().trim();
            if (!name.isEmpty()) w.name = name;
            w.shiftStart = start;
            w.shiftEnd = end;
            w.minor = switchMinor.isChecked();
        w.lunchTick = Ui.getSpinnerTick(spLunch);

        List<Integer> breaks = new ArrayList<>();
        int b1 = Ui.getSpinnerTick(spBreak1);
        int b2 = Ui.getSpinnerTick(spBreak2);
        if (b1 >= 0) breaks.add(b1);
        if (b2 >= 0 && !breaks.contains(b2)) breaks.add(b2);
        Collections.sort(breaks);
        w.breakTicks.clear();
        w.breakTicks.addAll(breaks);
        w.breaksDone = Math.min(w.breaksDone, w.breakTicks.size());

        store.save();
        dialog.dismiss();
        onChanged.run();
        Toast.makeText(activity, "Saved.", Toast.LENGTH_SHORT).show();
    });

    dialog.show();
}

/** After an early clock-out, drop any scheduled break/lunch that no longer fits. */
private static void trimScheduleToShift(Worker w) {
    List<Integer> kept = new ArrayList<>();
    for (int t : w.breakTicks) {
        if (t > w.shiftStart && t < w.shiftEnd) kept.add(t);
    }
    w.breakTicks.clear();
    w.breakTicks.addAll(kept);
    w.breaksDone = Math.min(w.breaksDone, w.breakTicks.size());
    if (w.lunchTick >= 0 && (w.lunchTick <= w.shiftStart || w.lunchTick >= w.shiftEnd)) {
        w.lunchTick = -1;
        w.lunchDone = false;
    }
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
