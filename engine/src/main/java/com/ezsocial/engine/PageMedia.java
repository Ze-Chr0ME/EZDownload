package com.ezsocial.engine;

import com.google.gson.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.util.*;

/** Public-page extraction only; never sends links to a third-party downloader. */
public final class PageMedia {
    public final String title, url;
    PageMedia(String title, String url) { this.title = title; this.url = url; }
    public static PageMedia parse(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        Element titleTag = doc.selectFirst("meta[property=og:title]");
        String title = titleTag == null ? doc.title() : titleTag.attr("content");
        for (Element script : doc.select("script")) {
            String raw = script.data().trim();
            if (!raw.startsWith("{") && !raw.startsWith("[")) continue;
            try {
                String media = find(JsonParser.parseString(raw));
                if (media != null) return new PageMedia(title, media);
            } catch (JsonParseException ignored) { }
        }
        for (String selector : new String[]{"meta[property=og:video:secure_url]", "meta[property=og:video]", "meta[property=og:video:url]", "meta[name=twitter:player:stream]", "video[src]", "video source[src]"}) {
            Element tag = doc.selectFirst(selector);
            if (tag != null) {
                String value = tag.hasAttr("src") ? tag.absUrl("src") : tag.attr("content");
                if (isMedia(value)) return new PageMedia(title, value);
            }
        }
        // Instagram embeds can put their public media in an escaped JSON field.
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("\"(?:video_url|contentUrl)\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")").matcher(html);
        while (match.find()) {
            try { String value = JsonParser.parseString(match.group(1)).getAsString();
                if (isMedia(value)) return new PageMedia(title, value);
            } catch (RuntimeException ignored) { }
        }
        return null;
    }
    static boolean isMedia(String value) { return value != null && value.startsWith("https://") && !value.contains("/embed"); }
    private static String address(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString(); return isMedia(value) ? value : null;
        }
        if (element.isJsonArray()) { for (JsonElement item : element.getAsJsonArray()) { String value = address(item); if (value != null) return value; } }
        if (element.isJsonObject()) for (String key : new String[]{"UrlList", "url_list", "url"}) { String value = address(element.getAsJsonObject().get(key)); if (value != null) return value; }
        return null;
    }
    private static String find(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : new String[]{"playAddr", "PlayAddr", "video_url", "contentUrl", "downloadAddr"}) {
                String value = address(object.get(key)); if (value != null) return value;
            }
            JsonElement versions = object.get("video_versions");
            if (versions != null) { String value = address(versions); if (value != null) return value; }
            for (Map.Entry<String, JsonElement> child : object.entrySet()) { String value = find(child.getValue()); if (value != null) return value; }
        } else if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) { String value = find(child); if (value != null) return value; }
        return null;
    }
}
