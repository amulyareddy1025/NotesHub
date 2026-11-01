package com.noteshub.app;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import android.content.ContentValues;
import android.provider.MediaStore;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.IOException;
public class MainActivity extends AppCompatActivity implements NotesAdapter.OnNoteActionListener {

    private NotesAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private Spinner spSemesterFilter, spSubjectFilter;
    private FirebaseFirestore db;
    private ListenerRegistration listenerRegistration;
    private final List<Note> loadedNotes = new ArrayList<>();

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("NotesHub");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }
        db = FirebaseFirestore.getInstance();

        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        spSemesterFilter = findViewById(R.id.spSemesterFilter);
        spSubjectFilter = findViewById(R.id.spSubjectFilter);
        RecyclerView recycler = findViewById(R.id.recyclerNotes);
        TextInputEditText etSearch = findViewById(R.id.etSearch);
        CheckBox cbMyUploads = findViewById(R.id.cbMyUploads);
        ExtendedFloatingActionButton fab = findViewById(R.id.fabUpload);

        adapter = new NotesAdapter(user.getUid(), this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        setupFilters();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.setQuery(s.toString());
                updateEmptyState();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        cbMyUploads.setOnCheckedChangeListener((btn, checked) -> {
            adapter.setOnlyMine(checked);
            updateEmptyState();
        });

        fab.setOnClickListener(v -> startActivity(new Intent(this, UploadActivity.class)));

        askNotificationPermission();
        AppUtils.updateTopicSubscription(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (db != null) listenForNotes();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && AppUtils.isNotificationsEnabled(this)
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // ---------- Semester + subject filters ----------

    private void setupFilters() {
        List<String> semesters = new ArrayList<>();
        semesters.add("All semesters");
        semesters.addAll(Arrays.asList(AppUtils.SEMESTERS));
        spSemesterFilter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, semesters));

        spSemesterFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                adapter.setSemesterFilter(pos == 0 ? null : (String) parent.getItemAtPosition(pos));
                rebuildSubjectSpinner();   // only list subjects that exist in this semester
                updateEmptyState();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        spSubjectFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                adapter.setSubjectFilter(pos == 0 ? null : (String) parent.getItemAtPosition(pos));
                updateEmptyState();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        rebuildSubjectSpinner();
    }

    /** Builds the subject dropdown from the notes loaded (for the chosen semester). */
    private void rebuildSubjectSpinner() {
        Object semItem = spSemesterFilter.getSelectedItem();
        String semester = (spSemesterFilter.getSelectedItemPosition() <= 0 || semItem == null)
                ? null : semItem.toString();
        Object prevItem = spSubjectFilter.getSelectedItem();
        String previous = prevItem == null ? null : prevItem.toString();

        // lower-case key -> first-seen display name (so "maths" and "Maths" merge)
        TreeMap<String, String> subjects = new TreeMap<>();
        for (Note n : loadedNotes) {
            if (n.getSubject() == null) continue;
            if (semester != null && !semester.equalsIgnoreCase(n.getSemester())) continue;
            String key = n.getSubject().toLowerCase();
            if (!subjects.containsKey(key)) subjects.put(key, n.getSubject());
        }
        List<String> items = new ArrayList<>();
        items.add("All subjects");
        for (Map.Entry<String, String> e : subjects.entrySet()) items.add(e.getValue());

        spSubjectFilter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, items));

        int restore = 0;
        if (previous != null) {
            for (int i = 1; i < items.size(); i++) {
                if (items.get(i).equalsIgnoreCase(previous)) { restore = i; break; }
            }
        }
        spSubjectFilter.setSelection(restore);
        if (restore == 0) adapter.setSubjectFilter(null);
    }

    // ---------- Firestore listener ----------

    /** Real-time listener: list updates automatically whenever anyone uploads a note. */
    private void listenForNotes() {
        progressBar.setVisibility(View.VISIBLE);
        listenerRegistration = db.collection("notes")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    progressBar.setVisibility(View.GONE);
                    if (error != null || snap == null) {
                        Toast.makeText(this, "Failed to load notes", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    loadedNotes.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Note n = doc.toObject(Note.class);
                        if (n != null) {
                            n.setId(doc.getId());
                            loadedNotes.add(n);
                        }
                    }
                    adapter.setNotes(loadedNotes);
                    rebuildSubjectSpinner();
                    updateEmptyState();
                });
    }

    private void updateEmptyState() {
        tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // ---------- Note actions ----------

    @Override
    public void onView(Note note) {
        Toast.makeText(this,
                "File: " + note.getFileName() + "\nPDF: " + AppUtils.isPdf(note.getFileName()),
                Toast.LENGTH_LONG).show();
        if (AppUtils.isPdf(note.getFileName())) {
            Intent i = new Intent(this, PdfViewerActivity.class);
            i.putExtra(PdfViewerActivity.EXTRA_STORAGE_PATH, note.getStoragePath());
            i.putExtra(PdfViewerActivity.EXTRA_FILE_NAME, note.getFileName());
            i.putExtra(PdfViewerActivity.EXTRA_FILE_URL, note.getFileUrl());
            startActivity(i);
        } else {
            // Non-PDF files (doc, ppt, images...) open in whatever app/browser can handle them
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(note.getFileUrl())));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No app found to open this file. Try Download.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDownload(Note note) {
        String path = note.getStoragePath();

        if (path == null || path.isEmpty()) {
            Toast.makeText(this, "PDF file path not found",
                    Toast.LENGTH_LONG).show();
            return;
        }

        File sourceFile = new File(path);

        if (!sourceFile.exists() || !sourceFile.isFile()) {
            Toast.makeText(this, "PDF file is not available",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String fileName = AppUtils.safeFileName(note.getFileName());

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS);

            Uri destinationUri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (destinationUri == null) {
                Toast.makeText(this, "Could not create download file",
                        Toast.LENGTH_LONG).show();
                return;
            }

            try (FileInputStream input = new FileInputStream(sourceFile);
                 OutputStream output = getContentResolver()
                         .openOutputStream(destinationUri)) {

                if (output == null) {
                    throw new IOException("Could not open destination");
                }

                byte[] buffer = new byte[8192];
                int length;

                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }

                output.flush();
            }

            Toast.makeText(this, "PDF saved to Downloads",
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onShare(Note note) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, note.getTitle());
        share.putExtra(Intent.EXTRA_TEXT, note.getTitle() + " (" + note.getSubject()
                + ", " + note.getSemester() + ")\nDownload: " + note.getFileUrl());
        startActivity(Intent.createChooser(share, "Share notes via"));
    }

    @Override
    public void onEdit(Note note) {
        Intent i = new Intent(this, UploadActivity.class);
        i.putExtra(UploadActivity.EXTRA_NOTE_ID, note.getId());
        startActivity(i);
    }

    @Override
    public void onDelete(Note note) {
        new AlertDialog.Builder(this)
                .setTitle("Delete notes")
                .setMessage("Delete \"" + note.getTitle() + "\" permanently?")
                .setPositiveButton("Delete", (d, w) -> deleteNote(note))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Deletes the Storage file FIRST, then the Firestore record.
     * - If the file delete fails (e.g. offline) nothing is removed, so the user can retry.
     * - If the file is already gone (OBJECT_NOT_FOUND) we continue and remove the record.
     * - If the record delete fails after the file was removed, retrying works because of the
     *   OBJECT_NOT_FOUND rule above. No orphan files, no dangling records.
     */

    private void deleteNote(Note note) {

        if (!AppUtils.isOnline(this)) {
            Toast.makeText(this,
                    "You are offline. Connect to the internet to delete.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        String path = note.getStoragePath();

        db.collection("notes").document(note.getId()).delete()
                .addOnSuccessListener(a -> {

                    if (path != null && !path.isEmpty()) {
                        File pdfFile = new File(path);
                        if (pdfFile.exists()) {
                            pdfFile.delete();
                        }
                    }

                    progressBar.setVisibility(View.GONE);

                    Toast.makeText(this, "Note deleted",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);

                    Toast.makeText(this,
                            "Could not delete note: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }


    // ---------- Menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            AppUtils.signOut();
            goToLogin();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void goToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
