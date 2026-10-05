package cl.demeberant.pocketzone;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZoneUrl {
    private static final Pattern ID = Pattern.compile("[0-9]{10,20}");
    private static final Pattern PATH = Pattern.compile("^/players/([0-9]{10,20})(?:/|/cards/)?$");
    private ZoneUrl() {}

    public static String normalize(String input) {
        String value = input == null ? "" : input.trim();
        if (ID.matcher(value).matches()) return "https://www.pokemon-zone.com/players/" + value + "/";
        try {
            URI uri = URI.create(value);
            if (!isOrigin(uri) || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            Matcher m = PATH.matcher(uri.getPath());
            if (!m.matches()) return null;
            return "https://www.pokemon-zone.com/players/" + m.group(1) + "/";
        } catch (IllegalArgumentException e) { return null; }
    }

    public static boolean isOrigin(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
            && (uri.getPort() == -1 || uri.getPort() == 443)
            && ("www.pokemon-zone.com".equalsIgnoreCase(uri.getHost()) || "pokemon-zone.com".equalsIgnoreCase(uri.getHost()));
    }

    public static boolean belongsTo(String url, String profileUrl) {
        try {
            URI uri = URI.create(url);
            String id = friendId(profileUrl);
            return id != null && isOrigin(uri) && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && (uri.getPath().equals("/players/" + id + "/") || uri.getPath().equals("/players/" + id + "/cards/"));
        } catch (IllegalArgumentException | NullPointerException e) { return false; }
    }

    public static String friendId(String url) {
        if (url == null) return null;
        try {
            Matcher m = PATH.matcher(URI.create(url).getPath());
            return m.matches() ? m.group(1) : null;
        } catch (IllegalArgumentException | NullPointerException e) { return null; }
    }
}
