package com.ezsocial.engine;

import okhttp3.*;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Transport extends Downloader {
    public static final Transport INSTANCE = new Transport();
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
    private final ThreadLocal<AtomicBoolean> cancellation = new ThreadLocal<>();
    private final ThreadLocal<List<Cookie>> cookies = ThreadLocal.withInitial(ArrayList::new);
    private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).followRedirects(true).cookieJar(new CookieJar() {
                @Override public void saveFromResponse(HttpUrl url, List<Cookie> incoming) {
                    List<Cookie> saved = cookies.get();
                    for (Cookie cookie : incoming) {
                        saved.removeIf(old -> old.name().equals(cookie.name()) && old.domain().equals(cookie.domain()) && old.path().equals(cookie.path()));
                        if (cookie.expiresAt() > System.currentTimeMillis()) saved.add(cookie);
                    }
                }
                @Override public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> saved = cookies.get(), result = new ArrayList<>();
                    saved.removeIf(cookie -> cookie.expiresAt() <= System.currentTimeMillis());
                    for (Cookie cookie : saved) if (cookie.matches(url)) result.add(cookie);
                    return result;
                }
            }).build();
    public void begin(AtomicBoolean token) { cancellation.set(token); }
    public void end() { cancellation.remove(); cookies.remove(); }
    void check() throws IOException {
        AtomicBoolean token = cancellation.get();
        if (Thread.currentThread().isInterrupted() || token != null && token.get()) throw new IOException("Cancelled");
    }
    okhttp3.Response open(String url, String method, Map<String, List<String>> headers, byte[] body) throws IOException {
        check();
        if (!url.startsWith("https://") && !url.startsWith("http://")) throw new IOException("Only web URLs are supported");
        Request.Builder builder = new Request.Builder().url(url).header("User-Agent", USER_AGENT);
        if (headers != null) headers.forEach((key, values) -> { if (key != null) {
            builder.removeHeader(key); for (String value : values) builder.addHeader(key, value);
        } });
        RequestBody data = body == null ? null : RequestBody.create(body, (MediaType) null);
        builder.method(method, data);
        return client.newCall(builder.build()).execute();
    }
    @Override public Response execute(org.schabi.newpipe.extractor.downloader.Request request) throws IOException {
        try (okhttp3.Response response = open(request.url(), request.httpMethod(), request.headers(), request.dataToSend())) {
            String text = response.body() == null ? "" : readText(response.body().byteStream());
            return new Response(response.code(), response.message(), response.headers().toMultimap(), text, response.request().url().toString());
        }
    }
    static String readText(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[16384]; int read;
        while ((read = input.read(buffer)) != -1) {
            INSTANCE.check();
            if (output.size() + read > 16 * 1024 * 1024) throw new IOException("Page metadata is too large");
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }
}
