package com.example.lazymanager;

import android.annotation.SuppressLint;
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

/** Roster rows: avatar, name (+M / out badges), role + shift, break/lunch pips. Tap to edit. */
public class RosterAdapter extends RecyclerView.Adapter<RosterAdapter.Holder> {

    public interface OnWorkerTapListener {
        void onWorkerTapped(Worker worker);
    }

    private final List<Worker> items = new ArrayList<>();
    private final OnWorkerTapListener listener;

    public RosterAdapter(OnWorkerTapListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<Worker> workers) {
        items.clear();
        items.addAll(workers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_roster, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Worker w = items.get(position);
        Ui.bindAvatar(h.avatar, w.name, w.id);
        h.name.setText(w.name);
        h.minorBadge.setVisibility(w.minor ? View.VISIBLE : View.GONE);
        h.liveBadge.setVisibility(w.live != Worker.LIVE_WORKING ? View.VISIBLE : View.GONE);
        if (w.live != Worker.LIVE_WORKING) {
            h.liveBadge.setText(w.live == Worker.LIVE_BREAK ? "on break" : "on lunch");
            h.liveBadge.setBackgroundResource(w.live == Worker.LIVE_BREAK
                    ? R.drawable.bg_pill_sage : R.drawable.bg_pill_peach);
        }

        String role = w.role.isEmpty() ? "floor"
                : Character.toUpperCase(w.role.charAt(0)) + w.role.substring(1);
        h.detail.setText(role + " · " + Ticks.fmt(w.shiftStart) + "\u2013" + Ticks.fmt(w.shiftEnd));

        h.breakPill.setAlpha(w.breakTicks.isEmpty() ? 0.3f : 1f);
        h.lunchPill.setAlpha(w.lunchTick < 0 ? 0.3f : 1f);

        h.itemView.setOnClickListener(v -> listener.onWorkerTapped(w));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView avatar, name, minorBadge, liveBadge, detail;
        final ImageView breakPill, lunchPill;

        Holder(@NonNull View v) {
            super(v);
            avatar = v.findViewById(R.id.rowAvatar);
            name = v.findViewById(R.id.rowName);
            minorBadge = v.findViewById(R.id.rowMinorBadge);
            liveBadge = v.findViewById(R.id.rowLiveBadge);
            detail = v.findViewById(R.id.rowDetail);
            breakPill = v.findViewById(R.id.rowBreakPill);
            lunchPill = v.findViewById(R.id.rowLunchPill);
        }
    }
}
