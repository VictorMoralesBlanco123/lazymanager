package com.example.lazymanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lazymanagerv21.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The review checklist after OCR: toggle rows on/off, toggle the M badge for minors. */
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Holder> {

    private final List<ScheduleTextParser.Row> items = new ArrayList<>();
    private final Runnable onChanged;

    public ReviewAdapter(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public void submit(List<ScheduleTextParser.Row> rows) {
        items.clear();
        items.addAll(rows);
        notifyDataSetChanged();
    }

    public List<ScheduleTextParser.Row> checkedRows() {
        List<ScheduleTextParser.Row> out = new ArrayList<>();
        for (ScheduleTextParser.Row r : items) {
            if (r.checked) out.add(r);
        }
        return out;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_review, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final ScheduleTextParser.Row r = items.get(position);
        h.name.setText(r.name);
        h.role.setText(r.role);
        h.times.setText(String.format(Locale.US, "%s\u2013%s \u00b7 %.1fh",
                Ticks.fmt(r.start), Ticks.fmt(r.end), (r.end - r.start) / 2.0));

        h.root.setAlpha(r.checked ? 1f : 0.45f);
        h.check.setBackgroundResource(r.checked ? R.drawable.bg_pill_ink : R.drawable.bg_pill_outline);
        h.check.setImageResource(r.checked ? R.drawable.ic_check : 0);

        h.minor.setBackgroundResource(r.minor ? R.drawable.bg_pill_butter : R.drawable.bg_pill_outline);
        h.minor.setAlpha(r.minor ? 1f : 0.5f);

        View.OnClickListener toggleCheck = v -> {
            r.checked = !r.checked;
            notifyItemChanged(h.getAdapterPosition());
            onChanged.run();
        };
        h.check.setOnClickListener(toggleCheck);
        h.root.setOnClickListener(toggleCheck);

        h.minor.setOnClickListener(v -> {
            r.minor = !r.minor;
            notifyItemChanged(h.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView check;
        final TextView name, times, role, minor;

        Holder(@NonNull View v) {
            super(v);
            root = v.findViewById(R.id.revRoot);
            check = v.findViewById(R.id.revCheck);
            name = v.findViewById(R.id.revName);
            times = v.findViewById(R.id.revTimes);
            role = v.findViewById(R.id.revRole);
            minor = v.findViewById(R.id.revMinor);
        }
    }
}
