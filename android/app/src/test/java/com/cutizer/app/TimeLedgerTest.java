package com.cutizer.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class TimeLedgerTest {
    private final ZoneId zone = ZoneId.of("Asia/Tokyo");
    private final long start = Instant.parse("2026-10-13T01:00:00Z").toEpochMilli();
    @Test public void thresholdsFireOnceEach() {
        TimeLedger ledger = new TimeLedger(); int shown = 0;
        for (int seconds = 1; seconds <= 7201; seconds++) {
            long end = start + seconds * 1000L; ledger.record(end - 1000, end, zone);
            int stage = ledger.due(end, zone);
            if (stage >= 0) { assertEquals(shown++, stage); ledger.markShown(stage, end, zone); }
        }
        assertEquals(4, shown); assertEquals(7201000L, ledger.today(start, zone).millis);
        assertEquals(4, ledger.today(start, zone).reminders);
    }
    @Test public void oneMillisecondBeforeThresholdDoesNotFire() {
        TimeLedger ledger = new TimeLedger(); ledger.restore(TimeLedger.key(start, zone), 1799999, 0, 0);
        assertEquals(-1, ledger.due(start, zone)); ledger.record(start, start + 1, zone); assertEquals(0, ledger.due(start + 1, zone));
    }
    @Test public void midnightSplitsAnIntervalExactly() {
        TimeLedger ledger = new TimeLedger(); long midnight = Instant.parse("2026-10-13T15:00:00Z").toEpochMilli();
        ledger.record(midnight - 700, midnight + 1300, zone);
        assertEquals(700, ledger.day("2026-10-13").millis); assertEquals(1300, ledger.day("2026-10-14").millis);
        assertEquals(-1, ledger.due(midnight + 1300, zone));
    }
    @Test public void midnightHonorsDaylightSaving() {
        TimeLedger ledger = new TimeLedger(); ZoneId paris = ZoneId.of("Europe/Paris"); long midnight = Instant.parse("2026-10-25T23:00:00Z").toEpochMilli();
        ledger.record(midnight - 1000, midnight + 1000, paris);
        assertEquals(1000, ledger.day("2026-10-25").millis); assertEquals(1000, ledger.day("2026-10-26").millis);
    }
    @Test public void sleepsAndClockRewindsAreNotCounted() {
        TimeLedger ledger = new TimeLedger(); ledger.record(start, start + 3001, zone); ledger.record(start, start - 1, zone); ledger.record(start, start, zone);
        assertEquals(0, ledger.today(start, zone).millis);
    }
    @Test public void dismissedStageDoesNotRepeatAfterRestore() {
        TimeLedger ledger = new TimeLedger(); ledger.restore(TimeLedger.key(start, zone), 1800000, 1, 1);
        assertEquals(-1, ledger.due(start, zone)); ledger.markShown(0, start, zone); assertEquals(1, ledger.today(start, zone).reminders);
    }
    @Test public void failedOverlayCanRetryUntilShown() {
        TimeLedger ledger = new TimeLedger(); ledger.restore(TimeLedger.key(start, zone), 1800000, 0, 0);
        assertEquals(0, ledger.due(start, zone)); assertEquals(0, ledger.due(start + 1000, zone)); ledger.markShown(0, start, zone); assertEquals(-1, ledger.due(start, zone));
    }
    @Test public void historyIsBoundedAndInvalidRestoreRejected() {
        TimeLedger ledger = new TimeLedger(); for (int i = 0; i < 40; i++) ledger.record(start + i * 86400000L, start + i * 86400000L + 1000, zone);
        assertEquals(30, ledger.history().size()); ledger.restore("invalid", 1000, 0, 0); ledger.restore("2026-12-01", -1, 0, 0); assertEquals(30, ledger.history().size());
    }
    @Test public void longAnimationDurationsIncludeEntryAndExit() { assertEquals(122000, TimeLedger.DURATIONS[2]); assertEquals(602000, TimeLedger.DURATIONS[3]); }
}
