package com.oai.ddaycalendar;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.List;

public final class AnniversaryNotification {
    public static final String CHANNEL_ID = "anniversary_status";
    public static final int NOTIFICATION_ID = 91001;

    private AnniversaryNotification() { }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "기념일 상시 표시", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("선택한 기념일이 오늘로 며칠째인지 알림창에 계속 표시합니다.");
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
    }

    public static Event pinnedEvent(Context context) {
        List<Event> events = new EventStore(context).load();
        for (Event e : events) if (e.statusPinned && e.isSinceCounter()) return e;
        return null;
    }

    public static void update(Context context) {
        createChannel(context);
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            DailyStatusReceiver.scheduleNext(context);
            return;
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Event e = pinnedEvent(context);
        if (e == null) {
            nm.cancel(NOTIFICATION_ID);
            DailyStatusReceiver.scheduleNext(context);
            return;
        }

        long today = Recurrence.startOfDay(System.currentTimeMillis());
        long start = Recurrence.startOfDay(e.dateMillis);
        long raw = Recurrence.daysBetween(start, today);
        String count;
        String sub;
        if (raw >= 0) {
            long nth = raw + 1; // 시작한 날을 1일째로 계산
            count = nth + "일째";
            sub = AnniversaryUtils.nextMilestoneText(start, today);
        } else {
            count = "시작까지 D-" + Math.abs(raw);
            sub = "시작일 " + AnniversaryUtils.shortDate(start);
        }

        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("openEventId", e.id);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, (int) e.id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("♥ " + e.title + " · " + count)
                .setContentText(sub)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(Notification.PRIORITY_LOW);
        nm.notify(NOTIFICATION_ID, b.build());
        DailyStatusReceiver.scheduleNext(context);
    }

    public static void cancel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }
}
