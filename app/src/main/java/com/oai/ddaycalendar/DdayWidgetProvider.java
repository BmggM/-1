package com.oai.ddaycalendar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DdayWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, DdayWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(cn);
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    private static RemoteViews build(Context context) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_dday);
        long today = Recurrence.startOfDay(System.currentTimeMillis());
        Event best = pickBest(context, today);
        if (best == null) {
            rv.setTextViewText(R.id.widget_title, "등록된 일정이 없어요");
            rv.setTextViewText(R.id.widget_dday, "+ 일정 추가");
            rv.setTextViewText(R.id.widget_date, "앱을 눌러 일정을 추가하세요");
        } else if (best.isSinceCounter()) {
            rv.setTextViewText(R.id.widget_title, "♥ " + best.title);
            rv.setTextViewText(R.id.widget_dday, AnniversaryUtils.countText(best, today));
            rv.setTextViewText(R.id.widget_date, AnniversaryUtils.nextMilestoneText(best.dateMillis, today));
        } else {
            long bestDay = Recurrence.nextOccurrenceDay(best, today, true);
            if (bestDay < 0) bestDay = best.dateMillis;
            long diff = Recurrence.daysBetween(today, bestDay);
            String dday = diff == 0 ? "D-Day" : (diff > 0 ? "D-" + diff : "D+" + Math.abs(diff));
            rv.setTextViewText(R.id.widget_title, best.title);
            rv.setTextViewText(R.id.widget_dday, dday);
            rv.setTextViewText(R.id.widget_date, new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(new Date(bestDay)));
        }
        Intent open = new Intent(context, MainActivity.class);
        if (best != null) open.putExtra("openEventId", best.id);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_root, pi);
        return rv;
    }

    private static Event pickBest(Context context, long today) {
        List<Event> all = new EventStore(context).load();
        for (Event e : all) if (e.statusPinned && e.isSinceCounter()) return e;
        for (Event e : all) if (e.pinned && e.isSinceCounter()) return e;
        Event best = null;
        long bestDay = Long.MAX_VALUE;
        for (Event e : all) {
            if (e.isSinceCounter()) continue;
            long d = Recurrence.nextOccurrenceDay(e, today, true);
            if (d >= 0 && (best == null || (e.pinned && !best.pinned) || (e.pinned == best.pinned && d < bestDay))) {
                best = e;
                bestDay = d;
            }
        }
        return best;
    }
}
