package com.example.lazymanager;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lazymanagerv21.R;

import java.util.ArrayList;
import java.util.List;

/**
 * "Your humans" — today's roster, filterable by role or minors,
 * with Add (one-by-one or from a photo) and tap-to-edit (including delete).
 */
public class RosterActivity extends AppCompatActivity {

    private static final String[] FILTERS =
            {"all", "minors", "cashier", "floor", "stock", "fitting", "greeter", "lead"};

    private ScheduleStore store;
    private RosterAdapter adapter;
    private TextView rosterSub, rosterEmpty;
    private LinearLayout filterRow;
    private String filter = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roster);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        store = ScheduleStore.get(this);
        rosterSub = findViewById(R.id.rosterSub);
        rosterEmpty = findViewById(R.id.rosterEmpty);
        filterRow = findViewById(R.id.filterRow);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AddWorkerActivity.class)));

        RecyclerView list = findViewById(R.id.rosterList);
        adapter = new RosterAdapter(worker ->
                EditSheet.show(this, worker, store, this::refresh));
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        buildFilterPills();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void buildFilterPills() {
        filterRow.removeAllViews();
        for (final String f : FILTERS) {
            TextView pill = new TextView(this);
            pill.setText(f);
            pill.setTextSize(12);
            pill.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Ui.dp(this, 6));
            pill.setLayoutParams(lp);
            pill.setOnClickListener(v -> {
                filter = f;
                refresh();
            });
            filterRow.addView(pill);
        }
        styleFilterPills();
    }

    private void styleFilterPills() {
        for (int i = 0; i < filterRow.getChildCount(); i++) {
            TextView pill = (TextView) filterRow.getChildAt(i);
            boolean active = pill.getText().toString().equals(filter);
            pill.setBackgroundResource(active ? R.drawable.bg_pill_ink : R.drawable.bg_pill_outline);
            pill.setTextColor(getColor(active ? R.color.surface : R.color.ink));
        }
    }

    @SuppressLint("SetTextI18n")
    private void refresh() {
        List<Worker> all = store.workers();
        int now = Ticks.nowTick();

        List<Worker> shown = new ArrayList<>();
        for (Worker w : all) {
            if ("all".equals(filter)
                    || ("minors".equals(filter) && w.minor)
                    || w.role.equals(filter)) {
                shown.add(w);
            }
        }
        adapter.submit(shown);

        int queued = Worker.pendingQueue(all, now, Integer.MAX_VALUE / 4).size();
        rosterSub.setText(all.size() + " today · " + queued + (queued == 1 ? " break queued" : " breaks queued"));
        rosterEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        styleFilterPills();
    }
}
