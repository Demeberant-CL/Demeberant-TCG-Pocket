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
    private static final int BG = 0xFFF1ECE4, TEXT = 0xFF25211C, ACCENT = 0xFFA84400;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView browser;
    private TextView status;
    private Button readButton;
    private String profileUrl;
    private JSONObject preview;
    private String pendingExport;
    private int generation;
    private boolean loaded;
    private boolean pageFailed;
    private SharedPreferences preferences;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        preferences = getSharedPreferences("zone-experiment", MODE_PRIVATE);
        if (saved != null) {
            profileUrl = ZoneUrl.normalize(saved.getString("profile"));
            pendingExport = saved.getString("pendingExport");
        }
        home();
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
        content.addView(label("Primero actualiza tus datos en Pokémon Zone desde tu navegador. Aquí solo se lee lo publicado; no se sincroniza con Nintendo ni con el juego.", 16));
        content.addView(label("Los perfiles privados y el acceso con Google, Discord o Nintendo no están incluidos en esta prueba. Las imágenes no se descargan para reducir consumo.", 15));
        content.addView(button("Borrar datos de esta prueba", () -> new AlertDialog.Builder(this)
            .setTitle("¿Borrar los datos de Pocket Zone?")
            .setMessage("Se elimina el enlace guardado y la sesión web de esta app de pruebas. TCG Pocket conserva sus datos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Borrar", (dialog, which) -> {
                preferences.edit().clear().apply();
                profileUrl = null; preview = null; pendingExport = null;
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
        content.addView(label("Pokémon Zone · Lectura", 20));
        status = label("Cargando perfil…", 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        HorizontalScrollView controls = new HorizontalScrollView(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(row);
        readButton = button("Leer datos visibles", this::readPage);
        readButton.setEnabled(false);
        row.addView(readButton);
        row.addView(button("Ver cartas", () -> { if (browser != null) browser.loadUrl(profileUrl + "cards/"); }));
        row.addView(button("Perfil", () -> { if (browser != null) browser.loadUrl(profileUrl); }));
        row.addView(button("Volver", this::home));
        row.addView(button("Navegador externo", () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))));
        content.addView(controls);
        content.addView(label("Solo se leen elementos cargados. Una colección parcial nunca se considera completa.", 13));
        browser = new WebView(this);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkImage(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                if (ZoneUrl.belongsTo(request.getUrl().toString(), profileUrl)) return false;
                loaded = false; readButton.setEnabled(false);
                status.setText("Enlace fuera del perfil bloqueado. Usa el navegador externo para acceder a tu cuenta.");
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                boolean blocked = !"https".equalsIgnoreCase(uri.getScheme())
                    || (request.isForMainFrame() && !ZoneUrl.belongsTo(uri.toString(), profileUrl));
                if (blocked) return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                return null;
            }

            @Override public void onPageStarted(WebView view, String pageUrl, android.graphics.Bitmap icon) {
                generation++; loaded = false; pageFailed = false; readButton.setEnabled(false);
                status.setText("Cargando… Espera a que aparezcan los datos del perfil.");
            }

            @Override public void onPageFinished(WebView view, String pageUrl) {
                loaded = !pageFailed && ZoneUrl.belongsTo(pageUrl, profileUrl);
                readButton.setEnabled(loaded);
                status.setText(loaded ? "Página cargada. Si muestra «Loading», espera antes de leer." : "No se puede leer esta página.");
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    loaded = false; pageFailed = true; generation++; readButton.setEnabled(false);
                    status.setText("No se pudo cargar el perfil. Comprueba la conexión y vuelve a abrirlo.");
                }
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
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

    private void readPage() {
        if (browser == null || !loaded || !ZoneUrl.belongsTo(browser.getUrl(), profileUrl)) return;
        final WebView source = browser;
        final int token = ++generation;
        readButton.setEnabled(false);
        status.setText("Leyendo elementos visibles…");
        Runnable timeout = () -> {
            if (token != generation || browser != source) return;
            generation++; readButton.setEnabled(loaded);
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
                    status.setText("Vista previa lista. No se ha importado nada.");
                    showPreview();
                } catch (Exception e) {
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
        if (requestCode != EXPORT_JSON) return;
        String text = pendingExport; pendingExport = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || text == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) throw new java.io.IOException();
            output.write(text.getBytes(StandardCharsets.UTF_8));
            message("JSON guardado. La colección de TCG Pocket no se ha modificado.");
        } catch (Exception e) { message("No se pudo guardar el JSON."); }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("profile", profileUrl);
        out.putString("pendingExport", pendingExport);
    }

    @Override public void onBackPressed() {
        if (browser != null) {
            if (browser.canGoBack()) browser.goBack(); else home();
        } else new AlertDialog.Builder(this).setTitle("¿Salir de Pocket Zone?")
            .setNegativeButton("Continuar", null).setPositiveButton("Salir", (dialog, which) -> finish()).show();
    }

    private void destroyBrowser() {
        generation++; loaded = false; handler.removeCallbacksAndMessages(null);
        if (browser != null) {
            browser.stopLoading();
            browser.setWebViewClient(new WebViewClient());
            if (browser.getParent() instanceof ViewGroup) ((ViewGroup) browser.getParent()).removeView(browser);
            browser.destroy(); browser = null;
        }
    }

    @Override protected void onDestroy() { destroyBrowser(); super.onDestroy(); }
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
