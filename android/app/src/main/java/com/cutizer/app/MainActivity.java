package com.cutizer.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = 0xFFF7F8F2, GREEN = 0xFF607C4E, INK = 0xFF344638, MUTED = 0xFF7B8872, PALE = 0xFFEAF0E1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private Button status, startButton;
    private TextView counter, activeLabel, errorLabel, speech;
    private ProgressBar progress;
    private String page = "home";
    private boolean shortPreview = true;
    private int pets;
    private boolean previousUsage, previousOverlay, previousNotifications;
    private final Runnable refresh = new Runnable() { @Override public void run() { update(); handler.postDelayed(this, 1000); } };
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (bundle != null) { page = bundle.getString("page", "home"); shortPreview = bundle.getBoolean("short", true); }
        build();
    }
    @Override protected void onResume() { super.onResume(); rebuildPage(); handler.removeCallbacks(refresh); refresh.run(); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("page", page); out.putBoolean("short", shortPreview); super.onSaveInstanceState(out); }
    private StateStore state() { return TrackingService.live != null ? TrackingService.live.store : new StateStore(this); }
    private boolean running() { return TrackingService.live != null && state().enabled(); }
    private void build() {
        LinearLayout root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = row(); header.setPadding(dp(23), dp(15), dp(20), dp(10));
        TextView brand = text("cutizer·", 28, INK); brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        status = button("Miso se repose", false, v -> toggleTracking()); status.setTextSize(11); header.addView(status, new LinearLayout.LayoutParams(-2, dp(44))); root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        content = column(); content.setPadding(dp(24), dp(14), dp(24), dp(22)); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = row(); nav.setPadding(dp(12), dp(5), dp(12), dp(7)); nav.setBackgroundColor(0xFFF0F3E9);
        String[] labels = {"⌂  Miso", "◷  Mon temps", "✧  Réglages"}, pages = {"home", "stats", "settings"};
        for (int i = 0; i < labels.length; i++) {
            String target = pages[i]; Button item = button(labels[i], false, view -> { page = target; rebuildPage(); scroll.smoothScrollTo(0, 0); });
            item.setTextSize(12); item.setBackgroundColor(Color.TRANSPARENT); nav.addView(item, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        root.addView(nav); setContentView(root);
    }
    private void rebuildPage() {
        content.removeAllViews(); counter = null; activeLabel = null; progress = null; startButton = null;
        previousUsage = Permissions.usage(this); previousOverlay = Permissions.overlay(this); previousNotifications = getSystemService(NotificationManager.class).areNotificationsEnabled();
        if ("stats".equals(page)) stats(); else if ("settings".equals(page)) settings(); else home();
        errorLabel = text("", 12, 0xFF9B6A45); errorLabel.setPadding(0, dp(14), 0, 0); content.addView(errorLabel); update();
    }
    private void home() {
        eyebrow("TON PETIT GARDIEN DU TEMPS"); title("Du temps pour toi."); subtitle("Miso t’accompagne sur Instagram.");
        speech = text("Coucou, moi c’est Miso ♡", 13, GREEN); speech.setGravity(Gravity.CENTER); speech.setPadding(dp(14), dp(12), dp(14), dp(12)); speech.setBackground(round(Color.WHITE, 18));
        LinearLayout.LayoutParams bubble = new LinearLayout.LayoutParams(-2, -2); bubble.gravity = Gravity.CENTER_HORIZONTAL; bubble.topMargin = dp(22); content.addView(speech, bubble);
        ImageView mascot = new ImageView(this); mascot.setImageResource(R.drawable.miso_happy); mascot.setAdjustViewBounds(true); mascot.setScaleType(ImageView.ScaleType.FIT_CENTER); mascot.setContentDescription("Faire un câlin à Miso");
        LinearLayout.LayoutParams image = new LinearLayout.LayoutParams(-1, dp(245)); image.topMargin = dp(5); content.addView(mascot, image);
        mascot.setOnClickListener(view -> {
            String[] messages = {"Ça fait du bien, un petit câlin ♡", "Les nuages sont jolis aussi.", "Je suis là. À ton rythme.", "Une gorgée d’eau, peut-être ?"};
            speech.setText(messages[pets++ % messages.length]);
            ObjectAnimator.ofFloat(mascot, "rotation", 0, -6, 5, 0).setDuration(550).start();
        });
        TextView hint = text("Un petit clic, un petit câlin.", 11, MUTED); hint.setGravity(Gravity.CENTER); content.addView(hint); space(18);
        LinearLayout card = card(); card.addView(text("◎   SUR INSTAGRAM AUJOURD’HUI", 10, MUTED));
        counter = text("0 min", 34, INK); counter.setTypeface(Typeface.create("serif", Typeface.NORMAL)); card.addView(counter);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(120); progress.setProgressTintList(ColorStateList.valueOf(0xFF9BB780)); card.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        activeLabel = text("", 12, MUTED); activeLabel.setPadding(0, dp(8), 0, 0); card.addView(activeLabel); content.addView(card); space(14);
        startButton = button("Démarrer le suivi", true, v -> toggleTracking()); content.addView(startButton, fullButton());
        Button instagram = button("Ouvrir Instagram  ↗", false, v -> openInstagram()); content.addView(instagram, fullButton());
        footnote("Le compteur cumule uniquement Instagram pendant que le suivi est activé. Tout reste sur ton téléphone.");
    }
    private void stats() {
        eyebrow("CHAQUE PETITE PAUSE COMPTE"); title("Ton temps, en douceur."); subtitle("Un regard sur tes habitudes, sans pression."); space(24);
        StateStore saved = state(); LinearLayout card = card(); card.addView(text("Aujourd’hui sur Instagram", 12, MUTED));
        counter = text(StateStore.duration(saved.today().millis), 38, INK); counter.setTypeface(Typeface.create("serif", Typeface.NORMAL)); card.addView(counter);
        activeLabel = text("", 12, MUTED); card.addView(activeLabel); content.addView(card); space(14);
        LinearLayout history = card(); history.addView(text("Les sept derniers jours", 17, INK));
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i); TimeLedger.Day day = saved.ledger.history().get(date.toString()); long duration = day == null ? 0 : day.millis;
            LinearLayout line = row(); line.setPadding(0, dp(13), 0, dp(3)); TextView label = text(i == 0 ? "Aujourd’hui" : date.format(DateTimeFormatter.ofPattern("EEE d", Locale.FRANCE)), 12, MUTED);
            line.addView(label, new LinearLayout.LayoutParams(0, -2, 1)); line.addView(text(StateStore.duration(duration), 13, INK)); history.addView(line);
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(120); bar.setProgress((int)Math.min(120, duration/60_000)); bar.setProgressTintList(ColorStateList.valueOf(0xFF9BB780)); history.addView(bar, new LinearLayout.LayoutParams(-1, dp(5)));
        }
        content.addView(history); space(14); LinearLayout reminders = card(); reminders.addView(text(saved.today().reminders + " petits rappels aujourd’hui", 16, INK)); reminders.addView(text("Les essais ne changent pas tes statistiques.", 12, MUTED)); content.addView(reminders);
        footnote("Remise à zéro du compteur à minuit. Historique de 30 jours enregistré uniquement sur cet appareil. Le temps antérieur à l’activation n’est pas importé.");
    }
    private void settings() {
        eyebrow("À TON RYTHME"); title("On fait équipe ?"); subtitle("Deux autorisations, puis Miso s’occupe des pauses."); space(20);
        permissionCard("1  ·  Reconnaître Instagram", "L’accès aux données d’utilisation sert à savoir si Instagram est au premier plan. Miso ne lit ni tes messages, ni les publications.", previousUsage, "Autoriser l’accès", () -> {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:" + getPackageName())); openSetting(intent, Settings.ACTION_USAGE_ACCESS_SETTINGS);
        });
        permissionCard("2  ·  Afficher Miso", "La superposition permet à Miso d’apparaître au-dessus d’Instagram. Chaque apparition possède un bouton Masquer.", previousOverlay, "Autoriser la superposition", () -> openSetting(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        permissionCard("3  ·  Garder les commandes à portée", "La notification indique que le suivi fonctionne et permet de l’arrêter. Recommandé, mais facultatif.", previousNotifications, "Activer la notification", () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !state().preferences.getBoolean("notificationAsked", false)) {
                state().preferences.edit().putBoolean("notificationAsked", true).apply(); requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 21);
            } else openSetting(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()), Settings.ACTION_SETTINGS);
        });
        startButton = button("Démarrer le suivi d’Instagram", true, v -> toggleTracking()); content.addView(startButton, fullButton()); space(24);
        content.addView(text("Il a son petit caractère.", 25, INK)); subtitle("Découvre les quatre interventions.");
        CheckBox shortBox = new CheckBox(this); shortBox.setText("Essais courts · 12 secondes"); shortBox.setTextSize(12); shortBox.setTextColor(GREEN); shortBox.setChecked(shortPreview); shortBox.setOnCheckedChangeListener((button, checked) -> shortPreview = checked); content.addView(shortBox);
        String[] names = {"30 min · La petite balade", "60 min · Le regard en rappel", "90 min · Les bras croisés", "120 min · Le grand sort"};
        String[] details = {"De droite à gauche, en bas de l’écran.", "Il descend au centre, te juge et remonte.", "Il reste 2 minutes, puis s’en va.", "Il recouvre l’écran de sorts pendant 10 minutes."};
        for (int i = 0; i < names.length; i++) {
            final int stage = i; LinearLayout card = card(); card.addView(text(names[i], 16, INK)); TextView detail = text(details[i], 12, MUTED); detail.setPadding(0, dp(7), 0, dp(8)); card.addView(detail);
            card.addView(button("Essayer  ▷", false, v -> preview(stage)), fullButton()); content.addView(card); space(10);
        }
        footnote("Un rappel par palier et par jour. Quitter Instagram ou verrouiller le téléphone ferme les rappels automatiques. Le grand sort reste toujours masquable.");
        Button privacy = button("Mes données et le suivi", false, v -> new AlertDialog.Builder(this).setTitle("Ça reste entre toi et Miso")
                .setMessage("Seule l’application Instagram officielle (com.instagram.android) est suivie. Aucun compte, serveur, accès Internet, lecture de messages ou service d’accessibilité.\n\nLe suivi nécessite une notification de service Android et les deux autorisations. Après un redémarrage du téléphone ou un arrêt forcé, rouvre Cutizer et démarre le suivi. Certains téléphones peuvent aussi arrêter les services en arrière-plan.\n\nLe temps est mesuré environ chaque seconde. Les changements d’application peuvent perdre une ou deux secondes. Instagram Lite, les sites web et les profils professionnels ne sont pas inclus.").setPositiveButton("Compris", null).show());
        content.addView(privacy, fullButton());
    }
    private void permissionCard(String title, String description, boolean granted, String action, Runnable click) {
        LinearLayout card = card(); card.addView(text((granted ? "✓  " : "") + title, 16, INK)); TextView explanation = text(description, 12, MUTED); explanation.setPadding(0, dp(9), 0, dp(8)); card.addView(explanation);
        card.addView(button(granted ? "Autorisé · ouvrir les réglages" : action, false, v -> click.run()), fullButton()); content.addView(card); space(12);
    }
    private void update() {
        StateStore saved = state(); boolean enabled = running();
        status.setText(enabled ? "● Miso veille  Ⅱ" : "○ En pause");
        if (counter != null) counter.setText(StateStore.duration(saved.today().millis));
        if (progress != null) progress.setProgress((int)Math.min(120, saved.today().millis / 60_000));
        if (activeLabel != null) activeLabel.setText(enabled ? TrackingService.live.instagramActive ? "Instagram est au premier plan" : "En attente d’Instagram · premier rappel à 30 min" : "Le suivi est en pause");
        if (startButton != null) startButton.setText(enabled ? "Mettre le suivi en pause" : Permissions.usage(this) && Permissions.overlay(this) ? "Démarrer le suivi d’Instagram" : "Activer les autorisations");
        if (errorLabel != null) { errorLabel.setText(saved.error()); errorLabel.setVisibility(saved.error().isEmpty() ? View.GONE : View.VISIBLE); }
        if ("settings".equals(page) && (previousUsage != Permissions.usage(this) || previousOverlay != Permissions.overlay(this) || previousNotifications != getSystemService(NotificationManager.class).areNotificationsEnabled())) rebuildPage();
    }
    private void toggleTracking() {
        if (running()) { startService(new Intent(this, TrackingService.class).setAction(TrackingService.STOP)); return; }
        if (!Permissions.usage(this) || !Permissions.overlay(this)) { page = "settings"; rebuildPage(); toast("Active les deux premières autorisations pour démarrer."); return; }
        try { startForegroundService(new Intent(this, TrackingService.class).setAction(TrackingService.START)); }
        catch (RuntimeException error) { toast("Android n’a pas pu démarrer le suivi. Réessaie depuis cette page."); }
    }
    private void preview(int stage) {
        if (!Permissions.overlay(this)) { toast("Autorise d’abord l’affichage au-dessus des applications."); return; }
        try { startForegroundService(new Intent(this, TrackingService.class).setAction(TrackingService.PREVIEW).putExtra("stage", stage).putExtra("short", shortPreview)); }
        catch (RuntimeException error) { toast("Impossible de lancer l’essai. Vérifie les autorisations."); }
    }
    private void openInstagram() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(ForegroundTimeline.INSTAGRAM);
        if (intent != null) { try { startActivity(intent); } catch (ActivityNotFoundException error) { toast("Instagram est indisponible."); } }
        else new AlertDialog.Builder(this).setTitle("Instagram n’est pas installé")
                .setMessage("Cette version accompagne l’application Instagram officielle. Installe-la sur ce téléphone pour activer le suivi ; tu peux déjà essayer les animations.")
                .setPositiveButton("Compris", null).show();
    }
    private void openSetting(Intent intent, String fallback) {
        try { startActivity(intent); } catch (ActivityNotFoundException error) { try { startActivity(new Intent(fallback)); } catch (ActivityNotFoundException ignored) { toast("Ouvre les paramètres Android, puis les autorisations spéciales de Cutizer."); } }
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private LinearLayout card() { LinearLayout view = column(); view.setPadding(dp(18), dp(17), dp(18), dp(17)); view.setBackground(round(0xFFFEFFF9, 20)); return view; }
    private GradientDrawable round(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), 0xFFE0E6D5); return drawable; }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3), 1); return view; }
    private Button button(String label, boolean primary, View.OnClickListener action) { Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(14); view.setTextColor(primary ? Color.WHITE : GREEN); view.setBackground(round(primary ? GREEN : PALE, 15)); view.setPadding(dp(14), dp(8), dp(14), dp(8)); view.setMinHeight(dp(48)); view.setMinimumHeight(dp(48)); view.setOnClickListener(action); return view; }
    private LinearLayout.LayoutParams fullButton() { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8); return params; }
    private void eyebrow(String value) { TextView view = text(value, 10, MUTED); view.setLetterSpacing(.14f); content.addView(view); space(10); }
    private void title(String value) { TextView view = text(value, 32, INK); view.setTypeface(Typeface.create("serif", Typeface.NORMAL)); content.addView(view); space(7); }
    private void subtitle(String value) { content.addView(text(value, 13, MUTED)); }
    private void footnote(String value) { TextView view = text(value, 11, MUTED); view.setPadding(0, dp(15), 0, 0); content.addView(view); }
    private void space(int height) { View view = new View(this); content.addView(view, new LinearLayout.LayoutParams(1, dp(height))); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
