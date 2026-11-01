package com.noteshub.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/** Student profile: personal details, upload statistics, notification setting, account actions. */
public class ProfileActivity extends AppCompatActivity {

    private TextView tvAvatar, tvEmail, tvNotesCount, tvMemberSince, tvVerified;
    private TextInputEditText etName, etDepartment, etRollNo;
    private SwitchMaterial switchNotify;
    private Button btnSave, btnResend, btnReset, btnLogout;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private FirebaseUser user;
    private FirebaseFirestore db;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        setTitle("My Profile");
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        if (user == null) { finish(); return; }
        db = FirebaseFirestore.getInstance();

        tvAvatar = findViewById(R.id.tvAvatar);
        tvEmail = findViewById(R.id.tvEmail);
        tvNotesCount = findViewById(R.id.tvNotesCount);
        tvMemberSince = findViewById(R.id.tvMemberSince);
        tvVerified = findViewById(R.id.tvVerified);
        etName = findViewById(R.id.etName);
        etDepartment = findViewById(R.id.etDepartment);
        etRollNo = findViewById(R.id.etRollNo);
        switchNotify = findViewById(R.id.switchNotify);
        btnSave = findViewById(R.id.btnSave);
        btnResend = findViewById(R.id.btnResendVerify);
        btnReset = findViewById(R.id.btnResetPassword);
        btnLogout = findViewById(R.id.btnLogout);
        progressBar = findViewById(R.id.progressBar);

        tvEmail.setText(user.getEmail());
        etName.setText(user.getDisplayName());
        updateAvatar(user.getDisplayName());
        if (user.getMetadata() != null) {
            tvMemberSince.setText(DateFormat.format("dd MMM yyyy",
                    user.getMetadata().getCreationTimestamp()));
        }

        loadProfile();
        loadNotesCount();

        switchNotify.setChecked(AppUtils.isNotificationsEnabled(this));
        switchNotify.setOnCheckedChangeListener((b, checked) -> {
            AppUtils.setNotificationsEnabled(this, checked);
            if (checked && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });

        btnSave.setOnClickListener(v -> saveProfile());
        btnResend.setOnClickListener(v -> user.sendEmailVerification()
                .addOnSuccessListener(a -> toast("Verification email sent"))
                .addOnFailureListener(e -> toast("Could not send email: " + e.getMessage())));
        btnReset.setOnClickListener(v -> {
            if (user.getEmail() == null) return;
            auth.sendPasswordResetEmail(user.getEmail())
                    .addOnSuccessListener(a -> toast("Password reset link sent to your email"))
                    .addOnFailureListener(e -> toast("Failed: " + e.getMessage()));
        });
        btnLogout.setOnClickListener(v -> {
            AppUtils.signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (user == null) return;
        // refresh the "email verified" flag after the user clicks the link in their inbox
        user.reload().addOnCompleteListener(t -> showVerification());
    }

    private void showVerification() {
        boolean verified = user.isEmailVerified();
        tvVerified.setText(verified ? "Verified" : "Not verified");
        btnResend.setVisibility(verified ? View.GONE : View.VISIBLE);
    }

    private void loadProfile() {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    if (doc.getString("name") != null) {
                        etName.setText(doc.getString("name"));
                        updateAvatar(doc.getString("name"));
                    }
                    etDepartment.setText(doc.getString("department"));
                    etRollNo.setText(doc.getString("rollNo"));
                });
    }

    private void loadNotesCount() {
        db.collection("notes").whereEqualTo("uploaderId", user.getUid())
                .count().get(AggregateSource.SERVER)
                .addOnSuccessListener(s -> tvNotesCount.setText(String.valueOf(s.getCount())))
                .addOnFailureListener(e -> tvNotesCount.setText("-"));
    }

    private void saveProfile() {
        String name = AppUtils.cleanText(etName.getText().toString());
        String dept = AppUtils.cleanText(etDepartment.getText().toString());
        String roll = AppUtils.cleanText(etRollNo.getText().toString());

        if (name.isEmpty()) { toast("Name cannot be empty"); return; }
        if (name.length() > 60 || dept.length() > 60 || roll.length() > 60) {
            toast("Each field can have at most 60 characters");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("email", user.getEmail());
        data.put("department", dept);
        data.put("rollNo", roll);

        UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                .setDisplayName(name).build();
        user.updateProfile(req).continueWithTask(t ->
                db.collection("users").document(user.getUid()).set(data, SetOptions.merge()))
                .addOnCompleteListener(t -> {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    if (t.isSuccessful()) {
                        updateAvatar(name);
                        toast("Profile saved");
                    } else {
                        toast("Could not save profile. Try again.");
                    }
                });
    }

    private void updateAvatar(String name) {
        String letter = (name == null || name.trim().isEmpty())
                ? "?" : name.trim().substring(0, 1).toUpperCase();
        tvAvatar.setText(letter);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
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
