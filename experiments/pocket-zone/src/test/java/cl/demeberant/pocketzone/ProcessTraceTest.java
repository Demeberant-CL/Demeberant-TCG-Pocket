package cl.demeberant.pocketzone;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;

public class ProcessTraceTest {
    @Test public void redactsIdsQueryTokensAndCredentials() {
        assertEquals("zone/players/{hidden}/api/sync", ProcessTrace.safeRoute("https://www.pokemon-zone.com/players/9824549574576397/api/sync?token=secret#password"));
        assertEquals("external", ProcessTrace.safeRoute("https://user:password@www.pokemon-zone.com/settings/"));
    }
    @Test public void externalDomainsAndUnknownPathsNeverLeak() {
        assertEquals("external", ProcessTrace.safeRoute("https://accounts.google.com/token/secret"));
        assertEquals("zone/{hidden}/{hidden}", ProcessTrace.safeRoute("https://www.pokemon-zone.com/private-name/my-secret/"));
    }
    @Test public void noEventsRecordedUntilStartedOrAfterStop() throws Exception {
        ProcessTrace log = new ProcessTrace();
        log.add("request", "https://www.pokemon-zone.com/settings/", 0);
        assertEquals(0, new JSONObject(log.exportJson()).getJSONArray("events").length());
        log.start(); log.stop(); log.add("request", "https://www.pokemon-zone.com/", 0);
        assertEquals(2, new JSONObject(log.exportJson()).getJSONArray("events").length());
    }
    @Test public void rejectsArbitraryEventStrings() {
        ProcessTrace log = new ProcessTrace(); log.start();
        log.add("password=secret", "https://www.pokemon-zone.com/settings/", 200);
        assertFalse(log.exportJson().contains("secret"));
    }
    @Test public void capsEventsAndReportsOmissions() throws Exception {
        ProcessTrace log = new ProcessTrace(); log.start();
        for (int i = 0; i < 1000; i++) log.add("request", "https://www.pokemon-zone.com/api/sync?token=secret", 0);
        JSONObject data = new JSONObject(log.exportJson());
        assertEquals(2, data.getJSONArray("events").length());
        assertEquals(1000, data.getJSONArray("events").getJSONObject(1).getInt("count"));
        assertEquals(0, data.getInt("omittedEvents"));
        assertFalse(data.toString().contains("secret"));
    }
    @Test public void restorationKeepsSanitizedDataAndDoesNotStartRecording() {
        ProcessTrace source = new ProcessTrace(); source.start(); source.add("http_error", "https://www.pokemon-zone.com/api/sync", 403);
        ProcessTrace restored = new ProcessTrace(); restored.restore(source.exportJson());
        assertFalse(restored.isRecording());
        assertTrue(restored.exportJson().contains("403"));
    }
    @Test public void rejectsUnsafeRestoredRoutes() throws Exception {
        ProcessTrace log = new ProcessTrace();
        log.restore("{\"kind\":\"zone-process-diagnostic\",\"events\":[{\"time\":\"2026-10-05T00:00:00Z\",\"event\":\"request\",\"route\":\"zone/passwordsecret\",\"token\":\"secret\"}]}");
        assertFalse(log.exportJson().contains("secret"));
    }
    @Test public void permitsOnlyHttpsZoneNavigation() {
        assertTrue(ZoneUrl.canBrowse("https://www.pokemon-zone.com/accounts/login/?next=/settings/"));
        assertFalse(ZoneUrl.canBrowse("http://www.pokemon-zone.com/settings/"));
        assertFalse(ZoneUrl.canBrowse("https://www.pokemon-zone.com.evil.test/settings/"));
    }
    @Test public void externalFloodDoesNotEraseSyncOrStart() throws Exception {
        ProcessTrace log = new ProcessTrace(); log.start();
        log.add("sync_control", "https://www.pokemon-zone.com/settings/", 0);
        for (int i = 0; i < 10000; i++) log.add("request", "https://example.com/secret", 0, "GET");
        log.stop(); JSONObject data = new JSONObject(log.exportJson());
        assertEquals(3, data.getJSONArray("events").length());
        assertEquals(10000, data.getInt("filteredRequests"));
        assertEquals("start", data.getJSONArray("events").getJSONObject(0).getString("event"));
        assertEquals("sync_control", data.getJSONArray("events").getJSONObject(1).getString("event"));
        assertFalse(data.toString().contains("secret"));
    }
    @Test public void criticalEventsEvictRequestsBeforeStart() throws Exception {
        ProcessTrace log = new ProcessTrace(); log.start();
        log.add("request", "https://www.pokemon-zone.com/api/cards", 0, "GET");
        for (int i = 0; i < 500; i++) log.add("page_end", "https://www.pokemon-zone.com/settings/", 0);
        log.stop(); JSONObject data = new JSONObject(log.exportJson());
        assertEquals(400, data.getJSONArray("events").length());
        assertEquals("start", data.getJSONArray("events").getJSONObject(0).getString("event"));
        assertEquals("stop", data.getJSONArray("events").getJSONObject(399).getString("event"));
        assertFalse(data.toString().contains("\"request\""));
    }

}
