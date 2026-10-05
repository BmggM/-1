package com.oai.ddaycalendar;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "schedule_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("eventId", -1);
        if (id < 0) return;
        Event found = null;
        for (Event e : new EventStore(context).load()) if (e.id == id) { found = e; break; }
        if (found == null) return;

        createChannel(context);
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            long day = Recurrence.nextOccurrenceDay(found, System.currentTimeMillis(), true);
            if (day < 0) day = found.dateMillis;
            String when = new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(new Date(day));
            Intent open = new Intent(context, MainActivity.class);
            open.putExtra("openEventId", found.id);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = PendingIntent.getActivity(context, (int) found.id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                        ? new android.app.Notification.Builder(context, CHANNEL_ID)
                        : new android.app.Notification.Builder(context);
                b.setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(found.title)
                        .setContentText(when + " 일정이 있어요")
                        .setAutoCancel(true)
                        .setContentIntent(content)
                        .setCategory(android.app.Notification.CATEGORY_REMINDER)
                        .setPriority(android.app.Notification.PRIORITY_HIGH);
                nm.notify((int) (found.id ^ (found.id >>> 32)), b.build());
            }
        }
        AlarmScheduler.schedule(context, found);
        DdayWidgetProvider.updateAll(context);
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "일정 알림", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("디데이 캘린더의 일정 알림");
                nm.createNotificationChannel(channel);
            }
        }
    }
}
