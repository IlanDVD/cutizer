package com.cutizer.app;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

final class UsageProbe {
    private final UsageStatsManager manager;
    private ForegroundTimeline timeline = new ForegroundTimeline();
    private long lastQuery;
    UsageProbe(Context context) { manager = context.getSystemService(UsageStatsManager.class); }
    boolean instagram(long now) {
        if (now < lastQuery) reset();
        // Reconstruct foreground state at startup without importing past usage time.
        long start = lastQuery == 0 || now < lastQuery ? now - 86_400_000L : lastQuery - 5000;
        UsageEvents events = manager.queryEvents(start, now);
        if (events == null) { timeline.clear(); return false; }
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType(), mapped = 0;
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) mapped = ForegroundTimeline.RESUME;
            else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) mapped = ForegroundTimeline.PAUSE;
            else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE || type == UsageEvents.Event.KEYGUARD_SHOWN) mapped = ForegroundTimeline.SCREEN_OFF;
            if (mapped != 0) timeline.accept(event.getTimeStamp(), mapped, event.getPackageName(), event.getClassName());
        }
        lastQuery = now;
        return timeline.isInstagram();
    }
    void reset() { lastQuery = 0; timeline = new ForegroundTimeline(); }
}
