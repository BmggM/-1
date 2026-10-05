package com.oai.ddaycalendar;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PAGE_HOME = 0;
    private static final int PAGE_CALENDAR = 1;
    private static final int PAGE_ALL = 2;
    private static final int PAGE_SETTINGS = 3;
    private static final int REQ_NOTIFICATION = 30;
    private static final int REQ_EXPORT = 31;
    private static final int REQ_IMPORT = 32;
    private static final int REQ_PHOTO = 33;

    private final List<Event> events = new ArrayList<>();
    private EventStore store;
    private FrameLayout content;
    private LinearLayout navBar;
    private int currentPage = PAGE_HOME;
    private long selectedDateMillis;
    private Calendar displayMonth;
    private String[] activePhotoValue;
    private Button activePhotoButton;
    private ImageView activePhotoPreview;

    private final SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN);
    private final SimpleDateFormat shortFmt = new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN);
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.KOREAN);

    private final String[] repeatLabels = {"반복 안 함", "매일", "매주", "매월", "매년"};
    private final String[] repeatValues = {Event.REPEAT_NONE, Event.REPEAT_DAILY, Event.REPEAT_WEEKLY, Event.REPEAT_MONTHLY, Event.REPEAT_YEARLY};
    private final String[] reminderLabels = {"알림 없음", "일정 시간에", "10분 전", "1시간 전", "하루 전", "일주일 전"};
    private final int[] reminderValues = {-1, 0, 10, 60, 1440, 10080};
    private final String[] countModeLabels = {"D-Day · 얼마나 남았는지", "며칠째 · 시작일부터 오늘까지"};
    private final String[] countModeValues = {Event.COUNT_DDAY, Event.COUNT_SINCE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new EventStore(this);
        events.addAll(store.load());
        selectedDateMillis = Recurrence.startOfDay(System.currentTimeMillis());
        displayMonth = Calendar.getInstance();
        displayMonth.setTimeInMillis(selectedDateMillis);
        displayMonth.set(Calendar.DAY_OF_MONTH, 1);

        ReminderReceiver.createChannel(this);
        AnniversaryNotification.createChannel(this);
        setContentView(buildShell());
        showPage(PAGE_HOME);
        requestNotificationPermissionIfNeeded(false);
        AnniversaryNotification.update(this);
        DailyStatusReceiver.scheduleNext(this);

        long openId = getIntent().getLongExtra("openEventId", -1);
        if (openId >= 0) {
            Event e = findEvent(openId);
            if (e != null) showEditDialog(e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmScheduler.scheduleAll(this, events);
        DdayWidgetProvider.updateAll(this);
        AnniversaryNotification.update(this);
        DailyStatusReceiver.scheduleNext(this);
        if (content != null && currentPage == PAGE_SETTINGS) showPage(PAGE_SETTINGS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        long openId = intent.getLongExtra("openEventId", -1);
        Event e = findEvent(openId);
        if (e != null) showEditDialog(e);
    }

    private View buildShell() {
        LinearLayout root = vertical();
        root.setBackgroundColor(getColor(R.color.bg));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(16), dp(14), dp(10));
        LinearLayout titles = vertical();
        titles.addView(text("디데이 캘린더", 24, true, R.color.text_primary));
        TextView sub = text("광고 없이 내 일정만", 12, false, R.color.text_secondary);
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView quick = pillText("＋", 24, true, R.color.accent);
        quick.setContentDescription("일정 추가");
        quick.setOnClickListener(v -> showAddDialog(selectedDateMillis));
        header.addView(quick, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(header);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        navBar = horizontal();
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(dp(8), dp(7), dp(8), dp(8));
        navBar.setBackgroundColor(getColor(R.color.surface));
        root.addView(navBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        rebuildNav();
        return root;
    }

    private void rebuildNav() {
        navBar.removeAllViews();
        String[] labels = {"홈", "캘린더", "전체", "설정"};
        String[] icons = {"⌂", "▦", "≡", "⚙"};
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            LinearLayout item = vertical();
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4), 0, dp(4), 0);
            int color = currentPage == i ? R.color.accent : R.color.text_secondary;
            TextView icon = text(icons[i], 18, true, color);
            icon.setGravity(Gravity.CENTER);
            TextView label = text(labels[i], 11, currentPage == i, color);
            label.setGravity(Gravity.CENTER);
            item.addView(icon);
            item.addView(label);
            item.setOnClickListener(v -> showPage(page));
            navBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private void showPage(int page) {
        currentPage = page;
        content.removeAllViews();
        View view;
        if (page == PAGE_CALENDAR) view = buildCalendarPage();
        else if (page == PAGE_ALL) view = buildAllPage();
        else if (page == PAGE_SETTINGS) view = buildSettingsPage();
        else view = buildHomePage();
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rebuildNav();
    }

    private View buildHomePage() {
        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(4), dp(16), dp(26));

        Event featured = featuredHomeEvent();
        if (featured == null) {
            LinearLayout emptyHero = card();
            TextView big = text("첫 기념일을 만들어보세요", 22, true, R.color.text_primary);
            emptyHero.addView(big);
            TextView small = text("D-Day뿐 아니라 ‘오늘로 며칠째’도 계산할 수 있어요.", 13, false, R.color.text_secondary);
            small.setPadding(0, dp(6), 0, dp(12));
            emptyHero.addView(small);
            Button add = actionButton("＋ 일정/기념일 추가");
            add.setOnClickListener(v -> showAddDialog(selectedDateMillis));
            emptyHero.addView(add);
            root.addView(emptyHero, matchWrapMargins(0, 0, 0, 14));
        } else {
            long day = displayDay(featured);
            LinearLayout hero = card();
            hero.setPadding(dp(18), dp(18), dp(18), dp(18));
            TextView category = text(CategoryUtils.ICONS[featured.category] + "  " + CategoryUtils.label(featured.category), 12, true, R.color.text_secondary);
            category.setTextColor(CategoryUtils.color(this, featured.category));
            hero.addView(category);
            TextView title = text(featured.title, 24, true, R.color.text_primary);
            title.setPadding(0, dp(8), 0, 0);
            hero.addView(title);
            TextView count = text(counterText(featured, day), 42, true, R.color.accent);
            count.setPadding(0, dp(8), 0, dp(3));
            hero.addView(count);
            if (featured.isSinceCounter()) {
                long today = Recurrence.startOfDay(System.currentTimeMillis());
                TextView span = text("함께한 시간 · " + AnniversaryUtils.togetherSpan(featured.dateMillis, today), 14, false, R.color.text_secondary);
                hero.addView(span);
                TextView milestone = text("🎉 " + AnniversaryUtils.nextMilestoneText(featured.dateMillis, today), 13, true, R.color.text_secondary);
                milestone.setPadding(0, dp(6), 0, 0);
                hero.addView(milestone);
            } else {
                hero.addView(text(formatDateTime(featured, day), 14, false, R.color.text_secondary));
            }
            if (!featured.photoUri.isEmpty()) {
                ImageView photo = new ImageView(this);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (loadPhotoInto(photo, featured.photoUri)) {
                    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
                    ip.setMargins(0, dp(12), 0, 0);
                    hero.addView(photo, ip);
                }
            }
            if (!featured.note.trim().isEmpty()) {
                TextView note = text(featured.note, 13, false, R.color.text_secondary);
                note.setPadding(0, dp(9), 0, 0);
                hero.addView(note);
            }
            hero.setOnClickListener(v -> showEditDialog(featured));
            root.addView(hero, matchWrapMargins(0, 0, 0, 14));
        }

        LinearLayout quickRow = horizontal();
        Button today = smallActionButton("오늘 일정 +");
        today.setOnClickListener(v -> showAddDialog(Recurrence.startOfDay(System.currentTimeMillis())));
        quickRow.addView(today, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button pick = smallActionButton("기념일/날짜 추가");
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        pp.setMargins(dp(8), 0, 0, 0);
        quickRow.addView(pick, pp);
        pick.setOnClickListener(v -> pickDate(selectedDateMillis, d -> showAddDialog(d)));
        root.addView(quickRow, matchWrapMargins(0, 0, 0, 16));

        List<Event> since = sinceEvents();
        if (!since.isEmpty()) {
            addSectionTitle(root, "며칠째 카운터", "연애·결혼·운동·프로젝트 시작일부터 오늘까지");
            LinearLayout c = card();
            for (int i = 0; i < Math.min(5, since.size()); i++) c.addView(buildEventRow(since.get(i), false));
            root.addView(c, matchWrapMargins(0, 0, 0, 16));
        }

        List<Event> pinned = new ArrayList<>();
        for (Event e : events) if (e.pinned && !e.isSinceCounter()) pinned.add(e);
        if (!pinned.isEmpty()) {
            addSectionTitle(root, "즐겨찾는 일정", "상단에 고정한 일정");
            sortForDisplay(pinned);
            LinearLayout c = card();
            int count = Math.min(5, pinned.size());
            for (int i = 0; i < count; i++) c.addView(buildEventRow(pinned.get(i), false));
            root.addView(c, matchWrapMargins(0, 0, 0, 16));
        }

        addSectionTitle(root, "다가오는 일정", "가까운 순서로 최대 7개");
        List<Event> future = upcomingEvents();
        LinearLayout list = card();
        if (future.isEmpty()) {
            TextView empty = text("앞으로 예정된 일정이 없어요.", 13, false, R.color.text_secondary);
            empty.setPadding(0, dp(8), 0, dp(8));
            list.addView(empty);
        } else {
            int count = Math.min(7, future.size());
            for (int i = 0; i < count; i++) list.addView(buildEventRow(future.get(i), true));
        }
        root.addView(list, matchWrapMargins(0, 0, 0, 16));

        if (!since.isEmpty()) root.addView(buildMemoryPromptCard(since.get(0)), matchWrapMargins(0, 0, 0, 16));

        long now = System.currentTimeMillis();
        Calendar m = Calendar.getInstance();
        m.setTimeInMillis(now);
        int monthCount = 0;
        for (Event e : events) {
            if (e.isSinceCounter()) continue;
            for (int d = 1; d <= m.getActualMaximum(Calendar.DAY_OF_MONTH); d++) {
                Calendar x = (Calendar) m.clone(); x.set(Calendar.DAY_OF_MONTH, d);
                if (Recurrence.occursOn(e, x.getTimeInMillis())) { monthCount++; break; }
            }
        }
        TextView stats = text("이번 달 일정 " + monthCount + "개 · 전체 " + events.size() + "개", 12, false, R.color.text_secondary);
        stats.setGravity(Gravity.CENTER);
        root.addView(stats);
        return scroll(root);
    }

    private View buildCalendarPage() {
        LinearLayout root = vertical();
        root.setPadding(dp(14), dp(4), dp(14), dp(28));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = miniButton("‹");
        prev.setOnClickListener(v -> { displayMonth.add(Calendar.MONTH, -1); showPage(PAGE_CALENDAR); });
        header.addView(prev, new LinearLayout.LayoutParams(dp(46), dp(44)));
        TextView month = text(String.format(Locale.KOREAN, "%d년 %d월", displayMonth.get(Calendar.YEAR), displayMonth.get(Calendar.MONTH) + 1), 20, true, R.color.text_primary);
        month.setGravity(Gravity.CENTER);
        header.addView(month, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button next = miniButton("›");
        next.setOnClickListener(v -> { displayMonth.add(Calendar.MONTH, 1); showPage(PAGE_CALENDAR); });
        header.addView(next, new LinearLayout.LayoutParams(dp(46), dp(44)));
        root.addView(header);

        Button today = miniButton("오늘로 이동");
        today.setOnClickListener(v -> {
            selectedDateMillis = Recurrence.startOfDay(System.currentTimeMillis());
            displayMonth = Calendar.getInstance(); displayMonth.setTimeInMillis(selectedDateMillis); displayMonth.set(Calendar.DAY_OF_MONTH, 1);
            showPage(PAGE_CALENDAR);
        });
        root.addView(today, matchWrapMargins(0, 4, 0, 10));

        LinearLayout calCard = card();
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        String[] weekdays = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < 7; i++) {
            TextView w = text(weekdays[i], 12, true, i == 0 ? R.color.danger : R.color.text_secondary);
            w.setGravity(Gravity.CENTER);
            grid.addView(w, gridParams());
        }

        Calendar first = (Calendar) displayMonth.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int cell = 0; cell < 42; cell++) {
            int dayNum = cell - offset + 1;
            if (dayNum < 1 || dayNum > days) {
                TextView blank = text("", 14, false, R.color.text_secondary);
                grid.addView(blank, gridParams());
                continue;
            }
            Calendar dayCal = (Calendar) first.clone();
            dayCal.set(Calendar.DAY_OF_MONTH, dayNum);
            long dayMillis = Recurrence.startOfDay(dayCal.getTimeInMillis());
            List<Event> onDay = eventsOnDay(dayMillis);
            String dots = eventDots(onDay);
            SpannableString span = new SpannableString(dayNum + (dots.isEmpty() ? "" : "\n" + dots));
            if (!dots.isEmpty()) {
                int start = String.valueOf(dayNum).length() + 1;
                int pos = start;
                int max = Math.min(3, onDay.size());
                for (int i = 0; i < max; i++) {
                    int len = 1;
                    span.setSpan(new ForegroundColorSpan(CategoryUtils.color(this, onDay.get(i).category)), pos, pos + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    pos += 2;
                }
            }
            TextView cellView = text("", 14, false, R.color.text_primary);
            cellView.setText(span);
            cellView.setGravity(Gravity.CENTER);
            cellView.setPadding(dp(2), dp(6), dp(2), dp(4));
            boolean selected = Recurrence.startOfDay(selectedDateMillis) == dayMillis;
            boolean isToday = Recurrence.startOfDay(System.currentTimeMillis()) == dayMillis;
            cellView.setBackground(dayBackground(selected, isToday));
            if (selected) cellView.setTextColor(Color.WHITE);
            cellView.setOnClickListener(v -> {
                selectedDateMillis = dayMillis;
                displayMonth.setTimeInMillis(dayMillis); displayMonth.set(Calendar.DAY_OF_MONTH, 1);
                showPage(PAGE_CALENDAR);
            });
            grid.addView(cellView, gridParams());
        }
        calCard.addView(grid);
        root.addView(calCard, matchWrapMargins(0, 0, 0, 14));

        LinearLayout dayCard = card();
        LinearLayout dayHead = horizontal();
        dayHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView dayTitle = text(dayFmt.format(new Date(selectedDateMillis)), 17, true, R.color.text_primary);
        dayHead.addView(dayTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView add = pillText("＋", 20, true, R.color.accent);
        add.setOnClickListener(v -> showAddDialog(selectedDateMillis));
        dayHead.addView(add, new LinearLayout.LayoutParams(dp(44), dp(40)));
        dayCard.addView(dayHead);
        List<Event> same = eventsOnDay(selectedDateMillis);
        if (same.isEmpty()) {
            TextView none = text("등록된 일정이 없습니다. + 버튼으로 추가해보세요.", 13, false, R.color.text_secondary);
            none.setPadding(0, dp(12), 0, dp(7));
            dayCard.addView(none);
        } else {
            same.sort(Comparator.comparingInt(a -> a.timeMinutes < 0 ? -1 : a.timeMinutes));
            for (Event e : same) dayCard.addView(buildEventRowForSpecificDay(e, selectedDateMillis));
        }
        root.addView(dayCard);
        return scroll(root);
    }

    private View buildAllPage() {
        LinearLayout root = vertical();
        root.setPadding(dp(14), dp(4), dp(14), dp(24));
        TextView title = text("전체 일정", 22, true, R.color.text_primary);
        root.addView(title);
        TextView sub = text("제목·메모 검색과 카테고리 필터", 12, false, R.color.text_secondary);
        sub.setPadding(0, dp(2), 0, dp(10));
        root.addView(sub);

        EditText search = new EditText(this);
        search.setHint("일정 검색");
        search.setSingleLine(true);
        search.setBackgroundResource(R.drawable.bg_input);
        search.setPadding(dp(14), 0, dp(14), 0);
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        String[] filters = new String[CategoryUtils.LABELS.length + 2];
        filters[0] = "전체 카테고리";
        filters[1] = "★ 즐겨찾기만";
        for (int i = 0; i < CategoryUtils.LABELS.length; i++) filters[i + 2] = CategoryUtils.LABELS[i];
        Spinner filter = spinner(filters);
        root.addView(filter, matchWrapMargins(0, 8, 0, 8));
        CheckBox hidePast = new CheckBox(this);
        hidePast.setText("지난 일회성 일정 숨기기");
        hidePast.setTextColor(getColor(R.color.text_secondary));
        root.addView(hidePast);

        LinearLayout results = vertical();
        root.addView(results, matchWrapMargins(0, 8, 0, 0));

        Runnable refresh = () -> rebuildSearchResults(results, search.getText().toString(), filter.getSelectedItemPosition(), hidePast.isChecked());
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            public void onTextChanged(CharSequence s, int st, int b, int c) { refresh.run(); }
            public void afterTextChanged(Editable s) { }
        });
        filter.setOnItemSelectedListener(new SimpleItemSelectedListener(refresh));
        hidePast.setOnCheckedChangeListener((b, checked) -> refresh.run());
        refresh.run();
        return scroll(root);
    }

    private void rebuildSearchResults(LinearLayout results, String query, int filterPos, boolean hidePast) {
        results.removeAllViews();
        String q = query.trim().toLowerCase(Locale.KOREAN);
        long today = Recurrence.startOfDay(System.currentTimeMillis());
        List<Event> filtered = new ArrayList<>();
        for (Event e : events) {
            if (!q.isEmpty()) {
                String hay = (e.title + " " + e.note + " " + CategoryUtils.label(e.category)).toLowerCase(Locale.KOREAN);
                if (!hay.contains(q)) continue;
            }
            if (filterPos == 1 && !e.pinned) continue;
            if (filterPos >= 2 && e.category != filterPos - 2) continue;
            if (hidePast && Event.REPEAT_NONE.equals(e.repeatRule) && Recurrence.startOfDay(e.dateMillis) < today) continue;
            filtered.add(e);
        }
        sortForDisplay(filtered);
        if (filtered.isEmpty()) {
            LinearLayout c = card();
            TextView none = text("조건에 맞는 일정이 없습니다.", 13, false, R.color.text_secondary);
            none.setPadding(0, dp(8), 0, dp(8));
            c.addView(none);
            results.addView(c);
            return;
        }
        LinearLayout c = card();
        for (Event e : filtered) c.addView(buildEventRow(e, false));
        results.addView(c);
    }

    private View buildSettingsPage() {
        LinearLayout root = vertical();
        root.setPadding(dp(14), dp(4), dp(14), dp(28));
        root.addView(text("설정", 22, true, R.color.text_primary));

        addSectionTitle(root, "알림", "일정을 놓치지 않도록 설정");
        LinearLayout alarm = card();
        TextView status = text(notificationStatus(), 13, false, R.color.text_secondary);
        alarm.addView(status);
        Button notif = smallActionButton("알림 권한 확인/허용");
        notif.setOnClickListener(v -> requestNotificationPermissionIfNeeded(true));
        alarm.addView(notif, matchWrapMargins(0, 10, 0, 6));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Button exact = smallActionButton("정확한 알림 허용");
            exact.setOnClickListener(v -> requestExactAlarmPermission());
            alarm.addView(exact, matchWrapMargins(0, 4, 0, 6));
        }
        TextView defaultTitle = text("새 일정 기본 알림", 13, true, R.color.text_primary);
        defaultTitle.setPadding(0, dp(10), 0, dp(4));
        alarm.addView(defaultTitle);
        Spinner defaultReminder = spinner(reminderLabels);
        defaultReminder.setSelection(indexOf(reminderValues, store.getDefaultReminder()));
        defaultReminder.setOnItemSelectedListener(new SimpleItemSelectedListener(() -> store.setDefaultReminder(reminderValues[defaultReminder.getSelectedItemPosition()])));
        alarm.addView(defaultReminder);
        root.addView(alarm, matchWrapMargins(0, 0, 0, 16));

        addSectionTitle(root, "상단바 며칠째 표시", "선택한 기념일을 알림창에 항상 고정");
        LinearLayout statusCard = card();
        Event statusEvent = null;
        for (Event e : events) if (e.statusPinned && e.isSinceCounter()) { statusEvent = e; break; }
        if (statusEvent == null) statusCard.addView(text("아직 선택된 기념일이 없어요. ‘며칠째’ 기념일 수정 화면에서 ‘알림창에 항상 표시’를 켜세요.", 13, false, R.color.text_secondary));
        else statusCard.addView(text("현재 표시: " + statusEvent.title + " · " + AnniversaryUtils.countText(statusEvent, System.currentTimeMillis()), 13, true, R.color.text_primary));
        root.addView(statusCard, matchWrapMargins(0, 0, 0, 16));

        addSectionTitle(root, "화면", "시스템/라이트/다크 모드");
        LinearLayout appearance = card();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LinearLayout row = horizontal();
            Button sys = miniWideButton("시스템");
            Button light = miniWideButton("라이트");
            Button dark = miniWideButton("다크");
            sys.setOnClickListener(v -> setNightMode(UiModeManager.MODE_NIGHT_AUTO));
            light.setOnClickListener(v -> setNightMode(UiModeManager.MODE_NIGHT_NO));
            dark.setOnClickListener(v -> setNightMode(UiModeManager.MODE_NIGHT_YES));
            row.addView(sys, new LinearLayout.LayoutParams(0, dp(46), 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f); lp.setMargins(dp(6),0,0,0);
            row.addView(light, lp);
            LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(0, dp(46), 1f); dp2.setMargins(dp(6),0,0,0);
            row.addView(dark, dp2);
            appearance.addView(row);
        } else {
            appearance.addView(text("Android 12 이상에서는 앱별 다크 모드를 바로 바꿀 수 있습니다. 현재 기기는 시스템 테마를 따릅니다.", 13, false, R.color.text_secondary));
        }
        root.addView(appearance, matchWrapMargins(0, 0, 0, 16));

        addSectionTitle(root, "백업과 복원", "일정은 기본적으로 이 기기에만 저장됩니다");
        LinearLayout backup = card();
        Button export = smallActionButton("백업 파일 내보내기 (.json)");
        export.setOnClickListener(v -> startExport());
        backup.addView(export);
        Button imp = smallActionButton("백업 파일에서 복원");
        imp.setOnClickListener(v -> startImport());
        backup.addView(imp, matchWrapMargins(0, 8, 0, 0));
        root.addView(backup, matchWrapMargins(0, 0, 0, 16));

        addSectionTitle(root, "홈 화면 위젯", "앱을 열지 않고 다음 디데이 확인");
        LinearLayout widget = card();
        widget.addView(text("홈 화면을 길게 누른 뒤 ‘위젯’ → ‘디데이 캘린더’를 선택하세요. 즐겨찾기 일정이 있으면 우선 표시됩니다.", 13, false, R.color.text_secondary));
        root.addView(widget, matchWrapMargins(0, 0, 0, 16));

        addSectionTitle(root, "데이터", "광고 SDK·회원가입·네트워크 권한 없음");
        LinearLayout data = card();
        TextView privacy = text("모든 일정은 휴대폰 내부에 저장됩니다. 백업 파일을 직접 내보내지 않는 한 외부 서버로 전송하지 않습니다.", 13, false, R.color.text_secondary);
        data.addView(privacy);
        Button clear = smallActionButton("모든 일정 삭제");
        clear.setTextColor(getColor(R.color.danger));
        clear.setOnClickListener(v -> confirmClearAll());
        data.addView(clear, matchWrapMargins(0, 10, 0, 0));
        root.addView(data);

        TextView version = text("디데이 캘린더 3.1 · 며칠째 + 사진 + 상단바", 11, false, R.color.text_secondary);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(18), 0, 0);
        root.addView(version);
        return scroll(root);
    }

    private void showAddDialog(long initialDate) {
        Event draft = new Event(System.currentTimeMillis(), "", Recurrence.startOfDay(initialDate));
        draft.reminderMinutes = store.getDefaultReminder();
        showEventDialog(draft, true);
    }

    private void showEditDialog(Event event) {
        showEventDialog(event, false);
    }

    private void showEventDialog(Event event, boolean isNew) {
        final long[] editDate = {event.dateMillis};
        final int[] editTime = {event.timeMinutes};
        final String[] editPhoto = {event.photoUri == null ? "" : event.photoUri};

        LinearLayout box = vertical();
        box.setPadding(dp(20), dp(4), dp(20), dp(8));
        EditText title = new EditText(this);
        title.setHint("일정/기념일 이름");
        title.setText(event.title);
        title.setSingleLine(true);
        box.addView(title);

        LinearLayout dateRow = horizontal();
        TextView dateBtn = dialogField(dayFmt.format(new Date(editDate[0])));
        dateBtn.setOnClickListener(v -> pickDate(editDate[0], millis -> {
            editDate[0] = millis;
            dateBtn.setText(dayFmt.format(new Date(millis)));
        }));
        dateRow.addView(dateBtn, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView timeBtn = dialogField(editTime[0] < 0 ? "종일" : minutesToTime(editTime[0]));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(92), dp(48)); tlp.setMargins(dp(8),0,0,0);
        dateRow.addView(timeBtn, tlp);
        timeBtn.setOnClickListener(v -> {
            if (editTime[0] < 0) editTime[0] = 9 * 60;
            int h = editTime[0] / 60, m = editTime[0] % 60;
            new TimePickerDialog(this, (view, hour, minute) -> {
                editTime[0] = hour * 60 + minute;
                timeBtn.setText(minutesToTime(editTime[0]));
            }, h, m, true).show();
        });
        timeBtn.setOnLongClickListener(v -> { editTime[0] = -1; timeBtn.setText("종일"); Toast.makeText(this, "종일 일정으로 변경했습니다.", Toast.LENGTH_SHORT).show(); return true; });
        box.addView(dateRow);
        TextView timeTip = text("시간 버튼을 길게 누르면 ‘종일’로 돌아갑니다.", 11, false, R.color.text_secondary);
        timeTip.setPadding(0, dp(3), 0, dp(7));
        box.addView(timeTip);

        box.addView(fieldLabel("계산 방식"));
        Spinner countMode = spinner(countModeLabels);
        countMode.setSelection(indexOf(countModeValues, event.countMode));
        box.addView(countMode);
        TextView countTip = text("‘며칠째’는 시작한 날을 1일째로 계산합니다. 예: 사귄 날 = 1일째", 11, false, R.color.text_secondary);
        countTip.setPadding(0, dp(2), 0, dp(4));
        box.addView(countTip);

        box.addView(fieldLabel("카테고리"));
        Spinner category = spinner(CategoryUtils.LABELS);
        category.setSelection(Math.max(0, Math.min(event.category, CategoryUtils.LABELS.length - 1)));
        box.addView(category);

        box.addView(fieldLabel("반복"));
        Spinner repeat = spinner(repeatLabels);
        repeat.setSelection(indexOf(repeatValues, event.repeatRule));
        box.addView(repeat);

        box.addView(fieldLabel("알림"));
        Spinner reminder = spinner(reminderLabels);
        reminder.setSelection(indexOf(reminderValues, event.reminderMinutes));
        box.addView(reminder);

        CheckBox pin = new CheckBox(this);
        pin.setText("★ 즐겨찾기에 고정");
        pin.setTextColor(getColor(R.color.text_primary));
        pin.setChecked(event.pinned);
        box.addView(pin);

        CheckBox statusPin = new CheckBox(this);
        statusPin.setText("♥ 알림창에 ‘오늘로 몇 일째’ 항상 표시");
        statusPin.setTextColor(getColor(R.color.text_primary));
        statusPin.setChecked(event.statusPinned);
        box.addView(statusPin);
        TextView statusTip = text("‘며칠째’ 기념일 하나를 선택하면 상단바를 내려 항상 확인할 수 있어요.", 11, false, R.color.text_secondary);
        statusTip.setPadding(dp(4), 0, 0, dp(6));
        box.addView(statusTip);

        box.addView(fieldLabel("사진"));
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setVisibility(View.GONE);
        if (!editPhoto[0].isEmpty() && loadPhotoInto(preview, editPhoto[0])) preview.setVisibility(View.VISIBLE);
        box.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        Button photo = smallActionButton(editPhoto[0].isEmpty() ? "사진 첨부" : "사진 변경 / 제거");
        box.addView(photo, matchWrapMargins(0, 6, 0, 2));
        photo.setOnClickListener(v -> {
            if (!editPhoto[0].isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("사진")
                        .setItems(new String[]{"다른 사진 선택", "사진 제거"}, (d, which) -> {
                            if (which == 0) launchPhotoPicker(editPhoto, photo, preview);
                            else {
                                editPhoto[0] = "";
                                preview.setImageDrawable(null);
                                preview.setVisibility(View.GONE);
                                photo.setText("사진 첨부");
                            }
                        }).show();
            } else launchPhotoPicker(editPhoto, photo, preview);
        });

        box.addView(fieldLabel("메모"));
        EditText note = new EditText(this);
        note.setHint("장소, 준비물, 추억, 하고 싶은 말 등");
        note.setText(event.note);
        note.setMinLines(2);
        note.setMaxLines(4);
        box.addView(note);

        if (!isNew) {
            LinearLayout utility = horizontal();
            Button share = miniWideButton("공유");
            share.setOnClickListener(v -> shareEvent(event));
            utility.addView(share, new LinearLayout.LayoutParams(0, dp(44), 1f));
            Button deviceCal = miniWideButton("기기 캘린더에 복사");
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, dp(44), 1.5f); ulp.setMargins(dp(6),0,0,0);
            utility.addView(deviceCal, ulp);
            deviceCal.setOnClickListener(v -> copyToDeviceCalendar(event));
            Button duplicate = miniWideButton("복제");
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(44), .8f); dlp.setMargins(dp(6),0,0,0);
            utility.addView(duplicate, dlp);
            box.addView(utility, matchWrapMargins(0, 8, 0, 0));
            duplicate.setOnClickListener(v -> {
                Event copy = event.copyWithNewId(System.currentTimeMillis());
                events.add(copy);
                persistAndRefresh();
                Toast.makeText(this, "일정을 복제했습니다.", Toast.LENGTH_SHORT).show();
            });
        }

        ScrollView wrapper = new ScrollView(this);
        wrapper.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isNew ? "새 일정/기념일" : "일정 수정")
                .setView(wrapper)
                .setNegativeButton("취소", null)
                .setNeutralButton(isNew ? null : "삭제", null)
                .setPositiveButton("저장", null)
                .create();

        dialog.setOnDismissListener(d -> {
            activePhotoValue = null;
            activePhotoButton = null;
            activePhotoPreview = null;
        });
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = title.getText().toString().trim();
                if (name.isEmpty()) { title.setError("이름을 입력해주세요."); return; }
                String newMode = countModeValues[countMode.getSelectedItemPosition()];
                if (Event.COUNT_SINCE.equals(newMode) && statusPin.isChecked()) {
                    for (Event other : events) if (other != event) other.statusPinned = false;
                }
                if (isNew) events.add(event);
                event.title = name;
                event.dateMillis = Recurrence.startOfDay(editDate[0]);
                event.timeMinutes = editTime[0];
                event.note = note.getText().toString().trim();
                event.category = category.getSelectedItemPosition();
                event.countMode = newMode;
                event.repeatRule = Event.COUNT_SINCE.equals(newMode) ? Event.REPEAT_NONE : repeatValues[repeat.getSelectedItemPosition()];
                event.reminderMinutes = reminderValues[reminder.getSelectedItemPosition()];
                event.pinned = pin.isChecked();
                event.statusPinned = Event.COUNT_SINCE.equals(newMode) && statusPin.isChecked();
                event.photoUri = editPhoto[0];
                selectedDateMillis = event.dateMillis;
                displayMonth.setTimeInMillis(selectedDateMillis); displayMonth.set(Calendar.DAY_OF_MONTH, 1);
                persistAndRefresh();
                dialog.dismiss();
                if (event.reminderMinutes >= 0 || event.statusPinned) requestNotificationPermissionIfNeeded(true);
            });
            if (!isNew) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getColor(R.color.danger));
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("일정을 삭제할까요?")
                        .setMessage(event.title)
                        .setNegativeButton("취소", null)
                        .setPositiveButton("삭제", (x, which) -> {
                            AlarmScheduler.cancel(this, event.id);
                            events.remove(event);
                            persistAndRefresh();
                            dialog.dismiss();
                        }).show());
            }
        });
        dialog.show();
    }

    private View buildEventRow(Event e, boolean upcomingOnly) {
        long day = displayDay(e);
        if (upcomingOnly && day < 0) return new View(this);
        return buildEventRowForSpecificDay(e, day < 0 ? e.dateMillis : day);
    }

    private View buildEventRowForSpecificDay(Event e, long day) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView dot = text(CategoryUtils.ICONS[e.category], 16, true, R.color.text_secondary);
        dot.setTextColor(CategoryUtils.color(this, e.category));
        dot.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        row.addView(dot, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout center = vertical();
        String prefix = e.pinned ? "★ " : "";
        TextView name = text(prefix + e.title, 15, true, R.color.text_primary);
        center.addView(name);
        String extra = e.isSinceCounter() ? ("시작일 " + shortFmt.format(new Date(e.dateMillis))) : formatDateTime(e, day);
        if (!e.isSinceCounter() && !Event.REPEAT_NONE.equals(e.repeatRule)) extra += " · " + repeatLabel(e.repeatRule);
        if (!e.photoUri.isEmpty()) extra += " · 📷";
        if (e.statusPinned) extra += " · 상단바 표시";
        TextView meta = text(extra, 12, false, R.color.text_secondary);
        meta.setPadding(0, dp(2), 0, 0);
        center.addView(meta);
        row.addView(center, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView dday = text(counterText(e, day), 13, true, R.color.accent);
        dday.setGravity(Gravity.CENTER);
        dday.setBackgroundResource(R.drawable.bg_pill);
        dday.setPadding(dp(10), dp(5), dp(10), dp(5));
        row.addView(dday);
        row.setOnClickListener(v -> showEditDialog(e));
        return row;
    }

    private void persistAndRefresh() {
        store.save(events);
        AlarmScheduler.scheduleAll(this, events);
        DdayWidgetProvider.updateAll(this);
        AnniversaryNotification.update(this);
        DailyStatusReceiver.scheduleNext(this);
        showPage(currentPage);
    }

    private Event nearestUpcoming() {
        List<Event> up = upcomingEvents();
        return up.isEmpty() ? null : up.get(0);
    }

    private Event featuredHomeEvent() {
        for (Event e : events) if (e.statusPinned && e.isSinceCounter()) return e;
        List<Event> since = sinceEvents();
        if (!since.isEmpty()) return since.get(0);
        return nearestUpcoming();
    }

    private List<Event> sinceEvents() {
        List<Event> result = new ArrayList<>();
        for (Event e : events) if (e.isSinceCounter()) result.add(e);
        Collections.sort(result, (a, b) -> {
            if (a.statusPinned != b.statusPinned) return a.statusPinned ? -1 : 1;
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            return Long.compare(b.dateMillis, a.dateMillis);
        });
        return result;
    }

    private String counterText(Event e, long day) {
        if (e.isSinceCounter()) return AnniversaryUtils.countText(e, Recurrence.startOfDay(System.currentTimeMillis()));
        return ddayText(day);
    }

    private View buildMemoryPromptCard(Event e) {
        String[] prompts = {
                "처음 서로에게 호감이 생긴 순간은 언제였어?",
                "둘이 함께 먹었던 음식 중 다시 먹고 싶은 건?",
                "상대방에게 가장 고마웠던 순간 하나를 떠올려봐.",
                "지금 당장 둘이 하루 휴가를 간다면 어디로 갈까?",
                "처음 만났을 때와 지금, 서로 가장 달라진 점은?",
                "둘만 아는 웃긴 사건 하나를 다시 이야기해봐.",
                "다음 100일 동안 같이 해보고 싶은 일은?",
                "상대방의 의외로 귀여운 습관은?",
                "같이 찍은 사진 중 가장 좋아하는 한 장은?",
                "우리에게 제목을 붙인다면 어떤 영화 제목이 어울릴까?",
                "서로에게 듣고 싶은 말 한마디는?",
                "다음 기념일에 꼭 남기고 싶은 추억은?"
        };
        Calendar c = Calendar.getInstance();
        int idx = Math.abs(c.get(Calendar.DAY_OF_YEAR) + c.get(Calendar.YEAR) + e.title.hashCode()) % prompts.length;
        LinearLayout card = card();
        card.addView(text("💬 오늘의 추억 질문", 16, true, R.color.text_primary));
        TextView q = text(prompts[idx], 14, false, R.color.text_secondary);
        q.setPadding(0, dp(7), 0, dp(4));
        card.addView(q);
        TextView next = text("내일은 다른 질문이 나와요 · " + AnniversaryUtils.nextMilestoneText(e.dateMillis, System.currentTimeMillis()), 11, false, R.color.text_secondary);
        card.addView(next);
        return card;
    }

    private boolean loadPhotoInto(ImageView view, String uriText) {
        if (uriText == null || uriText.isEmpty()) return false;
        try {
            view.setImageURI(Uri.parse(uriText));
            return view.getDrawable() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private void launchPhotoPicker(String[] target, Button button, ImageView preview) {
        activePhotoValue = target;
        activePhotoButton = button;
        activePhotoPreview = preview;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_PHOTO);
    }

    private List<Event> upcomingEvents() {
        List<Event> result = new ArrayList<>();
        long today = Recurrence.startOfDay(System.currentTimeMillis());
        for (Event e : events) if (!e.isSinceCounter() && Recurrence.nextOccurrenceDay(e, today, true) >= 0) result.add(e);
        result.sort(Comparator.comparingLong(this::displayDay));
        return result;
    }

    private void sortForDisplay(List<Event> list) {
        long today = Recurrence.startOfDay(System.currentTimeMillis());
        Collections.sort(list, (a, b) -> {
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            long ad = Recurrence.nextOccurrenceDay(a, today, true);
            long bd = Recurrence.nextOccurrenceDay(b, today, true);
            boolean af = ad >= 0, bf = bd >= 0;
            if (af != bf) return af ? -1 : 1;
            if (af) return Long.compare(ad, bd);
            return Long.compare(b.dateMillis, a.dateMillis);
        });
    }

    private long displayDay(Event e) {
        if (e.isSinceCounter()) return e.dateMillis;
        long today = Recurrence.startOfDay(System.currentTimeMillis());
        long next = Recurrence.nextOccurrenceDay(e, today, true);
        if (next >= 0) return next;
        return e.dateMillis;
    }

    private List<Event> eventsOnDay(long day) {
        List<Event> result = new ArrayList<>();
        for (Event e : events) if (Recurrence.occursOn(e, day)) result.add(e);
        return result;
    }

    private String eventDots(List<Event> onDay) {
        if (onDay.isEmpty()) return "";
        int max = Math.min(3, onDay.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) { if (i > 0) sb.append(" "); sb.append("●"); }
        if (onDay.size() > 3) sb.append(" +");
        return sb.toString();
    }

    private String ddayText(long day) {
        long diff = Recurrence.daysBetween(System.currentTimeMillis(), day);
        if (diff == 0) return "D-Day";
        return diff > 0 ? "D-" + diff : "D+" + Math.abs(diff);
    }

    private String formatDateTime(Event e, long day) {
        String date = shortFmt.format(new Date(day));
        if (e.timeMinutes >= 0) date += " " + minutesToTime(e.timeMinutes);
        else date += " · 종일";
        return date;
    }

    private String minutesToTime(int minutes) {
        return String.format(Locale.KOREAN, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private String repeatLabel(String value) {
        int i = indexOf(repeatValues, value);
        return repeatLabels[i];
    }

    private Event findEvent(long id) {
        for (Event e : events) if (e.id == id) return e;
        return null;
    }

    private void pickDate(long current, DatePicked callback) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(current);
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar p = Calendar.getInstance(); p.set(year, month, dayOfMonth, 0, 0, 0); p.set(Calendar.MILLISECOND, 0);
            callback.onPicked(p.getTimeInMillis());
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void shareEvent(Event e) {
        long day = displayDay(e);
        String body;
        if (e.isSinceCounter()) {
            long today = Recurrence.startOfDay(System.currentTimeMillis());
            body = "♥ " + e.title + "\n오늘로 " + AnniversaryUtils.countText(e, today) + "\n" + AnniversaryUtils.nextMilestoneText(e.dateMillis, today);
        } else {
            body = e.title + "\n" + formatDateTime(e, day) + "\n" + ddayText(day);
        }
        if (!e.note.isEmpty()) body += "\n" + e.note;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, body);
        startActivity(Intent.createChooser(send, "일정 공유"));
    }

    private void copyToDeviceCalendar(Event e) {
        long day = displayDay(e);
        long begin = Recurrence.occurrenceDateTime(e, day);
        Intent intent = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, e.title);
        intent.putExtra(CalendarContract.Events.DESCRIPTION, e.note);
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
        if (e.timeMinutes < 0) intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true);
        else intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000L);
        try { startActivity(intent); }
        catch (Exception ex) { Toast.makeText(this, "기기 캘린더 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show(); }
    }

    private void requestNotificationPermissionIfNeeded(boolean userInitiated) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (userInitiated) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        } else if (userInitiated) {
            Toast.makeText(this, "알림 권한이 이미 허용되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am != null && am.canScheduleExactAlarms()) {
            Toast.makeText(this, "정확한 알림이 이미 허용되어 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "설정에서 ‘알람 및 리마인더’를 허용해주세요.", Toast.LENGTH_LONG).show();
        }
    }

    private String notificationStatus() {
        boolean notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            exact = am != null && am.canScheduleExactAlarms();
        }
        return "알림: " + (notif ? "허용됨" : "권한 필요") + " · 정확한 시간: " + (exact ? "허용됨" : "미허용(근사 시간으로 동작)");
    }

    private void setNightMode(int mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager um = (UiModeManager) getSystemService(UI_MODE_SERVICE);
            if (um != null) um.setApplicationNightMode(mode);
        }
    }

    private void startExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "DdayCalendar_backup_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("open failed");
                out.write(store.exportBackup(events).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "백업 파일을 저장했습니다.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "백업 저장에 실패했습니다.", Toast.LENGTH_LONG).show(); }
        } else if (requestCode == REQ_PHOTO) {
            if (activePhotoValue == null) return;
            try {
                int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) { }
            activePhotoValue[0] = uri.toString();
            if (activePhotoPreview != null) {
                activePhotoPreview.setVisibility(View.VISIBLE);
                loadPhotoInto(activePhotoPreview, uri.toString());
            }
            if (activePhotoButton != null) activePhotoButton.setText("사진 변경 / 제거");
        } else if (requestCode == REQ_IMPORT) {
            try {
                String raw = readText(uri);
                List<Event> imported = store.importBackup(raw);
                new AlertDialog.Builder(this)
                        .setTitle("백업을 복원할까요?")
                        .setMessage("현재 일정 " + events.size() + "개를 백업의 일정 " + imported.size() + "개로 교체합니다.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("복원", (d, w) -> {
                            for (Event old : events) AlarmScheduler.cancel(this, old.id);
                            events.clear(); events.addAll(imported);
                            persistAndRefresh();
                            Toast.makeText(this, "복원이 완료되었습니다.", Toast.LENGTH_SHORT).show();
                        }).show();
            } catch (Exception e) { Toast.makeText(this, "올바른 디데이 캘린더 백업 파일이 아닙니다.", Toast.LENGTH_LONG).show(); }
        }
    }

    private String readText(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("모든 일정을 삭제할까요?")
                .setMessage("이 작업은 되돌릴 수 없습니다. 필요하면 먼저 백업 파일을 만들어주세요.")
                .setNegativeButton("취소", null)
                .setPositiveButton("모두 삭제", (d, w) -> {
                    for (Event e : events) AlarmScheduler.cancel(this, e.id);
                    events.clear(); persistAndRefresh();
                }).show();
    }

    private ScrollView scroll(View child) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.addView(child, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout horizontal() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }

    private LinearLayout card() {
        LinearLayout v = vertical();
        v.setBackgroundResource(R.drawable.bg_card);
        v.setElevation(dp(1));
        v.setPadding(dp(16), dp(14), dp(16), dp(14));
        return v;
    }

    private TextView text(String value, int sp, boolean bold, int colorRes) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(getColor(colorRes));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView pillText(String value, int sp, boolean bold, int colorRes) {
        TextView t = text(value, sp, bold, colorRes); t.setGravity(Gravity.CENTER); t.setBackgroundResource(R.drawable.bg_pill); return t;
    }

    private TextView dialogField(String value) {
        TextView t = text(value, 14, true, R.color.text_primary); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(12),0,dp(12),0); t.setBackgroundResource(R.drawable.bg_input); return t;
    }

    private TextView fieldLabel(String value) {
        TextView t = text(value, 12, true, R.color.text_secondary); t.setPadding(0, dp(10), 0, dp(2)); return t;
    }

    private Button actionButton(String value) {
        Button b = new Button(this); b.setText(value); b.setTextSize(15); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(Color.WHITE); b.setBackgroundResource(R.drawable.bg_button); return b;
    }

    private Button smallActionButton(String value) {
        Button b = new Button(this); b.setText(value); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(getColor(R.color.text_primary)); b.setBackgroundResource(R.drawable.bg_secondary_button); return b;
    }

    private Button miniButton(String value) {
        Button b = smallActionButton(value); b.setTextSize(14); b.setPadding(dp(8),0,dp(8),0); return b;
    }

    private Button miniWideButton(String value) { Button b = miniButton(value); b.setTextSize(12); return b; }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) ((TextView) v).setTextColor(getColor(R.color.text_primary));
                return v;
            }
        };
        s.setAdapter(adapter); return s;
    }

    private void addSectionTitle(LinearLayout root, String title, String subtitle) {
        TextView t = text(title, 17, true, R.color.text_primary); root.addView(t);
        TextView s = text(subtitle, 11, false, R.color.text_secondary); s.setPadding(0, dp(2), 0, dp(8)); root.addView(s);
    }

    private LinearLayout.LayoutParams matchWrapMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p;
    }

    private GridLayout.LayoutParams gridParams() {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams(); p.width = 0; p.height = dp(58); p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); p.setMargins(dp(1),dp(1),dp(1),dp(1)); return p;
    }

    private GradientDrawable dayBackground(boolean selected, boolean today) {
        GradientDrawable g = new GradientDrawable(); g.setCornerRadius(dp(12));
        if (selected) g.setColor(getColor(R.color.accent)); else g.setColor(Color.TRANSPARENT);
        if (today && !selected) g.setStroke(dp(1), getColor(R.color.accent));
        return g;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private int indexOf(int[] values, int target) { for (int i=0;i<values.length;i++) if(values[i]==target) return i; return 0; }
    private int indexOf(String[] values, String target) { for (int i=0;i<values.length;i++) if(values[i].equals(target)) return i; return 0; }

    private interface DatePicked { void onPicked(long millis); }

    private static class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable action;
        SimpleItemSelectedListener(Runnable action) { this.action = action; }
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { action.run(); }
        public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
