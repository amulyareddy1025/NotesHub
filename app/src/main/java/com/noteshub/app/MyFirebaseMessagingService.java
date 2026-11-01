package com.noteshub.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives "new note uploaded" messages sent to the "new_notes" topic by the Cloud Function
 * in /functions. Messages are data-only so this method runs in foreground AND background.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "new_notes_channel";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        if (data.isEmpty()) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;                                   // logged out
        if (!AppUtils.isNotificationsEnabled(this)) return;         // switched off in profile
        if (user.getUid().equals(data.get("uploaderId"))) return;   // don't notify the uploader

        String title = data.get("title");
        String subject = data.get("subject");
        String semester = data.get("semester");
        String uploader = data.get("uploaderName");

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "New notes", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Alerts when new notes are uploaded");
            nm.createNotificationChannel(channel);
        }

        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String text = (subject == null ? "" : subject)
                + (semester == null ? "" : " • " + semester)
                + (uploader == null ? "" : " — by " + uploader);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New notes: " + (title == null ? "" : title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify((int) System.currentTimeMillis(), b.build());
    }
}
