package com.example.lazymanager;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lazymanagerv21.R;
import com.google.android.material.chip.ChipGroup;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Add associates two ways:
 *  - One by one: name, role, shift, minor toggle. Breaks/lunch are auto-scheduled.
 *  - From a photo: snap or pick a picture of the posted schedule. Google ML Kit
 *    reads it ON-DEVICE (nothing leaves the phone), rows come back as a review
 *    checklist — uncheck misreads, tap M to flag minors — then add the batch.
 */
public class AddWorkerActivity extends AppCompatActivity {

    private ScheduleStore store;

    // One by one
    private EditText inputName;
    private ChipGroup roleGroup;
    private Spinner spStart, spEnd;
    private SwitchCompat switchMinor;
    private String selectedRole = "floor";

    // From a photo
    private ImageView photoPreview;
    private TextView btnRead, readStatus, reviewHeader, btnAddParsed, tabOne, tabPhoto;
    private View panelOne, panelPhoto;
    private RecyclerView reviewList;
    private ReviewAdapter reviewAdapter;
    private TextRecognizer recognizer;
    private Uri photoUri;
    private Uri pendingCameraUri;

    private ActivityResultLauncher<String> pickImage;
    private ActivityResultLauncher<Uri> takePhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_worker);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        store = ScheduleStore.get(this);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        findViewById(R.id.btnBackAdd).setOnClickListener(v -> finish());

        tabOne = findViewById(R.id.tabOne);
        tabPhoto = findViewById(R.id.tabPhoto);
        panelOne = findViewById(R.id.panelOne);
        panelPhoto = findViewById(R.id.panelPhoto);
        tabOne.setOnClickListener(v -> selectTab(true));
        tabPhoto.setOnClickListener(v -> selectTab(false));

        setupOneByOne();
        setupFromPhoto();
    }

    private void selectTab(boolean one) {
        panelOne.setVisibility(one ? View.VISIBLE : View.GONE);
        panelPhoto.setVisibility(one ? View.GONE : View.VISIBLE);
        tabOne.setBackgroundResource(one ? R.drawable.bg_pill_outline : android.R.color.transparent);
        tabPhoto.setBackgroundResource(one ? android.R.color.transparent : R.drawable.bg_pill_outline);
        tabOne.setTextColor(getColor(one ? R.color.ink : R.color.sub));
        tabPhoto.setTextColor(getColor(one ? R.color.sub : R.color.ink));
    }

    // ── One by one ──────────────────────────────────────────────────────────

    private void setupOneByOne() {
        inputName = findViewById(R.id.inputName);
        roleGroup = findViewById(R.id.roleGroup);
        spStart = findViewById(R.id.spStart);
        spEnd = findViewById(R.id.spEnd);
        switchMinor = findViewById(R.id.switchMinor);
        findViewById(R.id.minorRow).setOnClickListener(v -> switchMinor.toggle());

        // Role pills
        for (final String role : Worker.ROLES) {
            TextView pill = new TextView(this);
            pill.setText(role);
            pill.setTextSize(12);
            pill.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            pill.setOnClickListener(v -> {
                selectedRole = role;
                styleRolePills();
            });
            roleGroup.addView(pill);
        }
        styleRolePills();

        Ui.setupTickSpinner(spStart, false, Ticks.DAY_START, Ticks.DAY_END - 1);
        Ui.setupTickSpinner(spEnd, false, Ticks.DAY_START + 1, Ticks.DAY_END);
        int now = Ticks.nowTick();
        Ui.setSpinnerTick(spStart, now);
        Ui.setSpinnerTick(spEnd, Math.min(Ticks.DAY_END, now + 16));   // default 8 h

        findViewById(R.id.btnAddOne).setOnClickListener(v -> {
            String name = inputName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "They need a name.", Toast.LENGTH_SHORT).show();
                return;
            }
            int start = Ui.getSpinnerTick(spStart);
            int end = Ui.getSpinnerTick(spEnd);
            if (end <= start) {
                Toast.makeText(this, "Clock-out has to be after clock-in.", Toast.LENGTH_SHORT).show();
                return;
            }
            Worker w = new Worker(newId(), name, selectedRole, switchMinor.isChecked(), start, end);
            BreakRules.apply(w);
            store.add(w);
            Toast.makeText(this, name + " is on the books.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void styleRolePills() {
        for (int i = 0; i < roleGroup.getChildCount(); i++) {
            TextView pill = (TextView) roleGroup.getChildAt(i);
            boolean active = pill.getText().toString().equals(selectedRole);
            pill.setBackgroundResource(active ? R.drawable.bg_pill_ink : R.drawable.bg_pill_outline);
            pill.setTextColor(getColor(active ? R.color.surface : R.color.ink));
        }
    }

    // ── From a photo (ML Kit, fully on-device) ──────────────────────────────

    private void setupFromPhoto() {
        photoPreview = findViewById(R.id.photoPreview);
        btnRead = findViewById(R.id.btnRead);
        readStatus = findViewById(R.id.readStatus);
        reviewHeader = findViewById(R.id.reviewHeader);
        reviewList = findViewById(R.id.reviewList);
        btnAddParsed = findViewById(R.id.btnAddParsed);

        reviewAdapter = new ReviewAdapter(this::updateParsedButton);
        reviewList.setLayoutManager(new LinearLayoutManager(this));
        reviewList.setAdapter(reviewAdapter);

        pickImage = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) onImagePicked(uri); });

        takePhoto = registerForActivityResult(new ActivityResultContracts.TakePicture(),
                success -> {
                    if (Boolean.TRUE.equals(success) && pendingCameraUri != null) {
                        onImagePicked(pendingCameraUri);
                    }
                });

        View.OnClickListener launchCamera = v -> {
            try {
                File shot = new File(getCacheDir(), "camera_shots");
                if (!shot.exists()) shot.mkdirs();
                File f = new File(shot, "schedule_" + System.currentTimeMillis() + ".jpg");
                pendingCameraUri = FileProvider.getUriForFile(
                        this, getPackageName() + ".fileprovider", f);
                takePhoto.launch(pendingCameraUri);
            } catch (Exception e) {
                Toast.makeText(this, "No camera available — try the gallery instead.", Toast.LENGTH_SHORT).show();
            }
        };
        findViewById(R.id.photoDrop).setOnClickListener(launchCamera);
        findViewById(R.id.btnTake).setOnClickListener(launchCamera);
        findViewById(R.id.btnChoose).setOnClickListener(v -> pickImage.launch("image/*"));

        btnRead.setOnClickListener(v -> readSchedule());

        btnAddParsed.setOnClickListener(v -> {
            List<ScheduleTextParser.Row> rows = reviewAdapter.checkedRows();
            if (rows.isEmpty()) {
                Toast.makeText(this, "Nothing checked to add.", Toast.LENGTH_SHORT).show();
                return;
            }
            for (ScheduleTextParser.Row r : rows) {
                Worker w = new Worker(newId(), r.name, r.role, r.minor, r.start, r.end);
                BreakRules.apply(w);
                store.add(w);
            }
            Toast.makeText(this, rows.size() + " added to today.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void onImagePicked(Uri uri) {
        photoUri = uri;
        photoPreview.setImageBitmap(decodePreview(uri));
        photoPreview.setVisibility(View.VISIBLE);
        btnRead.setVisibility(View.VISIBLE);
        readStatus.setVisibility(View.GONE);
        reviewHeader.setVisibility(View.GONE);
        reviewList.setVisibility(View.GONE);
        btnAddParsed.setVisibility(View.GONE);
    }

    private void readSchedule() {
        if (photoUri == null) return;
        readStatus.setText("Squinting at the handwriting…");
        readStatus.setVisibility(View.VISIBLE);
        btnRead.setEnabled(false);
        btnRead.setAlpha(0.5f);

        InputImage image;
        try {
            image = InputImage.fromFilePath(this, photoUri);   // handles EXIF rotation
        } catch (IOException e) {
            readFailed();
            return;
        }

        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    List<ScheduleTextParser.Row> rows = ScheduleTextParser.parse(text);
                    btnRead.setEnabled(true);
                    btnRead.setAlpha(1f);
                    if (rows.isEmpty()) {
                        readStatus.setText("Couldn't find any names with shift times. Try a straighter, closer shot.");
                        return;
                    }
                    readStatus.setVisibility(View.GONE);
                    reviewAdapter.submit(rows);
                    reviewHeader.setText("Found " + rows.size()
                            + " \u2014 uncheck anyone I got wrong. Tap the M to flag a minor.");
                    reviewHeader.setVisibility(View.VISIBLE);
                    reviewList.setVisibility(View.VISIBLE);
                    btnAddParsed.setVisibility(View.VISIBLE);
                    updateParsedButton();
                })
                .addOnFailureListener(e -> readFailed());
    }

    private void readFailed() {
        btnRead.setEnabled(true);
        btnRead.setAlpha(1f);
        readStatus.setText("Couldn't read that one. Try a clearer photo, or add them one by one.");
        readStatus.setVisibility(View.VISIBLE);
    }

    private void updateParsedButton() {
        int n = reviewAdapter.checkedRows().size();
        btnAddParsed.setText(n == 0 ? "Nothing checked"
                : String.format(Locale.US, "Add %d to today", n));
        btnAddParsed.setAlpha(n == 0 ? 0.5f : 1f);
    }

    /** Camera captures can be 12 MP+; the preview only needs ~1280 px. */
    private android.graphics.Bitmap decodePreview(Uri uri) {
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                android.graphics.BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2;
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                return android.graphics.BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception e) {
            return null;   // ImageView just stays blank; Read still works from the file itself
        }
    }

    private static String newId() {
        return "W" + Long.toString(System.currentTimeMillis(), 36)
                + Long.toString((long) (Math.random() * 1296), 36);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) recognizer.close();
    }
}
