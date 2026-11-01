package com.noteshub.app;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

/**
 * Dual-purpose screen:
 *  - no extra            -> UPLOAD a new note
 *  - EXTRA_NOTE_ID given -> EDIT the details of an existing note (owner only)
 *
 * Upload safety:
 *  1. Duplicate check (same uploader + title + file name) before anything is sent.
 *  2. Firestore document ID is reserved first and used in the Storage path, so a retry always
 *     targets the same record and can never create a second one.
 *  3. The Firestore record is written ONLY after the file upload succeeded.
 *  4. If saving the record fails, the uploaded file is deleted again (rollback).
 *  5. Leaving the screen mid-upload cancels the upload task.
 */
public class UploadActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE_ID = "note_id";

    private TextInputEditText etTitle, etSubject, etDescription;
    private Spinner spSemester;
    private TextView tvFileName;
    private ProgressBar progressUpload;
    private Button btnUpload, btnChoose;

    private FirebaseFirestore db;
    private FirebaseUser user;

    private Uri selectedFileUri;
    private String selectedFileName;
    private long selectedFileSize;
    private String selectedMime;

    private String editNoteId;      // non-null => edit mode
    private String pendingDocId;    // reserved once, reused on retries
    private boolean busy = false;
    private boolean destroyed = false;

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) onFileChosen(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        db = FirebaseFirestore.getInstance();

        etTitle = findViewById(R.id.etTitle);
        etSubject = findViewById(R.id.etSubject);
        etDescription = findViewById(R.id.etDescription);
        spSemester = findViewById(R.id.spSemester);
        tvFileName = findViewById(R.id.tvFileName);
        progressUpload = findViewById(R.id.progressUpload);
        btnUpload = findViewById(R.id.btnUpload);
        btnChoose = findViewById(R.id.btnChooseFile);

        spSemester.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, AppUtils.SEMESTERS));

        editNoteId = getIntent().getStringExtra(EXTRA_NOTE_ID);
        if (editNoteId != null) {
            setupEditMode();
        } else {
            setTitle("Upload Notes");
            btnChoose.setOnClickListener(v -> filePicker.launch(new String[]{
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "image/*",
                    "text/plain"}));
        }
        btnUpload.setOnClickListener(v -> onSubmit());
    }

    // ------------------------------------------------------------------ EDIT MODE

    private void setupEditMode() {
        setTitle("Edit Notes");
        btnChoose.setVisibility(View.GONE);
        tvFileName.setVisibility(View.GONE);
        btnUpload.setText("Save Changes");
        btnUpload.setEnabled(false);

        db.collection("notes").document(editNoteId).get()
                .addOnSuccessListener(doc -> {
                    Note n = doc.toObject(Note.class);
                    if (n == null || !user.getUid().equals(n.getUploaderId())) {
                        Toast.makeText(this, "You can only edit your own notes",
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    etTitle.setText(n.getTitle());
                    etSubject.setText(n.getSubject());
                    etDescription.setText(n.getDescription());
                    for (int i = 0; i < AppUtils.SEMESTERS.length; i++) {
                        if (AppUtils.SEMESTERS[i].equals(n.getSemester())) {
                            spSemester.setSelection(i);
                            break;
                        }
                    }
                    btnUpload.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not load the note", Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void saveEdits(String title, String subject, String semester, String description) {
        setBusy(true, "Saving...");
        Map<String, Object> changes = new HashMap<>();
        changes.put("title", title);
        changes.put("subject", subject);
        changes.put("semester", semester);
        changes.put("description", description);
        changes.put("updatedAt", System.currentTimeMillis());

        db.collection("notes").document(editNoteId).update(changes)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (destroyed) return;
                    setBusy(false, "Save Changes");
                    Toast.makeText(this, "Could not save changes: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ------------------------------------------------------------------ SUBMIT

    private void onSubmit() {
        if (busy) return;   // ignore double taps

        String title = AppUtils.cleanText(etTitle.getText().toString());
        String subject = AppUtils.cleanText(etSubject.getText().toString());
        String description = AppUtils.cleanText(etDescription.getText().toString());
        String semester = spSemester.getSelectedItem().toString();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(subject)) {
            Toast.makeText(this, "Title and subject are required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.length() > 100 || subject.length() > 60 || description.length() > 500) {
            Toast.makeText(this, "Limits: title 100, subject 60, description 500 characters",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!AppUtils.isOnline(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show();
            return;
        }

        if (editNoteId != null) {
            saveEdits(title, subject, semester, description);
            return;
        }

        if (selectedFileUri == null) {
            Toast.makeText(this, "Please choose a file", Toast.LENGTH_SHORT).show();
            return;
        }
        checkDuplicateThenUpload(title, subject, semester, description);
    }

    // ------------------------------------------------------------------ UPLOAD

    private void checkDuplicateThenUpload(String title, String subject,
                                          String semester, String description) {
        setBusy(true, "Checking...");
        db.collection("notes")
                .whereEqualTo("uploaderId", user.getUid())
                .whereEqualTo("title", title)
                .whereEqualTo("fileName", selectedFileName)
                .limit(1).get()
                .addOnSuccessListener(q -> {
                    if (destroyed) return;
                    if (!q.isEmpty()) {
                        setBusy(false, "Upload Notes");
                        Toast.makeText(this, "You already uploaded this file with the same title",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    uploadFile(title, subject, semester, description);
                })
                .addOnFailureListener(e -> {
                    if (destroyed) return;
                    setBusy(false, "Upload Notes");
                    Toast.makeText(this, "Could not verify duplicates. Check your connection.",
                            Toast.LENGTH_LONG).show();
                });
    }


    private void uploadFile(String title, String subject,
                            String semester, String description) {

        if (pendingDocId == null) {
            pendingDocId = db.collection("notes").document().getId();
        }

        final String docId = pendingDocId;
        final String uploaderName = trimTo(
                user.getDisplayName() != null
                        ? user.getDisplayName() : "Unknown", 60);

        setBusy(true, "Uploading...");
        progressUpload.setProgress(0);

        File notesFolder = new File(getFilesDir(), "notes");

        if (!notesFolder.exists() && !notesFolder.mkdirs()) {
            setBusy(false, "Upload Notes");
            Toast.makeText(this, "Could not create storage folder",
                    Toast.LENGTH_LONG).show();
            return;
        }

        File destination = new File(
                notesFolder,
                docId + "_" + AppUtils.safeFileName(selectedFileName)
        );

        try (InputStream input = getContentResolver()
                .openInputStream(selectedFileUri);
             FileOutputStream output = new FileOutputStream(destination)) {

            if (input == null) {
                throw new IOException("Unable to read selected file");
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;

                if (selectedFileSize > 0) {
                    int percent = (int) (100.0 * totalBytes
                            / selectedFileSize);
                    progressUpload.setProgress(percent);
                }
            }

            output.flush();

            String localPath = destination.getAbsolutePath();

            Note note = new Note(
                    title,
                    subject,
                    semester,
                    description,
                    localPath,
                    selectedFileName,
                    localPath,
                    user.getUid(),
                    uploaderName,
                    System.currentTimeMillis()
            );

            db.collection("notes").document(docId).set(note)
                    .addOnSuccessListener(a -> {
                        Toast.makeText(this, "Notes uploaded!",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        destination.delete();
                        setBusy(false, "Upload Notes");

                        Toast.makeText(this,
                                "Could not save note details: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });

        } catch (IOException e) {
            destination.delete();
            setBusy(false, "Upload Notes");

            Toast.makeText(this,
                    "Upload failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }



    // ------------------------------------------------------------------ FILE PICKING

    private void onFileChosen(Uri uri) {
        String name = "note_" + System.currentTimeMillis();
        long size = -1;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int s = c.getColumnIndex(OpenableColumns.SIZE);
                if (n >= 0 && c.getString(n) != null) name = c.getString(n);
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s);
            }
        }
        String mime = resolveMime(uri, name);

        if (!isAllowedMime(mime)) {
            Toast.makeText(this, "Unsupported file type. Use PDF, Word, PowerPoint, image or text.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (size > AppUtils.MAX_FILE_BYTES) {
            Toast.makeText(this, "File is too large (max 25 MB)", Toast.LENGTH_LONG).show();
            return;
        }
        if (name.length() > 150) {
            Toast.makeText(this, "File name is too long. Please rename it.", Toast.LENGTH_LONG).show();
            return;
        }
        selectedFileUri = uri;
        selectedFileName = name;
        selectedFileSize = size;
        selectedMime = mime;
        tvFileName.setText(name);
    }

    private String resolveMime(Uri uri, String fileName) {
        String type = getContentResolver().getType(uri);
        if (type == null || type.equals("application/octet-stream")) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0) {
                String ext = fileName.substring(dot + 1).toLowerCase();
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            }
        }
        return type;
    }

    private boolean isAllowedMime(String m) {
        return m != null && (m.equals("application/pdf")
                || m.equals("application/msword")
                || m.equals("application/vnd.ms-powerpoint")
                || m.startsWith("application/vnd.openxmlformats-officedocument.")
                || m.startsWith("image/")
                || m.equals("text/plain"));
    }

    // ------------------------------------------------------------------ HELPERS

    private void setBusy(boolean isBusy, String buttonText) {
        busy = isBusy;
        btnUpload.setEnabled(!isBusy);
        btnUpload.setText(buttonText);
        btnChoose.setEnabled(!isBusy);
        progressUpload.setVisibility(isBusy && "Uploading...".equals(buttonText)
                ? View.VISIBLE : View.GONE);
    }

    private static String trimTo(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


}
