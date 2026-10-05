package com.oai.ddaycalendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

public final class AlarmScheduler {
    private AlarmScheduler() { }

    public static void rescheduleAll(Context context) {
        EventStore store = new EventStore(context);
        for (Event e : store.load()) schedule(context, e);
    }

    public static void schedule(Context context, Event e) {
        cancel(context, e.id);
        long trigger = Recurrence.nextReminderTime(e, System.currentTimeMillis());
        if (trigger < 0) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = reminderIntent(context, e.id, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    public static void scheduleAll(Context context, List<Event> events) {
        for (Event e : events) schedule(context, e);
    }

    public static void cancel(Context context, long eventId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = reminderIntent(context, eventId, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) am.cancel(pi);
    }

    private static PendingIntent reminderIntent(Context context, long eventId, int flags) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.oai.ddaycalendar.REMIND." + eventId);
        intent.putExtra("eventId", eventId);
        return PendingIntent.getBroadcast(context, (int) (eventId ^ (eventId >>> 32)), intent, flags);
    }
}
