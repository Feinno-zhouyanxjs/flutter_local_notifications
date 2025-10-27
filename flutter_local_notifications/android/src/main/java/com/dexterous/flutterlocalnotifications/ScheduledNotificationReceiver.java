package com.dexterous.flutterlocalnotifications;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.core.app.NotificationManagerCompat;

import com.dexterous.flutterlocalnotifications.models.NotificationDetails;
import com.dexterous.flutterlocalnotifications.utils.StringUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import android.app.NotificationManager;
import android.service.notification.StatusBarNotification;


/** Created by michaelbui on 24/3/18. */
@Keep
public class ScheduledNotificationReceiver extends BroadcastReceiver {

  private static final String TAG = "ScheduledNotifReceiver";

  @Override
  @SuppressWarnings("deprecation")
  public void onReceive(final Context context, Intent intent) {
    String notificationDetailsJson =
        intent.getStringExtra(FlutterLocalNotificationsPlugin.NOTIFICATION_DETAILS);

    String forwardNotificationDetailsJson = notificationDetailsJson;

    String payloadType = "";

    if (StringUtils.isNullOrEmpty(notificationDetailsJson)) {
      // This logic is needed for apps that used the plugin prior to 0.3.4

      Notification notification;
      int notificationId = intent.getIntExtra("notification_id", 0);

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        notification = intent.getParcelableExtra("notification", Notification.class);
      } else {
        notification = intent.getParcelableExtra("notification");
      }

      if (notification == null) {
        // This means the notification is corrupt
        FlutterLocalNotificationsPlugin.removeNotificationFromCache(context, notificationId);
        Log.e(TAG, "Failed to parse a notification from  Intent. ID: " + notificationId);
        return;
      }

      notification.when = System.currentTimeMillis();
      NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
      notificationManager.notify(notificationId, notification);
      boolean repeat = intent.getBooleanExtra("repeat", false);
      if (!repeat) {
        FlutterLocalNotificationsPlugin.removeNotificationFromCache(context, notificationId);
      }
    } else {
      Gson gson = FlutterLocalNotificationsPlugin.buildGson();
      Type type = new TypeToken<NotificationDetails>() {}.getType();
      NotificationDetails notificationDetails = gson.fromJson(notificationDetailsJson, type);

      Map<String, Object> mapPayload = new HashMap<String, Object>();
      if(notificationDetails!=null){
        String payload = notificationDetails.payload;
        Log.i(TAG, "Intent payload string: " + payload);
        if(payload!=null){
          Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
          try {
            mapPayload = gson.fromJson(payload, mapType);
          } catch (Exception e) {
            Log.e(TAG, "Parse payload string: " + e);
          }
        }
      }

      if (mapPayload != null && !mapPayload.isEmpty()) {
        Object typeObj = mapPayload.get("type");

        payloadType = typeObj != null
            ? (typeObj instanceof String ? (String) typeObj : String.valueOf(typeObj))
            : "";
      }

      if (Objects.equals(payloadType, "disabled")) {
        Log.i(TAG, "Show a notification with type " + payloadType);
        // FlutterLocalNotificationsPlugin.cancelNotification(context, notificationDetails.id);
      }else{
        int prayerTime = -1;
        Object prayerTimeObj = mapPayload.get("prayerTimeInt");
        if (prayerTimeObj instanceof Number) {
          prayerTime = ((Number) prayerTimeObj).intValue();
        }

        if (prayerTime != -1 && (System.currentTimeMillis() / 1000L) - prayerTime > (60 * 15)) {
          Log.i(TAG, "Prayer time notification for time: " + prayerTime + " skipped as it is older than 15 minutes.");
        } else {
          mapPayload.put("isNotificationShown", true);
          // Check how many active notifications are currently in the tray
          NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
          StatusBarNotification[] activeNotifications = new StatusBarNotification[0];

          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
              NotificationManager notificationManagerRaw = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
              if (notificationManagerRaw != null) {
                  activeNotifications = notificationManagerRaw.getActiveNotifications();
              }
          }

          Log.i(TAG,"Active notifications count: " + activeNotifications.length);
          // If there are more than 6 notifications, cancel all of them except live activity
          // if (activeNotifications.length > 7) {
          //   StatusBarNotification oldest = null;

          //   for (StatusBarNotification sbn : activeNotifications) {
          //       Notification n = sbn.getNotification();
          //       if (n.extras != null) {
          //           String originalSource = n.extras.getString("original");
          //           if ("live_activity".equals(originalSource)) {
          //               continue; // Skip live activity
          //           }
          //       }

          //       if (oldest == null || sbn.getPostTime() < oldest.getPostTime()) {
          //           oldest = sbn;
          //       }
          //   }

          //   if (oldest != null) {
          //       notificationManager.cancel(oldest.getId());
          //       Log.i(TAG,"Cancelled oldest notification with ID: " + oldest.getId());
          //   }
          // }
          if (activeNotifications.length > 6) {
            final int maxAllowed = 6;
            int toRemove = activeNotifications.length - maxAllowed;

            // Collect cancellable notifications (skip live_activity)
            List<StatusBarNotification> candidates = new ArrayList<>();
            for (StatusBarNotification sbn : activeNotifications) {
              Notification n = sbn.getNotification();
              String originalSource = null;
              if (n != null && n.extras != null) {
                originalSource = n.extras.getString("original");
              }
              if ("live_activity".equals(originalSource)) {
                continue;
              }
              candidates.add(sbn);
            }

            if (!candidates.isEmpty()) {
              // Sort by post time ascending (oldest first)
              Collections.sort(candidates, new Comparator<StatusBarNotification>() {
                @Override
                public int compare(StatusBarNotification a, StatusBarNotification b) {
                  return Long.compare(a.getPostTime(), b.getPostTime());
                }
              });

              // Remove as many oldest notifications as needed (or as many candidates exist)
              int removed = 0;
              for (StatusBarNotification s : candidates) {
                if (removed >= toRemove) break;
                notificationManager.cancel(s.getId());
                Log.i(TAG, "Cancelled notification with ID: " + s.getId() + " postTime: " + s.getPostTime());
                removed++;
              }
            } else {
              Log.i(TAG, "No cancellable notifications found (all are live_activity).");
            }
          }
          // End check
          FlutterLocalNotificationsPlugin.showNotification(context, notificationDetails);
        }
      }
      FlutterLocalNotificationsPlugin.scheduleNextNotification(context, notificationDetails);

      // Update forwardNotificationDetailsJson with updated mapPayload
      notificationDetails.payload = gson.toJson(mapPayload);
      forwardNotificationDetailsJson = gson.toJson(notificationDetails);
    }

    Intent broadcastIntent = new Intent();
    broadcastIntent.setClassName(
        context.getPackageName(),
        "com.hudaring.app.OwnAlarmReceiver" // fully qualified class name
    );
    // broadcastIntent.setAction("com.hudaring.ALARM_TRIGGERED");

    // Forward extras
    broadcastIntent.putExtra("notification_id", intent.getIntExtra("notification_id", -1));
     // Forward updated payload (serialize mapPayload to JSON so it can be passed in the Intent)
    broadcastIntent.putExtra(
        FlutterLocalNotificationsPlugin.NOTIFICATION_DETAILS,
        forwardNotificationDetailsJson
    );

    // Send broadcast
    context.sendBroadcast(broadcastIntent);
  }
}
