package com.oai.ddaycalendar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class AnniversaryUtils {
    private AnniversaryUtils() { }

    public static long nthDay(long startDay, long today) {
        long raw = Recurrence.daysBetween(Recurrence.startOfDay(startDay), Recurrence.startOfDay(today));
        return raw >= 0 ? raw + 1 : raw;
    }

    public static String countText(Event e, long today) {
        if (!e.isSinceCounter()) return "";
        long n = nthDay(e.dateMillis, today);
        return n > 0 ? n + "일째" : "시작까지 D-" + Math.abs(n);
    }

    public static String nextMilestoneText(long startDay, long today) {
        long n = nthDay(startDay, today);
        if (n <= 0) return "시작일 " + shortDate(startDay);
        long[] milestones = {100, 200, 300, 365, 500, 600, 700, 730, 800, 900, 1000, 1500, 2000, 3000, 5000};
        for (long m : milestones) {
            if (m > n) return "다음 " + m + "일까지 " + (m - n) + "일";
        }
        long next = ((n / 1000) + 1) * 1000;
        return "다음 " + next + "일까지 " + (next - n) + "일";
    }

    public static long nextMilestoneDay(long startDay, long today) {
        long n = nthDay(startDay, today);
        long[] milestones = {100, 200, 300, 365, 500, 600, 700, 730, 800, 900, 1000, 1500, 2000, 3000, 5000};
        long target = -1;
        for (long m : milestones) if (m > n) { target = m; break; }
        if (target < 0) target = ((n / 1000) + 1) * 1000;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(Recurrence.startOfDay(startDay));
        c.add(Calendar.DAY_OF_YEAR, (int) target - 1);
        return c.getTimeInMillis();
    }

    public static String shortDate(long day) {
        return new SimpleDateFormat("yyyy.M.d", Locale.KOREAN).format(new Date(day));
    }

    public static String togetherSpan(long startDay, long today) {
        Calendar s = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        s.setTimeInMillis(Recurrence.startOfDay(startDay));
        t.setTimeInMillis(Recurrence.startOfDay(today));
        if (s.after(t)) return "아직 시작 전";

        int years = t.get(Calendar.YEAR) - s.get(Calendar.YEAR);
        int months = t.get(Calendar.MONTH) - s.get(Calendar.MONTH);
        int days = t.get(Calendar.DAY_OF_MONTH) - s.get(Calendar.DAY_OF_MONTH);
        if (days < 0) {
            months--;
            Calendar prev = (Calendar) t.clone();
            prev.add(Calendar.MONTH, -1);
            days += prev.getActualMaximum(Calendar.DAY_OF_MONTH);
        }
        if (months < 0) { years--; months += 12; }
        StringBuilder b = new StringBuilder();
        if (years > 0) b.append(years).append("년 ");
        if (months > 0 || years > 0) b.append(months).append("개월 ");
        b.append(days).append("일");
        return b.toString().trim();
    }
}
