package com.cutizer.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;

final class StateStore {
    final SharedPreferences preferences;
    final TimeLedger ledger = new TimeLedger();
    StateStore(Context context) {
        preferences = context.getSharedPreferences("cutizer", Context.MODE_PRIVATE);
        try {
            JSONObject days = new JSONObject(preferences.getString("days", "{}"));
            for (Iterator<String> it = days.keys(); it.hasNext();) {
                String key = it.next(); JSONObject day = days.optJSONObject(key);
                if (day != null) ledger.restore(key, day.optLong("millis", -1), day.optInt("fired", 0), day.optInt("reminders", 0));
            }
        } catch (Exception error) { preferences.edit().putString("error", "La sauvegarde précédente était illisible. Le suivi repart de zéro.").apply(); }
    }
    boolean enabled() { return preferences.getBoolean("enabled", false); }
    void enabled(boolean value) { preferences.edit().putBoolean("enabled", value).apply(); }
    String error() { return preferences.getString("error", ""); }
    void error(String message) { preferences.edit().putString("error", message).apply(); }
    TimeLedger.Day today() { return ledger.today(System.currentTimeMillis(), ZoneId.systemDefault()); }
    void save() {
        try {
            JSONObject days = new JSONObject();
            for (Map.Entry<String, TimeLedger.Day> entry : ledger.history().entrySet()) {
                TimeLedger.Day day = entry.getValue();
                days.put(entry.getKey(), new JSONObject().put("millis", day.millis).put("fired", day.fired).put("reminders", day.reminders));
            }
            preferences.edit().putString("days", days.toString()).apply();
        } catch (Exception error) { error("Impossible de sauvegarder les statistiques."); }
    }
    static String duration(long millis) {
        long min = millis / 60_000;
        if (min == 0 && millis > 0) return "< 1 min";
        return min < 60 ? min + " min" : (min / 60) + " h " + String.format(java.util.Locale.FRANCE, "%02d", min % 60);
    }
}
