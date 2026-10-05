package com.oai.ddaycalendar;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class Recurrence {
    private Recurrence() { }

    public static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static boolean occursOn(Event e, long dayMillis) {
        long day = startOfDay(dayMillis);
        long start = startOfDay(e.dateMillis);
        if (day < start) return false;
        if (Event.REPEAT_NONE.equals(e.repeatRule)) return day == start;

        Calendar s = Calendar.getInstance();
        Calendar d = Calendar.getInstance();
        s.setTimeInMillis(start);
        d.setTimeInMillis(day);
        switch (e.repeatRule) {
            case Event.REPEAT_DAILY:
                return true;
            case Event.REPEAT_WEEKLY:
                return s.get(Calendar.DAY_OF_WEEK) == d.get(Calendar.DAY_OF_WEEK);
            case Event.REPEAT_MONTHLY:
                return d.get(Calendar.DAY_OF_MONTH) == Math.min(s.get(Calendar.DAY_OF_MONTH), d.getActualMaximum(Calendar.DAY_OF_MONTH));
            case Event.REPEAT_YEARLY:
                if (s.get(Calendar.MONTH) != d.get(Calendar.MONTH)) return false;
                return d.get(Calendar.DAY_OF_MONTH) == Math.min(s.get(Calendar.DAY_OF_MONTH), d.getActualMaximum(Calendar.DAY_OF_MONTH));
            default:
                return day == start;
        }
    }

    public static long nextOccurrenceDay(Event e, long fromMillis, boolean includeToday) {
        long from = startOfDay(fromMillis);
        long start = startOfDay(e.dateMillis);
        if (Event.REPEAT_NONE.equals(e.repeatRule)) {
            if (start > from || (includeToday && start == from)) return start;
            return -1;
        }

        long cursor = Math.max(start, from);
        if (!includeToday && cursor == from) cursor = addDays(cursor, 1);
        // Bounded day scan keeps all edge cases predictable (month ends, leap years, DST).
        for (int i = 0; i < 3700; i++) {
            if (occursOn(e, cursor)) return cursor;
            cursor = addDays(cursor, 1);
        }
        return -1;
    }

    public static long occurrenceDateTime(Event e, long occurrenceDay) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(occurrenceDay));
        if (e.timeMinutes >= 0) {
            c.set(Calendar.HOUR_OF_DAY, e.timeMinutes / 60);
            c.set(Calendar.MINUTE, e.timeMinutes % 60);
        } else {
            c.set(Calendar.HOUR_OF_DAY, 9); // all-day reminders default to 09:00
            c.set(Calendar.MINUTE, 0);
        }
        return c.getTimeInMillis();
    }

    public static long nextReminderTime(Event e, long now) {
        if (e.reminderMinutes < 0) return -1;
        long day = nextOccurrenceDay(e, now, true);
        for (int i = 0; i < 370; i++) {
            if (day < 0) return -1;
            long trigger = occurrenceDateTime(e, day) - TimeUnit.MINUTES.toMillis(e.reminderMinutes);
            if (trigger > now + 1000) return trigger;
            day = nextOccurrenceDay(e, addDays(day, 1), true);
        }
        return -1;
    }

    public static long addDays(long day, int amount) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(day));
        c.add(Calendar.DAY_OF_MONTH, amount);
        return startOfDay(c.getTimeInMillis());
    }

    public static long daysBetween(long from, long to) {
        return TimeUnit.MILLISECONDS.toDays(startOfDay(to) - startOfDay(from));
    }
}
