package family.chumen;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Places {
    static final String TAXI = "amapuri://drive/takeTaxi?page=0";
    private static final String SERVICE = "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/";

    static final class Suggestion {
        final String text, key;
        Suggestion(String text, String key) { this.text = text; this.key = key; }
    }

    static final class Place {
        final String name, address;
        final double lat, lon;
        final boolean precise;

        Place(String name, String address, double lat, double lon, boolean precise) {
            this.name = name;
            this.address = address;
            this.lat = lat;
            this.lon = lon;
            this.precise = precise;
        }

        String route(String origin) {
            // Both lookup providers return WGS84. Let AMap apply its own conversion.
            return "amapuri://route/plan/?sourceApplication=chumen&t=1&m=0&dev=1&dlat="
                    + lat + "&dlon=" + lon + "&dname=" + encode(name) + origin;
        }
    }

    static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private static String queryText(String query, String city) {
        return city.isEmpty() || query.startsWith(city) ? query : city + " " + query;
    }

    static List<Suggestion> suggest(String query, String city, String nearby) throws IOException, JSONException {
        String url = SERVICE + "suggest?f=json&countryCode=CHN&langCode=zh&maxSuggestions=6"
                + nearby + "&text=" + encode(queryText(query, city));
        JSONArray rows = fetch(url).getJSONArray("suggestions");
        List<Suggestion> suggestions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String text = row.optString("text"), key = row.optString("magicKey");
            if (!text.isEmpty() && !key.isEmpty() && !row.optBoolean("isCollection") && seen.add(text)) {
                suggestions.add(new Suggestion(text, key));
            }
        }
        return suggestions;
    }

    static List<Place> search(String query, String city, String nearby, Suggestion picked) throws IOException, JSONException {
        String full = picked == null ? queryText(query, city) : picked.text;
        String url = SERVICE + "findAddressCandidates?f=json&sourceCountry=CHN&langCode=zh&outSR=4326&maxLocations=12"
                + "&outFields=PlaceName,LongLabel,City,Region,Addr_type&SingleLine=" + encode(full) + nearby;
        if (picked != null) url += "&magicKey=" + encode(picked.key);
        return parse(fetch(url).getJSONArray("candidates"), picked == null ? query : full, city, picked != null);
    }

    private static JSONObject fetch(String url) throws IOException, JSONException {
        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setConnectTimeout(4500);
        request.setReadTimeout(6000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                request.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
            return new JSONObject(text.toString());
        } finally {
            request.disconnect();
        }
    }

    private static List<Place> parse(JSONArray rows, String query, String city, boolean picked) throws JSONException {
        List<Place> result = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i), attr = row.getJSONObject("attributes");
            String type = attr.optString("Addr_type");
            if (!picked && !Arrays.asList("POI", "PointAddress", "StreetAddress", "StreetName",
                    "StreetInt", "Subaddress").contains(type)) continue;
            if (row.optDouble("score", 0) < 80) continue;
            String name = attr.optString("PlaceName");
            if (name.isEmpty()) name = row.getString("address");
            String address = attr.optString("LongLabel", name);
            String cityStem = city.replaceFirst("市$", "");
            if (!cityStem.isEmpty() && !address.contains(cityStem)
                    && !attr.optString("City").contains(cityStem)) continue;
            JSONObject point = row.getJSONObject("location");
            double lat = point.getDouble("y"), lon = point.getDouble("x");
            if (!(lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180)) continue;
            boolean duplicate = false;
            for (Place known : result) {
                if (known.name.equals(name) && Math.abs(lat-known.lat) < 0.0005
                        && Math.abs(lon-known.lon) < 0.0005) { duplicate = true; break; }
            }
            if (!duplicate) {
                boolean exact = query.contains(name) || name.equals(city + query) || address.endsWith(query);
                result.add(new Place(name, address, lat, lon, picked || (exact && row.optDouble("score") >= 95)));
            }
        }
        return result;
    }
}
