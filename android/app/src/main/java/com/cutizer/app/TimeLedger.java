package com.cutizer.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Pure Java accounting: measured intervals only; previews never enter the ledger. */
public final class TimeLedger {
    public static final int[] MINUTES = {30, 60, 90, 120};
    public static final long[] DURATIONS = {12_000, 15_000, 122_000, 602_000};
    public static final class Day {
        public long millis;
        public int fired;
        public int reminders;
        public Day(long millis, int fired, int reminders) {
            this.millis = millis; this.fired = fired; this.reminders = reminders;
        }
    }
    private final TreeMap<String, Day> days = new TreeMap<>();
    public static String key(long at, ZoneId zone) { return Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString(); }
    public Day day(String key) { return days.computeIfAbsent(key, ignored -> new Day(0, 0, 0)); }
    public Day today(long at, ZoneId zone) { return day(key(at, zone)); }
    public void restore(String key, long millis, int fired, int reminders) {
        try { LocalDate.parse(key); } catch (RuntimeException invalid) { return; }
        if (millis < 0 || millis > 26 * 3_600_000L || fired < 0 || fired > 15 || reminders < 0 || reminders > 4) return;
        days.put(key, new Day(millis, fired, reminders)); prune();
    }
    public void record(long start, long end, ZoneId zone) {
        if (end <= start || end - start > 3000) return;
        long cursor = start;
        while (cursor < end) {
            LocalDate date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate();
            long midnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long until = Math.min(end, midnight);
            day(date.toString()).millis += until - cursor;
            cursor = until;
        }
        prune();
    }
    public int due(long at, ZoneId zone) {
        Day day = today(at, zone);
        for (int i = 0; i < MINUTES.length; i++) {
            if (day.millis >= MINUTES[i] * 60_000L && (day.fired & (1 << i)) == 0) return i;
        }
        return -1;
    }
    public void markShown(int stage, long at, ZoneId zone) {
        if (stage < 0 || stage >= MINUTES.length) return;
        Day day = today(at, zone);
        if ((day.fired & (1 << stage)) == 0) { day.fired |= 1 << stage; day.reminders++; }
    }
    public Map<String, Day> history() { return new LinkedHashMap<>(days); }
    private void prune() { while (days.size() > 30) days.pollFirstEntry(); }
}
