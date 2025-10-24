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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
