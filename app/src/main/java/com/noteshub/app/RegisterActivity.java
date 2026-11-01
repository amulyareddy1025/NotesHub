package com.noteshub.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword;
    private ProgressBar progressBar;
    private Button btnRegister;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        auth = FirebaseAuth.getInstance();

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        progressBar = findViewById(R.id.progressBar);
        btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        final String name = AppUtils.cleanText(etName.getText().toString());
        final String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (name.length() > 60) {
            Toast.makeText(this, "Name is too long (max 60 characters)",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (task.isSuccessful() && user != null) {
                        UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder()
                                .setDisplayName(name).build();
                        user.updateProfile(profile).addOnCompleteListener(t -> {
                            // Student profile document (best effort - app still works without it)
                            Map<String, Object> data = new HashMap<>();
                            data.put("name", name);
                            data.put("email", email);
                            data.put("department", "");
                            data.put("rollNo", "");
                            data.put("createdAt", System.currentTimeMillis());
                            FirebaseFirestore.getInstance().collection("users")
                                    .document(user.getUid()).set(data)
                                    .addOnCompleteListener(t2 -> {
                                        user.sendEmailVerification();
                                        setBusy(false);
                                        Toast.makeText(this,
                                                "Account created. Verification email sent.",
                                                Toast.LENGTH_LONG).show();
                                        Intent i = new Intent(this, MainActivity.class);
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(i);
                                    });
                        });
                    } else {
                        setBusy(false);
                        Toast.makeText(this, "Registration failed: "
                                + (task.getException() != null
                                ? task.getException().getMessage() : ""),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!busy);
    }
}
