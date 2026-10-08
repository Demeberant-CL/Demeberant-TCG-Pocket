package com.example.zonebrowser;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = {34})
public class ZoneTest {
    private static final String ID = "3778164033299021";
    private static final String URL = "https://www.pokemon-zone.com/players/" + ID + "/";
    private JSONObject fixture() throws Exception {
        return new JSONObject().put("schemaVersion", 1).put("friendId", ID).put("url", URL)
            .put("pageTitle", "Título").put("visibleHeading", "Encabezado")
            .put("visibleFields", new JSONArray()).put("visibleCards", new JSONArray());
    }

    @Test public void normalizesIdWithoutLosingPrecision() {
        assertEquals(URL, ZoneUrl.normalize(ID));
        assertEquals("https://www.pokemon-zone.com/players/99999999999999999999/", ZoneUrl.normalize("99999999999999999999"));
    }
    @Test public void normalizesOnlyProfileAndCardsPaths() {
        assertEquals(URL, ZoneUrl.normalize(URL + "cards/"));
        assertEquals(URL, ZoneUrl.normalize("https://pokemon-zone.com/players/" + ID));
        assertNull(ZoneUrl.normalize("https://www.pokemon-zone.com/accounts/login/"));
    }
    @Test public void rejectsForeignHostAndCredentials() {
        assertNull(ZoneUrl.normalize("https://www.pokemon-zone.com.evil.test/players/" + ID + "/"));
        assertNull(ZoneUrl.normalize("https://user@www.pokemon-zone.com/players/" + ID + "/"));
    }
    @Test public void rejectsInsecureAndLocalSchemes() {
        for (String url : new String[]{"http://www.pokemon-zone.com/players/" + ID + "/", "javascript:alert(1)", "file:///players/" + ID + "/", "content://players/" + ID}) assertNull(ZoneUrl.normalize(url));
    }
    @Test public void rejectsPortsQueryAndFragments() {
        assertNull(ZoneUrl.normalize("https://www.pokemon-zone.com:444/players/" + ID + "/"));
        assertNull(ZoneUrl.normalize(URL + "?token=private"));
        assertNull(ZoneUrl.normalize(URL + "#token"));
    }
    @Test public void rejectsMalformedIds() {
        for (String id : new String[]{"123", "123456789012345678901", "123456789x", "", "1.23456789e15"}) assertNull(ZoneUrl.normalize(id));
    }
    @Test public void navigationStaysOnSameProfile() {
        assertTrue(ZoneUrl.belongsTo(URL + "cards/", URL));
        assertFalse(ZoneUrl.belongsTo("https://www.pokemon-zone.com/players/1234567890123456/", URL));
        assertFalse(ZoneUrl.belongsTo(URL + "?token=private", URL));
        assertFalse(ZoneUrl.belongsTo("https://accounts.google.com/", URL));
    }
    @Test public void partialPayloadCannotClaimCompletenessOrIncludeCredentials() throws Exception {
        JSONObject f = fixture().put("collectionComplete", true).put("token", "secret").put("embedded", new JSONObject().put("password", "secret"));
        JSONObject clean = Preview.validate(f.toString(), URL);
        assertFalse(clean.getBoolean("collectionComplete"));
        assertFalse(clean.has("token")); assertFalse(clean.has("embedded"));
    }
    @Test public void rejectsOtherPlayer() throws Exception {
        assertThrows(Exception.class, () -> Preview.validate(fixture().put("friendId", "1234567890123456").toString(), URL));
    }
    @Test public void rejectsWrongSourceUrl() throws Exception {
        assertThrows(Exception.class, () -> Preview.validate(fixture().put("url", "https://evil.test/").toString(), URL));
    }
    @Test public void rejectsInvalidOrOversizeJson() {
        assertThrows(Exception.class, () -> Preview.validate("not-json", URL));
        assertThrows(Exception.class, () -> Preview.validate(" ".repeat(Preview.MAX_BYTES + 1), URL));
    }
    @Test public void rejectsInvalidSchemaVersion() {
        assertThrows(Exception.class, () -> Preview.validate(fixture().put("schemaVersion", 1.5).toString(), URL));
        assertThrows(Exception.class, () -> Preview.validate(fixture().put("schemaVersion", "1").toString(), URL));
    }
    @Test public void rejectsNonIntegerAndNegativeQuantities() throws Exception {
        JSONArray cards = new JSONArray();
        for (Object quantity : new Object[]{-1, 1.5, 1000000, "2", 2}) cards.put(new JSONObject().put("quantity", quantity).put("cardPath", "/cards/a1-1/"));
        JSONObject clean = Preview.validate(fixture().put("visibleCards", cards).toString(), URL);
        assertEquals(1, clean.getJSONArray("visibleCards").length());
        assertEquals(2, clean.getJSONArray("visibleCards").getJSONObject(0).getInt("quantity"));
    }
    @Test public void rejectsForeignCardUrlsAndBoundsPreview() throws Exception {
        JSONArray cards = new JSONArray().put(new JSONObject().put("quantity", 2).put("cardPath", "https://evil.test/cards/a1-1/"));
        JSONObject clean = Preview.validate(fixture().put("visibleCards", cards).put("visibleSummary", "x".repeat(7000)).toString(), URL);
        assertEquals(0, clean.getJSONArray("visibleCards").length());
        assertEquals(6000, clean.getString("visibleSummary").length());
    }
    @Test public void acceptsMoreThanTwoHundredCardsWithoutClaimingCompleteness() throws Exception {
        JSONArray cards = new JSONArray();
        for (int i = 0; i < 250; i++) cards.put(new JSONObject().put("quantity", 1).put("cardPath", "/cards/a1/" + i + "/"));
        JSONObject clean = Preview.validate(fixture().put("visibleCards", cards).toString(), URL);
        assertEquals(250, clean.getJSONArray("visibleCards").length());
        assertFalse(clean.getBoolean("collectionComplete"));
    }

}
