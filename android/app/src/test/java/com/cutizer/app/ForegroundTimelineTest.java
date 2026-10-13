package com.cutizer.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ForegroundTimelineTest {
    @Test public void onlyOfficialInstagramIsTracked() {
        ForegroundTimeline foreground = new ForegroundTimeline();
        foreground.accept(100, 1, "com.instagram.lite", "Main"); assertFalse(foreground.isInstagram());
        foreground.accept(200, 1, "com.instagram.android", "Main"); assertTrue(foreground.isInstagram());
        foreground.accept(300, 1, "com.android.chrome", "Main"); assertFalse(foreground.isInstagram());
    }
    @Test public void latePauseOfOldActivityDoesNotClearNewActivity() {
        ForegroundTimeline foreground = new ForegroundTimeline();
        foreground.accept(100, 1, "com.instagram.android", "Feed");
        foreground.accept(200, 1, "com.instagram.android", "Reels");
        foreground.accept(210, 2, "com.instagram.android", "Feed"); assertTrue(foreground.isInstagram());
        foreground.accept(300, 2, "com.instagram.android", "Reels"); assertFalse(foreground.isInstagram());
    }
    @Test public void overlappingQueriesDontReapplyOldEvents() {
        ForegroundTimeline foreground = new ForegroundTimeline();
        foreground.accept(100, 1, "com.instagram.android", "Feed"); foreground.accept(200, 1, "launcher", "Home");
        foreground.accept(100, 1, "com.instagram.android", "Feed"); assertFalse(foreground.isInstagram());
    }
    @Test public void distinctEventsAtSameTimestampAreBothApplied() {
        ForegroundTimeline foreground = new ForegroundTimeline();
        foreground.accept(100, 1, "com.instagram.android", "Feed"); foreground.accept(100, 2, "com.instagram.android", "Feed");
        foreground.accept(100, 1, "com.instagram.android", "Feed"); assertFalse(foreground.isInstagram());
    }
    @Test public void lockScreenClearsForeground() {
        ForegroundTimeline foreground = new ForegroundTimeline(); foreground.accept(100, 1, "com.instagram.android", "Main"); foreground.accept(200, 3, "android", null); assertFalse(foreground.isInstagram());
    }
}
