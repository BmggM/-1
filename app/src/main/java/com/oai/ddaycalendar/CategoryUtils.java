package com.oai.ddaycalendar;

import android.content.Context;

public final class CategoryUtils {
    public static final String[] LABELS = {"일반", "기념일", "업무", "공부", "약속", "여행", "생일"};
    public static final String[] ICONS = {"●", "♥", "■", "✎", "◆", "✈", "★"};

    private CategoryUtils() { }

    public static int color(Context context, int category) {
        int[] ids = {
                R.color.cat_general, R.color.cat_anniversary, R.color.cat_work,
                R.color.cat_study, R.color.cat_meeting, R.color.cat_travel, R.color.cat_birthday
        };
        int i = Math.max(0, Math.min(category, ids.length - 1));
        return context.getColor(ids[i]);
    }

    public static String label(int category) {
        int i = Math.max(0, Math.min(category, LABELS.length - 1));
        return LABELS[i];
    }
}
