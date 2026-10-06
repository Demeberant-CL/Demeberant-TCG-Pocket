package cl.demeberant.pocketzone;

import org.json.JSONArray;
import org.json.JSONObject;

public final class Preview {
    public static final int MAX_BYTES = 3000000;
    private Preview() {}

    public static JSONObject validate(String text, String profileUrl) throws Exception {
        if (text == null || text.length() > MAX_BYTES || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Respuesta demasiado grande");
        JSONObject input = new JSONObject(text);
        String id = ZoneUrl.friendId(profileUrl);
        if (id == null || !(input.opt("schemaVersion") instanceof Number) || input.getDouble("schemaVersion") != 1
            || !id.equals(input.optString("friendId")) || !ZoneUrl.belongsTo(input.optString("url"), profileUrl)) {
            throw new IllegalArgumentException("El resultado no corresponde al perfil abierto");
        }
        JSONObject output = new JSONObject();
        output.put("schemaVersion", 1);
        output.put("source", "pokemon-zone");
        output.put("friendId", id);
        output.put("url", input.getString("url"));
        output.put("pageTitle", limited(input.optString("pageTitle"), 180));
        output.put("visibleHeading", limited(input.optString("visibleHeading"), 180));
        output.put("visibleSummary", limited(input.optString("visibleSummary"), 6000));
        // No inferimos un nombre de jugador o estadísticas a partir del título de la página.
        JSONArray fields = input.optJSONArray("visibleFields");
        JSONArray cleanFields = new JSONArray();
        if (fields != null) for (int i = 0; i < Math.min(fields.length(), 30); i++) {
            JSONObject field = fields.optJSONObject(i);
            if (field == null) continue;
            String label = limited(field.optString("label"), 60);
            String value = limited(field.optString("value"), 120);
            if (!label.isEmpty() && !value.isEmpty()) cleanFields.put(new JSONObject().put("label", label).put("value", value));
        }
        output.put("visibleFields", cleanFields);
        output.put("collectionComplete", false);
        JSONArray cards = input.optJSONArray("visibleCards");
        JSONArray cleanCards = new JSONArray();
        if (cards != null) for (int i = 0; i < Math.min(cards.length(), 5000); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            Object rawQuantity = card.opt("quantity");
            if (!(rawQuantity instanceof Number)) continue;
            double number = ((Number) rawQuantity).doubleValue();
            if (number < 0 || number > 999999 || number != Math.floor(number)) continue;
            int quantity = (int) number;
            String path = limited(card.optString("cardPath"), 200);
            if (!path.matches("/cards/[A-Za-z0-9/_-]+/?")) continue;
            cleanCards.put(new JSONObject().put("cardPath", path).put("quantity", quantity)
                .put("name", limited(card.optString("name"), 120)));
        }
        output.put("visibleCards", cleanCards);
        output.put("notice", "Vista previa de elementos visibles. Colección incompleta; sin importar a TCG Pocket.");
        return output;
    }

    private static String limited(String value, int max) {
        String result = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        return result.substring(0, Math.min(result.length(), max));
    }
}
