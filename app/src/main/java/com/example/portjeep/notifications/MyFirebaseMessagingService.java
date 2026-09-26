package com.example.portjeep.notifications;

import android.util.Log;
import androidx.annotation.NonNull;
import com.example.portjeep.utils.NotificationHelper;
import com.example.portjeep.utils.PreferenceManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.Locale;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseMsgService";

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
            String type = remoteMessage.getData().get("type");
            
            if ("remittance".equalsIgnoreCase(type)) {
                handleRemittanceData(remoteMessage.getData().get("remittance"));
            } else {
                // Default handling for other types or generic payloads
                String title = remoteMessage.getData().get("title");
                String body = remoteMessage.getData().get("body");
                if (title != null && body != null) {
                    NotificationHelper.showNotification(this, title, body);
                }
            }
        }
    }

    private void handleRemittanceData(String remittanceJson) {
        if (remittanceJson == null) return;
        try {
            JSONObject obj = new JSONObject(remittanceJson);
            // Based on the API structure found in the project: gross, net, employeeCut
            double gross = obj.optDouble("gross", 0.0);
            double share = obj.optDouble("employeeCut", 0.0);
            String jeep = obj.optString("jeep", "Assigned Unit");
            
            String title = "New Remittance Recorded";
            String body = String.format(Locale.US, "Unit: %s | Gross: P%.2f | Your Share: P%.2f",
                    jeep, gross, share);
            
            NotificationHelper.showNotification(this, title, body);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing remittance data payload", e);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        // Save token to preferences so it can be sent to the server on the next login/sync
        PreferenceManager.saveFcmToken(getApplicationContext(), token);
    }
}
