package com.oai.ddaycalendar;

import org.json.JSONException;
import org.json.JSONObject;

public class Event {
    public static final String REPEAT_NONE = "NONE";
    public static final String REPEAT_DAILY = "DAILY";
    public static final String REPEAT_WEEKLY = "WEEKLY";
    public static final String REPEAT_MONTHLY = "MONTHLY";
    public static final String REPEAT_YEARLY = "YEARLY";

    public static final String COUNT_DDAY = "DDAY";
    public static final String COUNT_SINCE = "SINCE";

    public long id;
    public String title;
    public long dateMillis;
    public int timeMinutes; // -1 = all day
    public String note;
    public int category;
    public String repeatRule;
    public int reminderMinutes; // -1 = none, 0 = at time, otherwise minutes before
    public boolean pinned;
    public long createdAt;
    public String countMode;
    public boolean statusPinned;
    public String photoUri;

    public Event(long id, String title, long dateMillis) {
        this(id, title, dateMillis, -1, "", 0, REPEAT_NONE, -1, false,
                System.currentTimeMillis(), COUNT_DDAY, false, "");
    }

    public Event(long id, String title, long dateMillis, int timeMinutes, String note, int category,
                 String repeatRule, int reminderMinutes, boolean pinned, long createdAt) {
        this(id, title, dateMillis, timeMinutes, note, category, repeatRule, reminderMinutes,
                pinned, createdAt, COUNT_DDAY, false, "");
    }

    public Event(long id, String title, long dateMillis, int timeMinutes, String note, int category,
                 String repeatRule, int reminderMinutes, boolean pinned, long createdAt,
                 String countMode, boolean statusPinned, String photoUri) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.dateMillis = dateMillis;
        this.timeMinutes = (timeMinutes >= 0 && timeMinutes < 1440) ? timeMinutes : -1;
        this.note = note == null ? "" : note;
        this.category = Math.max(0, Math.min(category, 6));
        this.repeatRule = sanitizeRepeat(repeatRule);
        this.reminderMinutes = reminderMinutes;
        this.pinned = pinned;
        this.createdAt = createdAt;
        this.countMode = sanitizeCountMode(countMode);
        this.statusPinned = statusPinned;
        this.photoUri = photoUri == null ? "" : photoUri;
    }

    private static String sanitizeRepeat(String value) {
        if (REPEAT_DAILY.equals(value) || REPEAT_WEEKLY.equals(value) || REPEAT_MONTHLY.equals(value) || REPEAT_YEARLY.equals(value)) return value;
        return REPEAT_NONE;
    }

    private static String sanitizeCountMode(String value) {
        return COUNT_SINCE.equals(value) ? COUNT_SINCE : COUNT_DDAY;
    }

    public boolean isSinceCounter() {
        return COUNT_SINCE.equals(countMode);
    }

    public Event copyWithNewId(long newId) {
        return new Event(newId, title + " 복사본", dateMillis, timeMinutes, note, category,
                repeatRule, reminderMinutes, pinned, System.currentTimeMillis(), countMode, false, photoUri);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("dateMillis", dateMillis);
        obj.put("timeMinutes", timeMinutes);
        obj.put("note", note);
        obj.put("category", category);
        obj.put("repeatRule", repeatRule);
        obj.put("reminderMinutes", reminderMinutes);
        obj.put("pinned", pinned);
        obj.put("createdAt", createdAt);
        obj.put("countMode", countMode);
        obj.put("statusPinned", statusPinned);
        obj.put("photoUri", photoUri);
        return obj;
    }

    public static Event fromJson(JSONObject obj) throws JSONException {
        return new Event(
                obj.getLong("id"),
                obj.getString("title"),
                obj.getLong("dateMillis"),
                obj.optInt("timeMinutes", -1),
                obj.optString("note", ""),
                obj.optInt("category", 0),
                obj.optString("repeatRule", REPEAT_NONE),
                obj.optInt("reminderMinutes", -1),
                obj.optBoolean("pinned", false),
                obj.optLong("createdAt", obj.optLong("id", System.currentTimeMillis())),
                obj.optString("countMode", COUNT_DDAY),
                obj.optBoolean("statusPinned", false),
                obj.optString("photoUri", "")
        );
    }
}
