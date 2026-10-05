package com.oai.ddaycalendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AlarmScheduler.rescheduleAll(context);
        DdayWidgetProvider.updateAll(context);
        AnniversaryNotification.update(context);
        DailyStatusReceiver.scheduleNext(context);
    }
}
