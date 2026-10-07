package cl.demeberant.pocketzone;

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

public final class MainActivity extends Activity {
    private static final int EXPORT_JSON = 71;
    private static final int EXPORT_TRACE = 72;
    private static final int BG = 0xFFF1ECE4, TEXT = 0xFF25211C, ACCENT = 0xFFA84400;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView browser;
    private TextView status;
    private Button readButton;
    private String profileUrl;
    private JSONObject preview;
    private String pendingExport;
    private int generation;
    private Runnable cancelScan;
    private Runnable checkpointScan;
    private android.util.AtomicFile previewFile;
    private boolean loaded;
    private boolean pageFailed;
    private SharedPreferences preferences;
    private final ProcessTrace trace = new ProcessTrace();
    private android.util.AtomicFile traceFile;
    private Button recordButton;
    private final Runnable probeLoop = new Runnable() {
        @Override public void run() {
            WebView source = browser;
            if (source == null || !trace.isRecording() || !ZoneUrl.canBrowse(source.getUrl())) return;
            try {
                source.evaluateJavascript(asset("process-probe.js"), result -> {
                    if (browser != source || !trace.isRecording() || result == null || result.length() > 8000) return;
                    try {
                        Object decoded = new JSONTokener(result).nextValue();
                        if (!(decoded instanceof String)) return;
                        JSONArray events = new JSONArray((String) decoded);
                        for (int i = 0; i < Math.min(events.length(), 40); i++) {
                            String kind = events.optString(i);
                            if (java.util.Set.of("sync_control", "account_control", "load_control").contains(kind)) trace.add(kind, source.getUrl(), 0);
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
        home();
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
        scroll.setFillViewport(true);
        LinearLayout content = column();
        scroll.addView(content);
        content.addView(label("Pocket Zone · Pruebas", 25));
        content.addView(label("Perfil público · Vista previa", 19));
        content.addView(label("Esta app tiene datos propios. No importa ni modifica tu colección, mazos o conexiones de TCG Pocket.", 16));
        content.addView(label("Pega el enlace del perfil o su ID de amigo", 16));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(0xFF65594C);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("ID o https://www.pokemon-zone.com/players/…/");
        input.setText(profileUrl != null ? profileUrl : preferences.getString("profile", ""));
        content.addView(input);
        content.addView(button("Abrir perfil", () -> {
            String normalized = ZoneUrl.normalize(input.getText().toString());
            if (normalized == null) { message("Usa un ID de 10–20 números o un enlace de perfil HTTPS de Pokémon Zone."); return; }
            profileUrl = normalized;
            preferences.edit().putString("profile", profileUrl).apply();
            preview = null;
            openBrowser(profileUrl);
        }));
        content.addView(button("Conectar Google + Nintendo", this::connectInBrowser));
        content.addView(button("Acceso con contraseña en visor", () -> openBrowser("https://www.pokemon-zone.com/accounts/login/")));
        content.addView(label("Primero actualiza tus datos en Pokémon Zone desde tu navegador. Aquí solo se lee lo publicado; no se sincroniza con Nintendo ni con el juego.", 16));
        content.addView(label("Puedes probar el acceso con usuario y contraseña de Pokémon Zone desde Conectar cuenta. Google y Discord requieren navegador externo; su sesión no se transfiere a este visor.", 15));
        content.addView(label("Diagnóstico del proceso: activa el registro y navega por Pokémon Zone. Guarda pasos, rutas anonimizadas y errores; nunca guarda contraseñas, formularios, cookies o tokens.", 15));
        addTraceControls(content);
        content.addView(button("Borrar datos de esta prueba", () -> new AlertDialog.Builder(this)
            .setTitle("¿Borrar los datos de Pocket Zone?")
            .setMessage("Se elimina el enlace guardado y la sesión web de esta app de pruebas. TCG Pocket conserva sus datos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Borrar", (dialog, which) -> {
                preferences.edit().clear().apply();
                profileUrl = null; preview = null; pendingExport = null;
                trace.stop(); trace.restore("{}"); traceFile.delete();
                CookieManager.getInstance().removeAllCookies(value -> CookieManager.getInstance().flush());
                android.webkit.WebStorage.getInstance().deleteAllData();
                WebView cleaner = new WebView(this); cleaner.clearCache(true); cleaner.destroy();
                home();
            }).show()));
        content.addView(label("Versión " + versionName(), 13));
        setRoot(scroll);
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "0.1"; }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void openBrowser(String url) {
        destroyBrowser();
        LinearLayout content = column();
        
        status = label("Cargando perfil…", 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        HorizontalScrollView controls = new HorizontalScrollView(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(row);
        readButton = button("Leer datos visibles", this::readPage);
        readButton.setEnabled(false);
        row.addView(button("Google + Nintendo", this::connectInBrowser));
        row.addView(button("Mi cuenta", () -> { if (browser != null) browser.loadUrl("https://www.pokemon-zone.com/settings/"); }));
        row.addView(button("Acceso con contraseña", () -> { if (browser != null) browser.loadUrl("https://www.pokemon-zone.com/accounts/login/"); }));
        row.addView(readButton);
        row.addView(button("Recorrer cartas", this::readProgressively));
        row.addView(button("Detener recorrido", () -> { if (cancelScan != null) cancelScan.run(); }));
        row.addView(button("Ver cartas", () -> {
            if (browser == null) return;
            adoptProfile(browser.getUrl());
            if (profileUrl == null) { status.setText("En Mi cuenta pulsa View profile o Cards para reconocer tu perfil; después podrás usar Ver cartas."); return; }
            browser.loadUrl(profileUrl + "cards/");
        }));
        row.addView(button("Perfil", () -> { if (browser != null && profileUrl != null) browser.loadUrl(profileUrl); }));
        row.addView(button("Volver", this::home));
        row.addView(button("Navegador externo", () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))));
        content.addView(controls);
        HorizontalScrollView traceControls = new HorizontalScrollView(this);
        LinearLayout traceRow = new LinearLayout(this);
        traceRow.setOrientation(LinearLayout.HORIZONTAL);
        addTraceControls(traceRow);
        traceControls.addView(traceRow);
        content.addView(traceControls);
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
                    new AlertDialog.Builder(MainActivity.this).setTitle("Vincular Nintendo")
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
                new AlertDialog.Builder(MainActivity.this).setTitle("Inicio de sesión externo")
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
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                trace.add("network_error", request.getUrl().toString(), error.getErrorCode());
                if (request.isForMainFrame()) {
                    loaded = false; pageFailed = true; generation++; readButton.setEnabled(false);
                    status.setText("No se pudo cargar el perfil. Comprueba la conexión y vuelve a abrirlo.");
                }
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                trace.add("http_error", request.getUrl().toString(), response.getStatusCode());
                if (request.isForMainFrame()) {
                    loaded = false; pageFailed = true; generation++; readButton.setEnabled(false);
                    status.setText("El sitio respondió HTTP " + response.getStatusCode() + ". No se han leído datos.");
                }
            }
        });
        content.addView(browser, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
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

    private void readProgressively() {
        if (browser != null) { adoptProfile(browser.getUrl()); loaded = !pageFailed && ZoneUrl.belongsTo(browser.getUrl(), profileUrl); }
        if (browser == null || !loaded || profileUrl == null || !ZoneUrl.belongsTo(browser.getUrl(), profileUrl)
                || !browser.getUrl().endsWith("/cards/")) {
            message("Abre Ver cartas y espera a que cargue antes de recorrer."); return;
        }
        if (cancelScan != null) cancelScan.run();
        final WebView source = browser;
        final int token = ++generation;
        final java.util.LinkedHashMap<String, JSONObject> accumulated = new java.util.LinkedHashMap<>();
        final long hardDeadline = android.os.SystemClock.elapsedRealtime() + 900000;
        final String script, advance;
        try { script = asset("scan-observer.js") + ";" + asset("extract.js").replace("})();", "})(true);"); advance = asset("advance-cards.js"); } catch (Exception e) { message("No se pudo iniciar el recorrido."); return; }
        readButton.setEnabled(false);
        trace.add("read_start", source.getUrl(), 0);
        source.evaluateJavascript("window.__pocketZoneScan?.observer.disconnect();delete window.__pocketZoneScan;", null);
        new Runnable() {
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
            private boolean active() { return token == generation && browser == source && ZoneUrl.belongsTo(source.getUrl(), profileUrl); }
            private void finish(String reason) {
                if (!active()) return;
                generation++; cancelScan = null; checkpointScan = null; readButton.setEnabled(loaded);
                source.evaluateJavascript("window.__pocketZoneScan?.observer.disconnect();delete window.__pocketZoneScan;", null);
                try {
                    if (last == null) { status.setText("No se obtuvieron cartas. " + reason); return; }
                    snapshot(reason);
                    trace.add("read_result", source.getUrl(), 0);
                    status.setText("Recorrido terminado: " + accumulated.size() + " cartas distintas. " + reason + " No se garantiza una colección completa.");
                    showPreview();
                } catch (Exception e) { status.setText("No se pudo preparar el resultado del recorrido."); }
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
                        source.evaluateJavascript(advance, action -> {
                            if (!active()) return;
                            if ("\"load\"".equals(action) || "\"waiting\"".equals(action)) stable = 0;
                            else if ("\"load-timeout\"".equals(action)) { finish("La página no cargó nuevas cartas en veinte segundos; avance conservado."); return; }
                            else if ("\"blocked\"".equals(action)) { finish("Página no disponible."); return; }
                            if (stable >= 12) { finish("Sin nuevas cartas durante doce lecturas; puede haber filtros o cargas pendientes."); return; }
                            handler.postDelayed(this, "\"waiting\"".equals(action) ? 500 : stable == 0 ? 100 : 1000);
                        });
                    } catch (Exception e) { finish("Lectura interrumpida."); }
                });
            }
        }.run();
    }

    private void readPage() {
        if (browser == null || !loaded || !ZoneUrl.belongsTo(browser.getUrl(), profileUrl)) return;
        final WebView source = browser;
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
        if (browser != null) {
            if (browser.canGoBack()) browser.goBack(); else home();
        } else new AlertDialog.Builder(this).setTitle("¿Salir de Pocket Zone?")
            .setNegativeButton("Continuar", null).setPositiveButton("Salir", (dialog, which) -> finish()).show();
    }

    private void destroyBrowser() {
        if (checkpointScan != null) checkpointScan.run();
        cancelScan = null; checkpointScan = null;
        generation++; loaded = false; handler.removeCallbacksAndMessages(null);
        if (browser != null) {
            browser.stopLoading();
            browser.setWebViewClient(new WebViewClient());
            if (browser.getParent() instanceof ViewGroup) ((ViewGroup) browser.getParent()).removeView(browser);
            browser.destroy(); browser = null;
        }
    }

    @Override protected void onDestroy() { destroyBrowser(); super.onDestroy(); }
    @Override protected void onStop() { handler.removeCallbacks(probeLoop); if (checkpointScan != null) checkpointScan.run(); saveTrace(); super.onStop(); }
    @Override protected void onResume() {
        super.onResume();
        if (browser != null && trace.isRecording()) { handler.removeCallbacks(probeLoop); handler.post(probeLoop); }
    }

    private void addTraceControls(LinearLayout content) {
        recordButton = button(trace.isRecording() ? "Detener registro" : "Iniciar registro nuevo", () -> {
            if (trace.isRecording()) {
                trace.stop(); handler.removeCallbacks(probeLoop);
                if (browser != null) browser.evaluateJavascript("window.__pocketZoneProbeActive=false;window.__pocketZoneProbe?.drain();", null);
            } else {
                trace.start();
                if (browser != null) {
                    browser.evaluateJavascript("window.__pocketZoneProbeActive=true;window.__pocketZoneProbe?.drain();", null);
                    handler.post(probeLoop);
                }
            }
            recordButton.setText(trace.isRecording() ? "Detener registro" : "Iniciar registro nuevo");
            saveTrace();
            message(trace.isRecording() ? "Registro activo. Reproduce el proceso en esta app." : "Registro detenido.");
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
        try (java.io.InputStream input = getAssets().open(name)) {
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
