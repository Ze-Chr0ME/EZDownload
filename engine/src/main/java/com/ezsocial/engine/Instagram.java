package com.ezsocial.engine;

import com.google.gson.*;
import okhttp3.FormBody;
import okhttp3.Response;
import okio.Buffer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

final class Instagram {
    private static final String ROOT = "https://www.instagram.com/";
    private static final String QUERY = "PolarisLoggedOutDesktopWWWPostRootContentQuery";
    private static final String UNAVAILABLE = "Instagram did not return this video. It may be private, age-restricted, or Instagram is rate-limiting anonymous requests; try again later.";
    static String shortcode(String url) {
        String path = URI.create(url).getPath();
        if (path.startsWith("/share/")) return null;
        Matcher match = Pattern.compile("/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)(?:/|$)").matcher(path);
        return match.find() ? match.group(1) : null;
    }
    static String mediaId(String code) {
        if (code.length() > 28) code = code.substring(0, code.length() - 28);
        BigInteger value = BigInteger.ZERO;
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        for (char letter : code.toCharArray()) {
            int digit = alphabet.indexOf(letter);
            if (digit < 0) throw new IllegalArgumentException("Invalid Instagram shortcode");
            value = value.multiply(BigInteger.valueOf(64)).add(BigInteger.valueOf(digit));
        }
        return value.toString();
    }
    static String lsd(String html) throws IOException {
        Matcher match = Pattern.compile("\\[\"LSD\",\\[\\],\\{\"token\":\"([^\"]+)\"").matcher(html);
        if (match.find()) return match.group(1);
        Element script = Jsoup.parse(html).selectFirst("script[id=__eqmc]");
        if (script != null) try {
            String token = string(JsonParser.parseString(script.data()).getAsJsonObject(), "l");
            if (token != null && !token.isEmpty()) return token;
        } catch (RuntimeException ignored) { }
        throw new IOException("Instagram did not provide an anonymous session token. Try again later.");
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
    private static String video(JsonObject object) {
        JsonElement versions = object.get("video_versions");
        if (versions != null && versions.isJsonArray()) for (JsonElement version : versions.getAsJsonArray()) {
            if (!version.isJsonObject()) continue;
            String url = string(version.getAsJsonObject(), "url");
            if (PageMedia.isMedia(url)) return url;
        }
        return null;
    }
    static PageMedia parse(String json, String code) throws IOException {
        try {
            JsonObject media = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("data")
                    .getAsJsonObject("xig_polaris_media").getAsJsonObject("if_not_gated_logged_out");
            String url = video(media);
            JsonElement carousel = media.get("carousel_media");
            if (url == null && carousel != null && carousel.isJsonArray()) for (JsonElement item : carousel.getAsJsonArray()) {
                if (item.isJsonObject()) url = video(item.getAsJsonObject());
                if (url != null) break;
            }
            if (url == null) throw new IOException(UNAVAILABLE);
            JsonElement captionValue = media.get("caption");
            JsonObject caption = captionValue != null && captionValue.isJsonObject() ? captionValue.getAsJsonObject() : null;
            String title = caption == null ? null : string(caption, "text");
            if (title != null) title = title.split("\\R", 2)[0].trim();
            return new PageMedia(title == null || title.isEmpty() ? "Instagram " + code : title, url);
        } catch (RuntimeException error) { throw new IOException(UNAVAILABLE, error); }
    }
    private static PageMedia query(String code) throws IOException {
        String token;
        try (Response response = Transport.INSTANCE.open(ROOT, "GET", null, null)) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Instagram returned HTTP " + response.code());
            token = lsd(Transport.readText(response.body().byteStream()));
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        String[][] values = {{"Content-Type", "application/x-www-form-urlencoded"}, {"X-IG-App-ID", "936619743392459"},
                {"X-ASBD-ID", "359341"}, {"X-IG-WWW-Claim", "0"}, {"Origin", ROOT.substring(0, ROOT.length() - 1)},
                {"Accept", "*/*"}, {"X-FB-Friendly-Name", QUERY}, {"X-FB-LSD", token}, {"X-Requested-With", "XMLHttpRequest"},
                {"Referer", ROOT + "p/" + code + "/"}, {"Sec-Fetch-Site", "same-origin"}, {"Sec-Fetch-Mode", "cors"}, {"Sec-Fetch-Dest", "empty"}};
        for (String[] header : values) headers.put(header[0], Collections.singletonList(header[1]));
        FormBody body = new FormBody.Builder().add("lsd", token).add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", QUERY).add("server_timestamps", "true")
                .add("variables", "{\"media_id\":\"" + mediaId(code) + "\"}").add("doc_id", "27130156389949648").build();
        Buffer buffer = new Buffer(); body.writeTo(buffer);
        try (Response response = Transport.INSTANCE.open(ROOT + "api/graphql", "POST", headers, buffer.readByteArray())) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Instagram returned HTTP " + response.code() + ". Try again later.");
            return parse(Transport.readText(response.body().byteStream()), code);
        }
    }
    static PageMedia resolve(String url) throws IOException {
        String code = shortcode(url), html = null, finalUrl = url;
        if (code == null) {
            try (Response response = Transport.INSTANCE.open(url, "GET", null, null)) {
                finalUrl = response.request().url().toString(); code = shortcode(finalUrl);
                if (response.body() != null) html = Transport.readText(response.body().byteStream());
            }
        }
        IOException failure;
        try {
            if (code == null) throw new IOException("Instagram did not resolve this link to a post or reel. Try its full post URL.");
            return query(code);
        } catch (IOException error) { Transport.INSTANCE.check(); failure = error; }
        try {
            if (html == null) try (Response response = Transport.INSTANCE.open(finalUrl, "GET", null, null)) {
                if (response.isSuccessful() && response.body() != null) html = Transport.readText(response.body().byteStream());
            }
            PageMedia media = html == null ? null : PageMedia.parse(html, finalUrl);
            if (media != null) return media;
            if (code != null) {
                String embed = ROOT + "p/" + code + "/embed/captioned/";
                try (Response response = Transport.INSTANCE.open(embed, "GET", null, null)) {
                    if (response.isSuccessful() && response.body() != null) {
                        media = PageMedia.parse(Transport.readText(response.body().byteStream()), embed);
                        if (media != null) return media;
                    }
                }
            }
        } catch (IOException error) { Transport.INSTANCE.check(); failure.addSuppressed(error); }
        throw failure;
    }
}
