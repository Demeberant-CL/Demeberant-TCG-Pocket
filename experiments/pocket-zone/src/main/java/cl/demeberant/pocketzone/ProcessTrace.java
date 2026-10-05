package cl.demeberant.pocketzone;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded metadata only. Never accepts page text, headers, request bodies or raw URLs. */
public final class ProcessTrace {
    private static final Set<String> SEGMENTS = Set.of("api", "ajax", "players", "profile", "collection", "cards", "sync", "synchronize", "refresh", "settings", "accounts", "account", "login", "logout", "connect", "nintendo", "status", "data", "v1", "v2", "static", "assets", "js", "css", "images", "pocket", "tcgp", "import", "auth", "callback");
    private static final Set<String> EVENTS = Set.of("start", "stop", "page_start", "page_end", "request", "http_error", "network_error", "blocked_link", "read_start", "read_result", "read_error", "page_probe", "sync_control", "account_control", "load_control");
    private final ArrayDeque<JSONObject> events = new ArrayDeque<>();
    private int omitted;
    private int filteredRequests;
    private final java.util.LinkedHashMap<String, JSONObject> requests = new java.util.LinkedHashMap<>();
    private boolean recording;

    public synchronized void start() { events.clear(); requests.clear(); omitted = 0; filteredRequests = 0; recording = true; add("start", null, 0); }
    public synchronized void stop() { add("stop", null, 0); recording = false; }
    public synchronized boolean isRecording() { return recording; }

    public synchronized void add(String event, String url, int code) {
        add(event, url, code, null);
    }

    public synchronized void add(String event, String url, int code, String method) {
        if (!recording || !EVENTS.contains(event)) return;
        try {
            String route = url == null ? null : safeRoute(url);
            String safeMethod = method != null && Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(method) ? method : "";
            String now = Instant.now().toString();
            String key = route + ":" + safeMethod;
            if ("request".equals(event)) {
                if (route == null || !route.startsWith("zone")) { filteredRequests++; return; }
                JSONObject previous = requests.get(key);
                if (previous != null) {
                    previous.put("count", previous.optInt("count", 1) + 1).put("lastTime", now); return;
                }
                if (requests.size() >= 100) { filteredRequests++; return; }
            }
            JSONObject row = new JSONObject().put("time", now).put("event", event);
            if ("request".equals(event)) row.put("count", 1);
            if (url != null) row.put("route", safeRoute(url));
            if (method != null && Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(method)) row.put("method", method);
            if (code != 0 && code >= -100 && code <= 599) row.put("code", code);
            if (events.size() == 400) {
                JSONObject victim = null;
                for (JSONObject candidate : events) if ("request".equals(candidate.optString("event"))) { victim = candidate; break; }
                if (victim == null) for (JSONObject candidate : events) if (!"start".equals(candidate.optString("event"))) { victim = candidate; break; }
                if (victim != null) { events.remove(victim); requests.values().remove(victim); }
                omitted++;
            }
            events.addLast(row);
            if ("request".equals(event)) requests.put(key, row);
        } catch (Exception ignored) { }
    }

    public synchronized String exportJson() {
        try {
            return new JSONObject().put("schemaVersion", 1).put("kind", "zone-process-diagnostic")
                .put("filteredRequests", filteredRequests).put("recording", recording).put("omittedEvents", omitted).put("events", new JSONArray(events))
                .put("notice", "Metadatos limitados; sin URLs originales, parámetros, cookies, tokens, formularios ni cuerpos. No es una captura completa de red.").toString(2);
        } catch (Exception e) { return "{}"; }
    }

    public synchronized void restore(String json) {
        // Restore only our own sanitized diagnostics; recording always needs a new user action.
        recording = false; events.clear(); requests.clear(); omitted = 0; filteredRequests = 0;
        try {
            if (json == null || json.length() > 160000) return;
            JSONObject root = new JSONObject(json);
            if (!"zone-process-diagnostic".equals(root.optString("kind"))) return;
            JSONArray rows = root.optJSONArray("events");
            if (rows == null) return;
            omitted = Math.max(0, root.optInt("omittedEvents"));
            filteredRequests = Math.max(0, root.optInt("filteredRequests"));
            for (int i = Math.max(0, rows.length() - 400); i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || !EVENTS.contains(row.optString("event"))) continue;
                String time = row.optString("time");
                try { Instant.parse(time); } catch (Exception e) { continue; }
                JSONObject clean = new JSONObject().put("time", time).put("event", row.getString("event"));
                String route = row.optString("route");
                if (route.matches("(?:zone|external|invalid)(?:/(?:[a-z0-9]+|\\{hidden\\})){0,10}")) {
                    boolean safe = true;
                    for (String segment : route.split("/")) if (!Set.of("zone", "external", "invalid", "{hidden}").contains(segment) && !SEGMENTS.contains(segment)) safe = false;
                    if (safe) clean.put("route", route);
                }
                int code = row.optInt("code");
                String method = row.optString("method");
                if (Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(method)) clean.put("method", method);
                if (code != 0 && code >= -100 && code <= 599) clean.put("code", code);
                if ("request".equals(clean.optString("event"))) {
                    clean.put("count", Math.max(1, row.optInt("count", 1)));
                    try { String last = row.optString("lastTime"); Instant.parse(last); clean.put("lastTime", last); } catch (Exception ignored) { }
                }
                events.addLast(clean);
            }
        } catch (Exception ignored) { events.clear(); omitted = 0; }
    }

    public static String safeRoute(String raw) {
        try {
            URI uri = URI.create(raw);
            if (!ZoneUrl.isOrigin(uri)) return "external";
            StringBuilder result = new StringBuilder("zone");
            int count = 0;
            for (String segment : uri.getPath().split("/")) {
                if (segment.isEmpty()) continue;
                if (++count > 10) break;
                String lower = segment.toLowerCase(Locale.ROOT);
                result.append('/').append(SEGMENTS.contains(lower) ? lower : "{hidden}");
            }
            return result.toString();
        } catch (Exception e) { return "invalid"; }
    }
}
