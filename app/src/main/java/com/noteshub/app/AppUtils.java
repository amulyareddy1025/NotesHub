package com.noteshub.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Locale;

/** Shared constants and helper methods used across the app. */
public final class AppUtils {

    public static final String[] SEMESTERS = {
            "Semester 1", "Semester 2", "Semester 3", "Semester 4",
            "Semester 5", "Semester 6", "Semester 7", "Semester 8"};

    public static final String TOPIC_NEW_NOTES = "new_notes";
    public static final long MAX_FILE_BYTES = 25L * 1024 * 1024; // keep in sync with storage.rules

    private static final String PREFS = "noteshub_prefs";
    private static final String KEY_NOTIFY = "notify_new_notes";

    private AppUtils() { }

    public static boolean isPdf(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /** Trim and collapse repeated whitespace. */
    public static String cleanText(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    /** Safe for use in storage paths and local file names. */
    public static String safeFileName(String name) {
        return name == null ? "file" : name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static boolean isOnline(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    // ---------- Notification preference + FCM topic ----------

    public static boolean isNotificationsEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NOTIFY, true);
    }

    public static void setNotificationsEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_NOTIFY, enabled).apply();
        updateTopicSubscription(ctx);
    }

    /** Subscribes or unsubscribes this device from the "new_notes" topic. */
    public static void updateTopicSubscription(Context ctx) {
        boolean loggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        if (loggedIn && isNotificationsEnabled(ctx)) {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_NEW_NOTES);
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_NEW_NOTES);
        }
    }

    public static void signOut() {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_NEW_NOTES);
        FirebaseAuth.getInstance().signOut();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
