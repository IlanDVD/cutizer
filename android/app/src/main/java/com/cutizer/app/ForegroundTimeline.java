package com.cutizer.app;

import java.util.HashSet;
import java.util.Set;

/** Tracks the most recently resumed activity and tolerates late pauses of another activity. */
public final class ForegroundTimeline {
    public static final String INSTAGRAM = "com.instagram.android";
    public static final int RESUME = 1, PAUSE = 2, SCREEN_OFF = 3;
    private long timestamp = -1;
    private final Set<String> atTimestamp = new HashSet<>();
    private String foreground = "", activity = "";
    public void accept(long at, int type, String packageName, String className) {
        if (at < timestamp) return;
        if (at > timestamp) { timestamp = at; atTimestamp.clear(); }
        String eventKey = type + ":" + packageName + ":" + className;
        if (!atTimestamp.add(eventKey)) return;
        if (type == RESUME) { foreground = packageName == null ? "" : packageName; activity = className == null ? "" : className; }
        if (type == PAUSE && foreground.equals(packageName) && activity.equals(className == null ? "" : className)) clear();
        if (type == SCREEN_OFF) clear();
    }
    public void clear() { foreground = ""; activity = ""; }
    public boolean isInstagram() { return INSTAGRAM.equals(foreground); }
}
