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

      Log.i(TAG, "Show a notification with type " + mapPayload.get("type"));
      if (mapPayload!=null && !mapPayload.isEmpty() && Objects.equals(mapPayload.get("type"), "disabled")) {
        // FlutterLocalNotificationsPlugin.cancelNotification(context, notificationDetails.id);
      }else{
        int prayerTime = mapPayload.get("prayerTimeInt") != null ? ((Integer) mapPayload.get("prayerTimeInt")).intValue() : -1;
        if(prayerTime != -1 && (System.currentTimeMillis()/1000L) - prayerTime > (60 * 15) ){
          Log.i(TAG, "Prayer time notification for time: " + prayerTime + " skipped as it is older than 15 minutes.");
        }else{
          FlutterLocalNotificationsPlugin.showNotification(context, notificationDetails);
        }
        
      }
      FlutterLocalNotificationsPlugin.scheduleNextNotification(context, notificationDetails);
    }

    Intent broadcastIntent = new Intent();
    broadcastIntent.setClassName(
        context.getPackageName(),
        "com.hudaring.app.OwnAlarmReceiver" // fully qualified class name
    );
    // broadcastIntent.setAction("com.hudaring.ALARM_TRIGGERED");

    // Forward extras
    broadcastIntent.putExtra("notification_id", intent.getIntExtra("notification_id", -1));
    broadcastIntent.putExtra(
        FlutterLocalNotificationsPlugin.NOTIFICATION_DETAILS,
        intent.getStringExtra(FlutterLocalNotificationsPlugin.NOTIFICATION_DETAILS)
    );

    // Send broadcast
    context.sendBroadcast(broadcastIntent);
  }
}
