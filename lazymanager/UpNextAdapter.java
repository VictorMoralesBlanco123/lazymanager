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

/** The "Up next" queue — minors first, overdue flagged, one Send button per row. */
public class UpNextAdapter extends RecyclerView.Adapter<UpNextAdapter.Holder> {

    /** (worker, kind) → open the confirm sheet. kind is Worker.ST_BREAK or ST_LUNCH. */
    public interface OnSendListener {
        void onSend(Worker worker, String kind);
    }

    private final List<Worker.Pending> items = new ArrayList<>();
    private final OnSendListener listener;

    public UpNextAdapter(OnSendListener listener) {
        this.listener = listener;
    }

    public void submit(List<Worker.Pending> pending) {
        items.clear();
        items.addAll(pending);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_upnext, parent, false);
        return new Holder(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Worker.Pending p = items.get(position);
        Worker w = p.worker;
        boolean isBreak = Worker.ST_BREAK.equals(p.kind);

        Ui.bindAvatar(h.avatar, w.name, w.id);
        h.name.setText(w.name);
        h.minorBadge.setVisibility(w.minor ? View.VISIBLE : View.GONE);
        h.kindIcon.setImageResource(isBreak ? R.drawable.ic_coffee : R.drawable.ic_sandwich);

        String what = isBreak ? "15-min break" : "30-min lunch";
        h.detail.setText(p.overdue
                ? "Overdue · was " + Ticks.fmt(p.tick)
                : what + " · " + Ticks.fmt(p.tick));

        h.send.setBackgroundResource(isBreak ? R.drawable.bg_pill_sage : R.drawable.bg_pill_peach);
        h.send.setText("Send");
        h.send.setOnClickListener(v -> listener.onSend(w, p.kind));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView avatar, name, minorBadge, detail, send;
        final ImageView kindIcon;

        Holder(@NonNull View v) {
            super(v);
            avatar = v.findViewById(R.id.upAvatar);
            name = v.findViewById(R.id.upName);
            minorBadge = v.findViewById(R.id.upMinorBadge);
            detail = v.findViewById(R.id.upDetail);
            send = v.findViewById(R.id.upSend);
            kindIcon = v.findViewById(R.id.upKindIcon);
        }
    }
}
