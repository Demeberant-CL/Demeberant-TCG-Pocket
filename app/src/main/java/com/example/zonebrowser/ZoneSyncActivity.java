package com.example.zonebrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class ZoneSyncActivity extends Activity {
    private static final int EXPORT_JSON = 71;
    private static final int EXPORT_TRACE = 72;
    private static final int BG = 0xFFF1ECE4, TEXT = 0xFF25211C, ACCENT = 0xFFA84400;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView browser;
    private TextView status;
    private Button readButton;
    private View progressOverlay;
    private Button syncButton, accountButton, closeButton, cancelButton;
    private boolean autoStart, syncSucceeded, importing;
    private String profileUrl;
    private JSONObject preview;
    private String pendingExport;
    private int generation;
    private int syncPhase, syncFlowId;
    private long syncDeadline;
    private WebView syncBrowser;
    private String syncProfile;
    private boolean syncEvaluationPending;
    private Runnable cancelScan;
    private Runnable checkpointScan;
    private android.util.AtomicFile previewFile;
    private boolean resumed;
    private boolean pendingScanResult;
    private boolean loaded;
    private boolean pageFailed;
    private SharedPreferences preferences;
    private final ProcessTrace trace = new ProcessTrace();
    private android.util.AtomicFile traceFile;
    private Button recordButton;
    private final Runnable probeLoop = new Runnable() {
        @Override public void run() {
            WebView source = browser;
            if (source == null || !trace.isRecording() || (!resumed && cancelScan == null && syncPhase == 0) || !ZoneUrl.canBrowse(source.getUrl())) return;
            try {
                source.evaluateJavascript(asset("sync-probe.js") + ";" + asset("process-probe.js").replace("JSON.stringify(window.__pocketZoneProbe.drain())", "JSON.stringify(window.__pocketZoneSyncProbe.drain().concat(window.__pocketZoneProbe.drain()).slice(0,40))"), result -> {
                    if (browser != source || !trace.isRecording() || result == null || result.length() > 8000) return;
                    try {
                        Object decoded = new JSONTokener(result).nextValue();
                        if (!(decoded instanceof String)) return;
                        JSONArray events = new JSONArray((String) decoded);
                        for (int i = 0; i < Math.min(events.length(), 40); i++) {
                            String kind = events.optString(i);
                            trace.add(kind, source.getUrl(), 0);
                        }
                    } catch (Exception ignored) { }
                });
            } catch (Exception ignored) { }
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        preferences = getSharedPreferences("zone-experiment", MODE_PRIVATE);
        traceFile = new android.util.AtomicFile(new java.io.File(getFilesDir(), "zone-process.json"));
        try (java.io.InputStream input = traceFile.openRead()) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int size;
            while ((size = input.read(buffer)) != -1 && bytes.size() <= 160000) bytes.write(buffer, 0, size);
            if (bytes.size() <= 160000) trace.restore(bytes.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception ignored) { }
        if (saved != null) {
            profileUrl = ZoneUrl.normalize(saved.getString("profile"));
            pendingExport = saved.getString("pendingExport");
        }
        if (profileUrl == null) profileUrl = ZoneUrl.normalize(preferences.getString("profile", ""));
        previewFile = new android.util.AtomicFile(new java.io.File(getFilesDir(), "zone-preview.json"));
        try (java.io.InputStream input = previewFile.openRead()) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int size;
            while ((size = input.read(buffer)) != -1 && bytes.size() <= Preview.MAX_BYTES) bytes.write(buffer, 0, size);
            if (bytes.size() <= Preview.MAX_BYTES) preview = Preview.validate(bytes.toString(StandardCharsets.UTF_8.name()), profileUrl);
        } catch (Exception ignored) { }
        if (profileUrl == null) profileUrl = ZoneUrl.normalize(getSharedPreferences("zone-import-test", MODE_PRIVATE).getString("player", ""));
        if (profileUrl != null) { autoStart = true; openBrowser(profileUrl); }
        else home();
    }

    private void savePreviewSnapshot() {
        if (preview == null || previewFile == null) return;
        java.io.FileOutputStream output = null;
        try {
            byte[] bytes = preview.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > Preview.MAX_BYTES) return;
            output = previewFile.startWrite(); output.write(bytes); previewFile.finishWrite(output);
        } catch (Exception e) { if (output != null) previewFile.failWrite(output); }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setBackgroundColor(BG);
        return view;
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(TEXT);
        view.setPadding(0, dp(6), 0, dp(8));
        return view;
    }

    private Button button(String text, Runnable action) {
        Button view = new Button(this);
        view.setText(text);
        view.setTextSize(15);
        view.setAllCaps(false);
        view.setMinHeight(dp(48));
        view.setTextColor(Color.WHITE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(ACCENT);
        background.setCornerRadius(dp(12));
        view.setBackground(background);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), dp(6), dp(5));
        view.setLayoutParams(params);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void home() {
        destroyBrowser();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(); scroll.addView(content);
        content.addView(label("Sincronizar colección", 25));
        content.addView(label("Conecta tu cuenta de Pokémon Zone en este visor. La sesión se conserva en esta copia de prueba.", 16));
        content.addView(button("Conectar cuenta", () -> openBrowser("https://www.pokemon-zone.com/settings/")));
        content.addView(label("Si tu perfil aún no se reconoce, pega tu ID de amigo o su enlace:", 16));
        EditText input = new EditText(this); input.setSingleLine(true); input.setTextColor(TEXT);
        input.setHintTextColor(0xFF65594C); input.setHint("ID de amigo o enlace de perfil");
        input.setText(profileUrl == null ? "" : profileUrl); content.addView(input);
        content.addView(button("Sincronizar colección", () -> {
            String normalized = ZoneUrl.normalize(input.getText().toString());
            if (normalized == null || !ZoneUrl.friendId(normalized).matches("[0-9]{16}")) { message("Usa el ID de amigo de 16 números o el enlace de tu perfil."); return; }
            profileUrl = normalized; preferences.edit().putString("profile", profileUrl).apply();
            autoStart = true; openBrowser(profileUrl);
        }));
        content.addView(label("La primera conexión puede requerir el acceso con contraseña de Pokémon Zone y la vinculación de Nintendo. Google en un navegador externo no comparte su sesión con el visor.", 15));
        content.addView(label("Después se sincroniza, recorre y guarda desde un solo botón. Solo cambian las cantidades de esta copia de prueba.", 15));
        content.addView(button("Volver a la app", this::finish));
        setRoot(scroll);
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "0.1"; }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void openBrowser(String url) {
        destroyBrowser();
        trace.start(); saveTrace();
        LinearLayout content = column();
        
        status = label("Cargando perfil…", 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        readButton = button("Leer", this::readPage); // Internal compatibility; not exposed in the normal flow.
        HorizontalScrollView controls = new HorizontalScrollView(this);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); controls.addView(row);
        syncButton = button("Sincronizar colección", this::syncAndRead);
        accountButton = button("Conectar cuenta", () -> {
            autoStart = false;
            if (browser == null) openBrowser("https://www.pokemon-zone.com/settings/");
            else browser.loadUrl("https://www.pokemon-zone.com/settings/");
        });
        cancelButton = button("Cancelar", () -> {
            autoStart = false; cancelSync("Cancelado. La colección guardada se conserva.");
            if (cancelScan != null) cancelScan.run();
            setRunning(false);
        });
        closeButton = button("Volver", () -> { if (!importing) finish(); });
        row.addView(syncButton); row.addView(accountButton); row.addView(cancelButton); row.addView(closeButton);
        row.addView(button("Copiar diagnóstico", () -> {
            StringBuilder text = new StringBuilder("POCKET ZONE · PRUEBA " + versionName() + "\n");
            text.append(status.getText()).append("\nFase Sync: ").append(syncPhase).append("\n");
            try {
                JSONArray events = new JSONObject(trace.exportJson()).optJSONArray("events");
                if (events != null) for (int i = Math.max(0, events.length() - 20); i < events.length(); i++) {
                    JSONObject event = events.getJSONObject(i);
                    text.append(event.optString("event")).append(" ").append(event.optString("route"))
                        .append(" ").append(event.optInt("code")).append("\n");
                }
            } catch (Exception ignored) { }
            ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico de sincronización", text.toString()));
            message("Diagnóstico copiado; puedes pegarlo en el chat.");
        }));
        content.addView(controls);
        browser = new WebView(this);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkImage(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                if (ZoneUrl.canBrowse(request.getUrl().toString())) return false;
                if (ZoneUrl.isNintendoLogin(request.getUrl().toString())) {
                    Uri target = request.getUrl();
                    new AlertDialog.Builder(ZoneSyncActivity.this).setTitle("Vincular Nintendo")
                        .setMessage("Nintendo se abrirá en tu navegador. Mantén pulsado Select this account y copia su enlace. Vuelve a esta pantalla, pégalo en Paste Copied Link Code y pulsa Link Account. No se leerá el portapapeles ni se guardará ese enlace en el diagnóstico.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Abrir Nintendo", (dialog, which) -> {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, target));
                                trace.add("nintendo_open", null, 0);
                                status.setText("Vuelve aquí para pegar el enlace en el formulario de Zone. No necesitas recargar Mi cuenta.");
                            } catch (android.content.ActivityNotFoundException e) { message("No se encontró un navegador para abrir Nintendo."); }
                        }).show();
                    return true;
                }
                trace.add("blocked_link", request.getUrl().toString(), 0);
                loaded = false; readButton.setEnabled(false);
                status.setText("Acceso externo bloqueado. Puedes volver al formulario de Pokémon Zone para usar usuario y contraseña.");
                new AlertDialog.Builder(ZoneSyncActivity.this).setTitle("Inicio de sesión externo")
                    .setMessage("Esta redirección sale de Pokémon Zone. El inicio de sesión con usuario y contraseña del sitio se puede probar dentro del visor. Las sesiones de un navegador externo no se transfieren aquí.")
                    .setNegativeButton("Cerrar", null)
                    .setPositiveButton("Formulario de Pokémon Zone", (dialog, which) -> view.loadUrl("https://www.pokemon-zone.com/accounts/login/"))
                    .show();
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                trace.add("request", uri.toString(), 0, request.getMethod());
                boolean blocked = !"https".equalsIgnoreCase(uri.getScheme())
                    || (request.isForMainFrame() && !ZoneUrl.canBrowse(uri.toString()));
                if (blocked) return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                return null;
            }

            @Override public void onPageStarted(WebView view, String pageUrl, android.graphics.Bitmap icon) {
                adoptProfile(pageUrl);
                boolean interruptedScan = cancelScan != null;
                if (checkpointScan != null) checkpointScan.run();
                cancelScan = null; checkpointScan = null;
                if (interruptedScan) { syncSucceeded = false; setRunning(false); }
                if (syncPhase != 0) {
                    String expected = syncPhase >= 3 ? syncProfile + "cards/" : syncProfile;
                    if (!expected.equals(pageUrl)) cancelSync("La navegación interrumpió la sincronización.");
                }
                if (syncPhase == 0) ScanService.end(ZoneSyncActivity.this);
                generation++; loaded = false; pageFailed = false; readButton.setEnabled(false);
                trace.add("page_start", pageUrl, 0);
                status.setText("Cargando… Espera a que aparezcan los datos del perfil.");
            }

            @Override public void onPageFinished(WebView view, String pageUrl) {
                adoptProfile(pageUrl);
                loaded = !pageFailed && ZoneUrl.belongsTo(pageUrl, profileUrl);
                trace.add("page_end", pageUrl, 0);
                readButton.setEnabled(loaded);
                status.setText(loaded ? "Página cargada. Si muestra «Loading», espera antes de leer." : "Página de cuenta. Puedes interactuar con el sitio; Leer se habilita solo en tu perfil.");
                handler.removeCallbacks(probeLoop);
                if (trace.isRecording()) handler.post(probeLoop);
                if (syncBrowser == view && syncPhase == 1 && syncProfile.equals(pageUrl) && loaded) {
                    syncPhase = 2;
                    status.setText("Sincronizando Pokémon Zone… Esperando confirmación.");
                } else if (syncBrowser == view && syncPhase == 3 && (syncProfile + "cards/").equals(pageUrl) && loaded) {
                    syncPhase = 4;
                    status.setText("Sincronización confirmada. Esperando a que aparezcan las cartas…");
                }
                if (autoStart && syncPhase == 0 && cancelScan == null && loaded && profileUrl.equals(pageUrl)) {
                    autoStart = false; handler.post(() -> { if (browser == view && !importing) syncAndRead(); });
                }
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                trace.add("network_error", request.getUrl().toString(), error.getErrorCode());
                if (request.isForMainFrame()) {
                    cancelSync("El sitio no pudo cargar la página para Sync."); loaded = false; pageFailed = true; generation++; readButton.setEnabled(false);
                    setRunning(false);
                    status.setText("No se pudo cargar el perfil. Comprueba la conexión y vuelve a abrirlo. No se importó nada.");
                }
            }

            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                cancelSync("El visor se cerró; no se importó el recorrido.");
                generation++; cancelScan = null; checkpointScan = null;
                ScanService.end(ZoneSyncActivity.this);
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                view.destroy(); browser = null; setRunning(false);
                status.setText("Android cerró el visor. Las cantidades guardadas se conservan. Vuelve a entrar para repetir.");
                return true;
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                trace.add("http_error", request.getUrl().toString(), response.getStatusCode());
                if (request.isForMainFrame()) {
                    cancelSync("El sitio no pudo cargar la página para Sync."); loaded = false; pageFailed = true; generation++; readButton.setEnabled(false);
                    setRunning(false);
                    status.setText("El sitio respondió HTTP " + response.getStatusCode() + ". No se han leído datos.");
                }
            }
        });
        android.widget.FrameLayout stage = new android.widget.FrameLayout(this);
        stage.addView(browser, new android.widget.FrameLayout.LayoutParams(-1, -1));
        LinearLayout cover = column(); cover.setGravity(android.view.Gravity.CENTER);
        cover.setClickable(true); cover.setFocusable(true);
        cover.addView(label("Actualizando tu colección…", 23));
        cover.addView(new android.widget.ProgressBar(this));
        cover.addView(label("El recorrido trabaja automáticamente. Puedes cancelar con el botón de arriba.", 16));
        cover.addView(label("No necesitas bajar por las cartas ni guardar un archivo JSON.", 16));
        progressOverlay = cover; cover.setVisibility(View.INVISIBLE);
        stage.addView(cover, new android.widget.FrameLayout.LayoutParams(-1, -1));
        content.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        setRoot(content);
        browser.loadUrl(url);
    }

    private void adoptProfile(String pageUrl) {
        String normalized = ZoneUrl.normalize(pageUrl);
        if (normalized != null && !normalized.equals(profileUrl)) {
            profileUrl = normalized;
            preferences.edit().putString("profile", profileUrl).apply();
            preview = null;
        }
    }

    private void requestWorkNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !preferences.getBoolean("notificationPermissionAsked", false)) {
            preferences.edit().putBoolean("notificationPermissionAsked", true).apply();
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 73);
        }
    }

    private void setRunning(boolean running) {
        if (progressOverlay != null) progressOverlay.setVisibility(running ? View.VISIBLE : View.INVISIBLE);
        if (syncButton != null) syncButton.setEnabled(!running && !importing);
        if (accountButton != null) accountButton.setEnabled(!running && !importing);
        if (closeButton != null) closeButton.setEnabled(!running && !importing);
        if (cancelButton != null) cancelButton.setEnabled(running && !importing);
    }

    private void importFinishedScan(JSONObject value) {
        if (importing) return;
        importing = true; setRunning(true); status.setText("Guardando y verificando cantidades…");
        boolean started = com.example.zoneimport.ZoneAutoImport.start(this, value.toString(), ZoneUrl.friendId(profileUrl),
            (ok, result) -> {
                importing = false;
                if (isDestroyed()) return;
                setRunning(false); status.setText(result);
                if (ok) { message(result); finish(); }
            });
        if (!started) { importing = false; setRunning(false); status.setText("Ya hay una importación en curso. Espera a que termine."); }
    }

    private void syncAndRead() {
        if (importing || syncPhase != 0 || cancelScan != null) return;
        if (browser == null || profileUrl == null) { message("Abre primero tu perfil para reconocer la cuenta."); return; }
        cancelSync("Se reinició la sincronización.");
        if (cancelScan != null) cancelScan.run();
        requestWorkNotifications();
        autoStart = false; syncSucceeded = false; setRunning(true);
        syncBrowser = browser; syncProfile = profileUrl;
        syncPhase = 1; syncEvaluationPending = false; syncFlowId++;
        syncDeadline = android.os.SystemClock.elapsedRealtime() + 120000;
        final int token = syncFlowId;
        try {
            ScanService.begin(this, () -> {
                if (token != syncFlowId || syncBrowser != browser) return;
                status.setText("Abriendo tu perfil para sincronizar…");
                handler.removeCallbacks(syncPoll); handler.post(syncPoll);
                syncBrowser.loadUrl(syncProfile);
            }, () -> cancelSync("Sincronización detenida; la colección no se ha leído."));
        } catch (RuntimeException e) { cancelSync("No se pudo iniciar la sincronización. Inténtalo con la app abierta."); }
    }

    private void cancelSync(String reason) {
        if (syncPhase == 0) return;
        syncPhase = 0; syncFlowId++; syncBrowser = null; syncEvaluationPending = false;
        handler.removeCallbacks(syncPoll); ScanService.end(this);
        syncSucceeded = false; setRunning(false);
        if (status != null) status.setText(reason);
        trace.add("read_error", null, 0); saveTrace();
    }

    private final Runnable syncPoll = new Runnable() {
        @Override public void run() {
            if (syncPhase == 0 || syncBrowser != browser) return;
            if (android.os.SystemClock.elapsedRealtime() >= syncDeadline) { cancelSync("No se confirmó el final de Sync en dos minutos. La colección no se ha leído."); return; }
            if (syncPhase == 2 && !syncEvaluationPending) {
                if (!syncProfile.equals(browser.getUrl())) { cancelSync("La página cambió durante Sync."); return; }
                syncEvaluationPending = true;
                final int token = syncFlowId;
                try {
                    syncBrowser.evaluateJavascript(asset("sync-probe.js") + ";" + asset("sync-control.js"), result -> {
                        if (token != syncFlowId || syncPhase != 2 || syncBrowser != browser) return;
                        syncEvaluationPending = false;
                        if ("\"success\"".equals(result)) {
                            syncSucceeded = true; syncPhase = 3;
                            status.setText("Sincronización confirmada. Abriendo cartas…");
                            syncBrowser.loadUrl(syncProfile + "cards/");
                        } else if ("\"failed\"".equals(result) || "\"blocked\"".equals(result)) {
                            cancelSync("Sync no confirmó éxito. La colección no se ha leído.");
                        }
                    });
                } catch (Exception e) { cancelSync("No se pudo comprobar el estado de Sync."); return; }
            }
            if (syncPhase == 4 && !syncEvaluationPending) {
                syncEvaluationPending = true;
                final int token = syncFlowId;
                try {
                    syncBrowser.evaluateJavascript(asset("cards-ready.js"), result -> {
                        if (token != syncFlowId || syncPhase != 4 || syncBrowser != browser) return;
                        syncEvaluationPending = false;
                        if ("\"ready\"".equals(result)) {
                            syncPhase = 0; syncFlowId++; syncBrowser = null;
                            handler.removeCallbacks(syncPoll);
                            readProgressively();
                        } else if ("\"blocked\"".equals(result)) cancelSync("No se pudo abrir la colección después de Sync.");
                    });
                } catch (Exception e) { cancelSync("No se pudo comprobar la carga de cartas."); return; }
            }
            if (syncPhase != 0) handler.postDelayed(this, 500);
        }
    };

    private void readProgressively() {
        cancelSync("Se inició un recorrido manual.");
        if (browser != null) { adoptProfile(browser.getUrl()); loaded = !pageFailed && ZoneUrl.belongsTo(browser.getUrl(), profileUrl); }
        if (browser == null || !loaded || profileUrl == null || !ZoneUrl.belongsTo(browser.getUrl(), profileUrl)
                || !browser.getUrl().endsWith("/cards/")) {
            message("Abre Ver cartas y espera a que cargue antes de recorrer."); return;
        }
        requestWorkNotifications();
        if (cancelScan != null) cancelScan.run();
        final WebView source = browser;
        final int token = ++generation;
        final java.util.LinkedHashMap<String, JSONObject> accumulated = new java.util.LinkedHashMap<>();
        final long hardDeadline = android.os.SystemClock.elapsedRealtime() + 900000;
        final String script, advance;
        try { script = asset("scan-observer.js") + ";" + asset("extract.js").replace("})();", "})(true);"); advance = asset("advance-cards.js"); } catch (Exception e) { ScanService.end(this); message("No se pudo iniciar el recorrido."); return; }
        readButton.setEnabled(false);
        trace.add("read_start", source.getUrl(), 0);
        source.evaluateJavascript("window.__pocketZoneScan?.observer.disconnect();delete window.__pocketZoneScan;", null);
        Runnable worker = new Runnable() {
            int stable, rounds;
            final Runnable stop = () -> finish("Detenido por el usuario; avance conservado.");
            private void snapshot(String reason) throws Exception {
                if (last == null) return;
                JSONObject value = new JSONObject(last.toString());
                value.put("visibleCards", new JSONArray(accumulated.values()));
                value.put("readAt", Instant.now().toString());
                value.put("collectionComplete", false);
                value.put("scanReason", reason); value.put("scanRounds", rounds);
                preview = value; savePreviewSnapshot();
            }
            long progressDeadline = android.os.SystemClock.elapsedRealtime() + 180000;
            JSONObject last;
            private boolean active() {
                if (token != generation || browser != source) return false;
                if (ZoneUrl.belongsTo(source.getUrl(), profileUrl)) return true;
                generation++; cancelScan = null; checkpointScan = null; syncSucceeded = false;
                ScanService.end(ZoneSyncActivity.this); setRunning(false);
                status.setText("La página cambió durante el recorrido. No se importó nada.");
                return false;
            }
            private void finish(String reason) { finish(reason, false); }
            private void finish(String reason, boolean completed) {
                if (!active()) return;
                generation++; cancelScan = null; checkpointScan = null; ScanService.end(ZoneSyncActivity.this); readButton.setEnabled(loaded);
                source.evaluateJavascript("window.__pocketZoneScan?.observer.disconnect();delete window.__pocketZoneScan;", null);
                try {
                    if (last == null) { setRunning(false); status.setText("No se obtuvieron cartas. " + reason); return; }
                    snapshot(reason);
                    trace.add("read_result", source.getUrl(), 0);
                    status.setText("Recorrido terminado: " + accumulated.size() + " cartas distintas. " + reason + " No se garantiza una colección completa.");
                    if (completed && syncSucceeded) {
                        preview.put("syncConfirmed", true); preview.put("scanCompleted", true);
                        importFinishedScan(preview);
                    } else {
                        setRunning(false);
                        status.setText(reason + " No se importó nada; las cantidades guardadas se conservan.");
                    }
                } catch (Exception e) { setRunning(false); status.setText("No se pudo preparar el resultado. No se importó nada."); }
            }
            @Override public void run() {
                if (!active()) return;
                cancelScan = stop;
                checkpointScan = () -> { if (active()) try { snapshot("Avance guardado; recorrido todavía incompleto."); } catch (Exception ignored) { } };
                long now = android.os.SystemClock.elapsedRealtime();
                if (now >= hardDeadline) { finish("Límite de seguridad de quince minutos."); return; }
                if (now >= progressDeadline) { finish("Tres minutos sin nuevas cartas."); return; }
                source.evaluateJavascript(script, result -> {
                    if (!active()) return;
                    try {
                        if (result == null || result.length() > Preview.MAX_BYTES * 2) throw new IllegalArgumentException();
                        Object decoded = new JSONTokener(result).nextValue();
                        if (!(decoded instanceof String)) throw new IllegalArgumentException();
                        JSONObject raw = new JSONObject((String) decoded);
                        if (raw.has("error")) {
                            stable = 0;
                            if (!"loading".equals(raw.optString("error"))) { finish("Página no disponible."); return; }
                        } else {
                            last = Preview.validate((String) decoded, profileUrl);
                            int before = accumulated.size();
                            JSONArray cards = last.getJSONArray("visibleCards");
                            for (int i = 0; i < cards.length(); i++) {
                                JSONObject card = cards.getJSONObject(i);
                                String key = card.getString("cardPath");
                                if (accumulated.containsKey(key) || accumulated.size() < 5000) accumulated.put(key, card);
                            }
                            stable = accumulated.size() == before ? stable + 1 : 0;
                            if (accumulated.size() > before) progressDeadline = android.os.SystemClock.elapsedRealtime() + 180000;
                            if (accumulated.size() >= 5000) { finish("Límite de seguridad de 5000 cartas."); return; }
                        }
                        rounds++;
                        status.setText("Recorriendo cartas: " + accumulated.size() + " distintas · paso " + rounds + ".");
                        ScanService.progress(accumulated.size());
                        source.evaluateJavascript(advance, action -> {
                            if (!active()) return;
                            if ("\"load\"".equals(action) || "\"waiting\"".equals(action)) stable = 0;
                            else if ("\"load-timeout\"".equals(action)) { finish("La página no cargó nuevas cartas en veinte segundos; avance conservado."); return; }
                            else if ("\"blocked\"".equals(action)) { finish("Página no disponible."); return; }
                            if (stable >= 12) { finish("Sin nuevas cartas durante doce lecturas; puede haber filtros o cargas pendientes.", true); return; }
                            handler.postDelayed(this, "\"waiting\"".equals(action) ? 500 : stable == 0 ? 100 : 1000);
                        });
                    } catch (Exception e) { finish("Lectura interrumpida."); }
                });
            }
        };
        try { ScanService.begin(this, worker, () -> { if (cancelScan != null) cancelScan.run(); }); }
        catch (RuntimeException e) { readButton.setEnabled(loaded); status.setText("No se pudo iniciar el recorrido en segundo plano. Inténtalo con la app abierta."); }
    }

    private void readPage() {
        cancelSync("Se inició una lectura manual.");
        if (browser == null || !loaded || !ZoneUrl.belongsTo(browser.getUrl(), profileUrl)) return;
        final WebView source = browser;
        if (cancelScan != null) cancelScan.run();
        final int token = ++generation;
        readButton.setEnabled(false);
        status.setText("Leyendo elementos visibles…");
        trace.add("read_start", source.getUrl(), 0);
        Runnable timeout = () -> {
            if (token != generation || browser != source) return;
            generation++; readButton.setEnabled(loaded);
            trace.add("read_error", source.getUrl(), 0);
            status.setText("La lectura agotó su tiempo. Puedes intentarlo otra vez.");
        };
        handler.postDelayed(timeout, 8000);
        try {
            String script;
            try (java.io.InputStream stream = getAssets().open("extract.js")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                script = bytes.toString(StandardCharsets.UTF_8.name());
            }
            source.evaluateJavascript(script, (ValueCallback<String>) result -> {
                if (token != generation || browser != source || !ZoneUrl.belongsTo(source.getUrl(), profileUrl)) return;
                handler.removeCallbacks(timeout); readButton.setEnabled(loaded);
                try {
                    if (result == null || result.length() > Preview.MAX_BYTES * 2) throw new IllegalArgumentException();
                    Object decoded = new JSONTokener(result).nextValue();
                    if (!(decoded instanceof String)) throw new IllegalArgumentException();
                    String json = (String) decoded;
                    JSONObject raw = new JSONObject(json);
                    String error = raw.optString("error");
                    if (!error.isEmpty()) {
                        status.setText("loading".equals(error) ? "Los datos siguen cargando. Espera y pulsa Leer de nuevo." : "El sitio no permite leer este perfil en este momento.");
                        return;
                    }
                    JSONObject clean = Preview.validate(json, profileUrl);
                    String summary = clean.optString("visibleSummary");
                    if (summary.isEmpty() && clean.getJSONArray("visibleCards").length() == 0) {
                        status.setText("No se encontraron datos visibles. El perfil puede ser privado o haber cambiado su diseño.");
                        return;
                    }
                    clean.put("readAt", Instant.now().toString());
                    preview = clean;
                    trace.add("read_result", source.getUrl(), 0);
                    status.setText("Vista previa lista. No se ha importado nada.");
                    showPreview();
                } catch (Exception e) {
                    trace.add("read_error", source.getUrl(), 0);
                    status.setText("No se pudo validar la lectura. No se ha guardado ni importado nada.");
                }
            });
        } catch (Exception e) {
            handler.removeCallbacks(timeout); readButton.setEnabled(loaded);
            status.setText("No se pudo iniciar la lectura.");
        }
    }

    private void showPreview() throws Exception {
        JSONObject current = preview;
        if (current == null) return;
        LinearLayout content = column();
        content.addView(label("ID: " + current.getString("friendId"), 16));
        content.addView(label("Encabezado visible: " + current.optString("visibleHeading", ""), 17));
        content.addView(label("Título de página: " + current.optString("pageTitle"), 14));
        content.addView(label("Leído: " + current.optString("readAt"), 13));
        content.addView(label("Datos visibles, sin interpretar como estadísticas del jugador", 16));
        JSONArray fields = current.getJSONArray("visibleFields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            content.addView(label(field.getString("label") + ": " + field.getString("value"), 15));
        }
        JSONArray cards = current.getJSONArray("visibleCards");
        content.addView(label("Filas de cartas cargadas: " + cards.length() + " · Colección incompleta", 16));
        for (int i = 0; i < Math.min(cards.length(), 20); i++) {
            JSONObject card = cards.getJSONObject(i);
            content.addView(label(card.optString("name") + " × " + card.getInt("quantity"), 14));
        }
        content.addView(label("Texto visible de referencia", 16));
        TextView summary = label(current.optString("visibleSummary"), 14);
        summary.setTextIsSelectable(true); content.addView(summary);
        content.addView(label("Este JSON es de diagnóstico. No es un respaldo compatible para restaurar en TCG Pocket.", 15));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("Vista previa · Sin importar")
            .setView(scroll).setNegativeButton("Cerrar", null)
            .setPositiveButton("Guardar JSON", (dialog, which) -> {
                try {
                    pendingExport = current.toString(2);
                    preferences.edit().putString("pending-export", pendingExport).commit();
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, "zone-vista-previa-" + current.optString("friendId") + ".json");
                    startActivityForResult(intent, EXPORT_JSON);
                } catch (Exception e) { pendingExport = null; message("No se pudo abrir el selector de archivos."); }
            }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_JSON && requestCode != EXPORT_TRACE) return;
        String text = pendingExport != null ? pendingExport : preferences.getString("pending-export", null);
        pendingExport = null;
        if (resultCode != RESULT_OK) { preferences.edit().remove("pending-export").apply(); return; }
        if (data == null || data.getData() == null || text == null || text.isEmpty()) {
            message("No se pudo recuperar el registro. Vuelve a pulsar Guardar diagnóstico."); return;
        }
        boolean written = false;
        try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) throw new java.io.IOException();
            if (requestCode == EXPORT_TRACE) {
                try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
                    zip.putNextEntry(new java.util.zip.ZipEntry("registro.json"));
                    zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                    zip.putNextEntry(new java.util.zip.ZipEntry("resumen.txt"));
                    String summary = "Pocket Zone · Pruebas " + versionName() + "\nRegistro de navegación y peticiones, rutas anonimizadas.\nSin cuerpos, cabeceras, contraseñas, cookies, tokens ni parámetros.\nNo registra todos los códigos HTTP de éxito ni actividad fuera de esta app.\n";
                    zip.write(summary.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                }
                written = true;
            } else { output.write(text.getBytes(StandardCharsets.UTF_8)); output.flush(); written = true; }
        } catch (Exception e) { message("No se pudo guardar el archivo. Prueba otra carpeta."); return; }
        if (written) {
            try (java.io.InputStream check = getContentResolver().openInputStream(data.getData())) {
                if (check == null) throw new java.io.IOException();
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = check.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > 1000000) throw new java.io.IOException();
                }
                if (requestCode == EXPORT_TRACE) {
                    try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                        java.util.zip.ZipEntry entry = zip.getNextEntry();
                        if (entry == null || !"registro.json".equals(entry.getName())) throw new java.io.IOException();
                        java.io.ByteArrayOutputStream payload = new java.io.ByteArrayOutputStream();
                        while ((count = zip.read(buffer)) != -1) payload.write(buffer, 0, count);
                        if (!text.equals(payload.toString(StandardCharsets.UTF_8.name()))) throw new java.io.IOException();
                        zip.closeEntry();
                        entry = zip.getNextEntry();
                        if (entry == null || !"resumen.txt".equals(entry.getName()) || zip.read() == -1) throw new java.io.IOException();
                    }
                } else if (!text.equals(bytes.toString(StandardCharsets.UTF_8.name()))) throw new java.io.IOException();
                preferences.edit().remove("pending-export").apply();
                message("Archivo guardado y verificado: " + bytes.size() + " bytes.");
            } catch (Exception e) { message("No se pudo verificar el archivo guardado. Prueba guardarlo en Descargas."); }
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("profile", profileUrl);
        // Export payload is persisted privately; avoid oversized Android state bundles.
    }

    @Override public void onBackPressed() {
        if (importing) { message("Terminando el guardado…"); return; }
        if (syncPhase != 0 || cancelScan != null) {
            new AlertDialog.Builder(this).setTitle("¿Cancelar la sincronización?")
                .setNegativeButton("Continuar", null).setPositiveButton("Cancelar", (d, w) -> {
                    autoStart = false; cancelSync("Cancelado. La colección guardada se conserva.");
                    if (cancelScan != null) cancelScan.run(); finish();
                }).show(); return;
        }
        if (browser != null) {
            if (browser.canGoBack()) browser.goBack(); else home();
        } else new AlertDialog.Builder(this).setTitle("¿Salir de Pocket Zone?")
            .setNegativeButton("Continuar", null).setPositiveButton("Salir", (dialog, which) -> finish()).show();
    }

    private void destroyBrowser() {
        cancelSync("Sincronización interrumpida al cerrar el visor.");
        if (checkpointScan != null) checkpointScan.run();
        cancelScan = null; checkpointScan = null;
        ScanService.end(this);
        generation++; loaded = false; handler.removeCallbacksAndMessages(null);
        if (browser != null) {
            browser.stopLoading();
            browser.setWebViewClient(new WebViewClient());
            if (browser.getParent() instanceof ViewGroup) ((ViewGroup) browser.getParent()).removeView(browser);
            browser.destroy(); browser = null;
        }
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        trace.add(configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ? "orientation_landscape" : "orientation_portrait", null, 0);
        saveTrace();
        // Keep the same WebView, DOM observer and native scan when the screen rotates.
        if (browser != null) { browser.requestLayout(); browser.invalidate(); }
    }

    @Override protected void onDestroy() { destroyBrowser(); super.onDestroy(); }
    @Override protected void onStop() { resumed = false; trace.add("app_background", null, 0); if (cancelScan == null && syncPhase == 0) handler.removeCallbacks(probeLoop); if (checkpointScan != null) checkpointScan.run(); saveTrace(); super.onStop(); }
    @Override protected void onResume() {
        super.onResume(); resumed = true; trace.add("app_foreground", null, 0);
        if (pendingScanResult) { pendingScanResult = false; try { showPreview(); } catch (Exception e) { message("El resultado está guardado; vuelve a abrir la vista previa."); } }
        if (browser != null && trace.isRecording()) { handler.removeCallbacks(probeLoop); handler.post(probeLoop); }
    }

    private void addTraceControls(LinearLayout content) {
        recordButton = button("Reiniciar diagnóstico", () -> {
            trace.start();
            if (browser != null) {
                browser.evaluateJavascript("window.__pocketZoneProbeActive=true;window.__pocketZoneProbe?.drain();", null);
                handler.removeCallbacks(probeLoop); handler.post(probeLoop);
            }
            saveTrace(); message("Diagnóstico reiniciado. Se registra automáticamente al abrir el visor.");
        });
        content.addView(recordButton);
        content.addView(button("Guardar diagnóstico ZIP", () -> {
            saveTrace(); pendingExport = trace.exportJson();
            preferences.edit().putString("pending-export", pendingExport).commit();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "zone-proceso.zip");
            try { startActivityForResult(intent, EXPORT_TRACE); }
            catch (Exception e) { pendingExport = null; message("No se pudo abrir el selector de archivos."); }
        }));
    }

    private void connectInBrowser() {
        new AlertDialog.Builder(this).setTitle("Conectar Google y Nintendo")
            .setMessage("Se abrirá Mi cuenta de Pokémon Zone en tu navegador. Allí puedes entrar con Google y seguir Sync your data para vincular Nintendo o actualizar los datos. Al terminar, vuelve a esta app y abre tu perfil para leer las cartas publicadas. El registro de esta prueba no captura los pasos que realices en el navegador ni transfiere su sesión al visor.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Abrir Mi cuenta", (dialog, which) -> {
                try {
                    trace.add("account_control", "https://www.pokemon-zone.com/settings/", 0);
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pokemon-zone.com/settings/")));
                } catch (android.content.ActivityNotFoundException e) { message("No se encontró un navegador para abrir Pokémon Zone."); }
            }).show();
    }

    private void saveTrace() {
        if (traceFile == null) return;
        java.io.FileOutputStream stream = null;
        try {
            stream = traceFile.startWrite();
            stream.write(trace.exportJson().getBytes(StandardCharsets.UTF_8));
            traceFile.finishWrite(stream);
        } catch (Exception e) { if (stream != null) traceFile.failWrite(stream); }
    }

    private String asset(String name) throws Exception {
        try (java.io.InputStream input = getAssets().open("zone/" + name)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
    private void setRoot(View view) {
        int left = view.getPaddingLeft(), top = view.getPaddingTop();
        int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(left + insets.getSystemWindowInsetLeft(), top + insets.getSystemWindowInsetTop(),
                right + insets.getSystemWindowInsetRight(), bottom + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(view);
        view.requestApplyInsets();
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
