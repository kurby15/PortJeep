package com.example.portjeep.notifications;

import androidx.annotation.NonNull;
import com.example.portjeep.utils.NotificationHelper;
import com.example.portjeep.utils.PreferenceManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Priority 1: Handle notification payload from Firebase Console/Backend
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            NotificationHelper.showNotification(this, title, body);
        }
        
        // Priority 2: Handle data payload (Custom server implementation for Schedules/Remittances)
        if (remoteMessage.getData().size() > 0) {
            String title = remoteMessage.getData().get("title");
            String body = remoteMessage.getData().get("body");
            if (title != null && body != null) {
                NotificationHelper.showNotification(this, title, body);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        // Save token to preferences so it can be sent to the server on the next login/sync
        PreferenceManager.saveFcmToken(getApplicationContext(), token);
    }
}
