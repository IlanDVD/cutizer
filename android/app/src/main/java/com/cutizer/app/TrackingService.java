package com.cutizer.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.time.ZoneId;

public final class TrackingService extends Service {
    public static final String START = "com.cutizer.START", STOP = "com.cutizer.STOP", PREVIEW = "com.cutizer.PREVIEW", HIDE = "com.cutizer.HIDE";
    private static final int NOTIFICATION_ID = 7;
    private static final String CHANNEL = "miso_tracking";
    static TrackingService live;
    StateStore store;
    private UsageProbe probe;
    private OverlayController overlays;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastWall, lastElapsed, lastSave;
    boolean instagramActive;
    private boolean previousInstagram, stopping;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (stopping) return;
            long now = System.currentTimeMillis(), monotonic = SystemClock.elapsedRealtime();
            try {
                boolean screenOn = getSystemService(PowerManager.class).isInteractive() && !getSystemService(KeyguardManager.class).isKeyguardLocked();
                if (!Permissions.overlay(TrackingService.this) || (store.enabled() && !Permissions.usage(TrackingService.this))) {
                    store.error("Une autorisation a été retirée. Réactive-la dans Cutizer pour reprendre le suivi."); stopTracking(); return;
                }
                instagramActive = store.enabled() && screenOn && probe.instagram(now);
                if (!screenOn) { probe.reset(); overlays.hide(); }
                if (!instagramActive && overlays.active() && !overlays.isPreview()) overlays.hide();
                if (instagramActive && previousInstagram && lastWall > 0 && monotonic - lastElapsed <= 3000 && Math.abs((now - lastWall) - (monotonic - lastElapsed)) < 1500) {
                    store.ledger.record(lastWall, now, ZoneId.systemDefault());
                }
                if (instagramActive && !overlays.active()) {
                    int due = store.ledger.due(now, ZoneId.systemDefault());
                    if (due >= 0 && overlays.show(due, false, false)) { store.ledger.markShown(due, now, ZoneId.systemDefault()); store.save(); }
                }
                previousInstagram = instagramActive; lastWall = now; lastElapsed = monotonic;
                if (now - lastSave >= 5000) { store.save(); lastSave = now; updateNotification(); }
            } catch (RuntimeException error) {
                store.error("Le suivi Android est indisponible. Arrête puis relance le suivi depuis Cutizer."); stopTracking(); return;
            }
            if (!stopping) handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate() {
        super.onCreate(); live = this; store = new StateStore(this); probe = new UsageProbe(this);
        overlays = new OverlayController(this, () -> { if (!store.enabled()) stopTracking(); });
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Miso veille sur Instagram", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("État du suivi et commandes pour arrêter ou masquer Miso."); manager.createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? START : intent.getAction();
        if (STOP.equals(action)) { stopTracking(); return START_NOT_STICKY; }
        if (HIDE.equals(action)) { overlays.hide(); return store.enabled() ? START_STICKY : START_NOT_STICKY; }
        boolean preview = PREVIEW.equals(action);
        if (!Permissions.overlay(this) || (!preview && !Permissions.usage(this))) {
            store.error("Autorise le suivi d’utilisation et l’affichage au-dessus des applications."); stopTracking(); return START_NOT_STICKY;
        }
        if (!preview && intent != null) store.enabled(true);
        if (!preview && !store.enabled()) { stopTracking(); return START_NOT_STICKY; }
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION_ID, notification());
        } catch (RuntimeException error) {
            store.error("Android n’a pas pu démarrer le suivi. Réessaie depuis la fenêtre de Cutizer."); stopTracking(); return START_NOT_STICKY;
        }
        stopping = false; store.error("");
        if (preview) {
            int stage = intent.getIntExtra("stage", 0);
            if (!overlays.show(stage, true, intent.getBooleanExtra("short", true))) {
                store.error("Impossible d’afficher Miso. Vérifie l’autorisation de superposition.");
                if (!store.enabled()) { stopTracking(); return START_NOT_STICKY; }
            }
        }
        handler.removeCallbacks(tick); previousInstagram = false; lastWall = 0; tick.run();
        return store.enabled() ? START_STICKY : START_NOT_STICKY;
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, TrackingService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent hide = PendingIntent.getService(this, 2, new Intent(this, TrackingService.class).setAction(HIDE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_miso).setContentTitle(store.enabled() ? "Miso veille sur Instagram" : "Un petit essai avec Miso")
                .setContentText(StateStore.duration(store.today().millis) + " aujourd’hui · " + (instagramActive ? "Instagram au premier plan" : "En attente d’Instagram"))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Masquer", hide).build())
                .addAction(new Notification.Action.Builder(null, "Arrêter", stop).build()).build();
    }
    private void updateNotification() { getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification()); }
    private void stopTracking() {
        if (stopping) return;
        stopping = true; store.enabled(false); store.save(); instagramActive = false;
        handler.removeCallbacksAndMessages(null); overlays.hide(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        stopping = true; handler.removeCallbacksAndMessages(null); overlays.hide(); store.save();
        if (live == this) live = null; super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
