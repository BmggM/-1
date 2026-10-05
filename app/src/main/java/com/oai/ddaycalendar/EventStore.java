package com.oai.ddaycalendar;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class EventStore {
    private static final String PREFS = "dday_calendar_prefs";
    private static final String KEY_EVENTS = "events";
    private static final String KEY_DEFAULT_REMINDER = "default_reminder";
    private final SharedPreferences prefs;

    public EventStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Event> load() {
        List<Event> result = new ArrayList<>();
        String raw = prefs.getString(KEY_EVENTS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) result.add(Event.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
        return result;
    }

    public void save(List<Event> events) {
        JSONArray arr = new JSONArray();
        try {
            for (Event e : events) arr.put(e.toJson());
            prefs.edit().putString(KEY_EVENTS, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    public int getDefaultReminder() { return prefs.getInt(KEY_DEFAULT_REMINDER, -1); }
    public void setDefaultReminder(int minutes) { prefs.edit().putInt(KEY_DEFAULT_REMINDER, minutes).apply(); }

    public String exportBackup(List<Event> events) {
        try {
            JSONObject root = new JSONObject();
            root.put("format", "DdayCalendarBackup");
            root.put("version", 2);
            root.put("exportedAt", System.currentTimeMillis());
            root.put("defaultReminder", getDefaultReminder());
            JSONArray arr = new JSONArray();
            for (Event e : events) arr.put(e.toJson());
            root.put("events", arr);
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    public List<Event> importBackup(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        JSONArray arr = root.getJSONArray("events");
        List<Event> imported = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) imported.add(Event.fromJson(arr.getJSONObject(i)));
        setDefaultReminder(root.optInt("defaultReminder", -1));
        return imported;
    }
}
