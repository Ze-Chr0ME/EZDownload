package com.ezsocial.engine;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.*;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.FileRandomAccessSourceImpl;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import okhttp3.Response;
import java.io.*;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

public final class VideoEngine {
    public interface Progress { void update(int percent, String message); }
    static { NewPipe.init(Transport.INSTANCE); }
    public static String extractUrl(String text) {
        Matcher matcher = Pattern.compile("https?://[^\\s<>\"']+").matcher(text);
        while (matcher.find()) {
            String url = matcher.group().replaceAll("[.,;!?)。]+$", "");
            try { URI uri = URI.create(url); if (uri.getHost() != null && uri.getUserInfo() == null) return url; }
            catch (IllegalArgumentException ignored) { }
        }
        throw new IllegalArgumentException("Share or paste a complete video URL.");
    }
    private static int height(VideoStream stream) {
        Matcher number = Pattern.compile("(\\d+)").matcher(stream.getResolution());
        return number.find() ? Integer.parseInt(number.group(1)) : 0;
    }
    private static boolean direct(Stream stream) {
        return stream.isUrl() && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP;
    }
    public static File download(String input, File folder, int maxHeight, Progress progress, AtomicBoolean cancelled) throws Exception {
        String url = extractUrl(input);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create download folder");
        File work = Files.createTempDirectory(folder.toPath(), ".ezsocial-").toFile();
        Transport.INSTANCE.begin(cancelled);
        try {
            progress.update(0, "Finding video…");
            String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
            String title; File completed = new File(work, "finished.mp4");
            if (host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com")) {
                StreamInfo info = StreamInfo.getInfo(url);
                title = info.getName();
                List<VideoStream> videos = new ArrayList<>(info.getVideoStreams());
                videos.addAll(info.getVideoOnlyStreams());
                videos.removeIf(stream -> stream.getFormat() != MediaFormat.MPEG_4 || !direct(stream) || height(stream) > maxHeight);
                videos.sort(Comparator.comparingInt(VideoEngine::height).reversed());
                VideoStream video = videos.isEmpty() ? null : videos.get(0);
                if (video == null) throw new IOException("YouTube did not return a downloadable MP4 at this quality. The site may require a sign-in or an extractor update.");
                if (video.isVideoOnly()) {
                    AudioStream audio = info.getAudioStreams().stream().filter(stream -> stream.getFormat() == MediaFormat.M4A && direct(stream))
                            .max(Comparator.comparingInt(AudioStream::getAverageBitrate)).orElseThrow(() -> new IOException("No compatible audio track available"));
                    File videoFile = new File(work, "video.mp4"), audioFile = new File(work, "audio.m4a");
                    transfer(video.getContent(), videoFile, url, 0, 75, progress);
                    transfer(audio.getContent(), audioFile, url, 75, 95, progress);
                    progress.update(96, "Combining video and audio…");
                    merge(videoFile, audioFile, completed);
                } else transfer(video.getContent(), completed, url, 0, 98, progress);
            } else if (host.equals("instagram.com") || host.endsWith(".instagram.com")) {
                PageMedia media = Instagram.resolve(url);
                title = media.title;
                transfer(media.url, completed, "https://www.instagram.com/", 0, 98, progress);
            } else {
                try (Response response = Transport.INSTANCE.open(url, "GET", null, null)) {
                    if (!response.isSuccessful() || response.body() == null) throw new IOException("Site returned HTTP " + response.code());
                    String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
                    if (type.startsWith("video/") || URI.create(url).getPath().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
                        title = new File(URI.create(url).getPath()).getName().replaceFirst("\\.[^.]+$", "");
                        if (!type.startsWith("video/") && !type.contains("octet-stream")) throw new IOException("The link did not return a video");
                        copyResponse(response, completed, 0, 98, progress);
                    } else {
                        String html = Transport.readText(response.body().byteStream());
                        String finalUrl = response.request().url().toString();
                        PageMedia media = PageMedia.parse(html, finalUrl);
                        if (media == null) throw new IOException("No public video found. This page may require a sign-in, block downloading, or use an unsupported format.");
                        title = media.title;
                        transfer(media.url, completed, finalUrl, 0, 98, progress);
                    }
                }
            }
            Transport.INSTANCE.check();
            validateMp4(completed);
            String safe = safeName(title);
            File destination = new File(folder, safe + ".mp4");
            for (int index = 2; destination.exists(); index++) destination = new File(folder, safe + " (" + index + ").mp4");
            Files.move(completed.toPath(), destination.toPath());
            if (cancelled.get()) { Files.deleteIfExists(destination.toPath()); throw new IOException("Cancelled"); }
            progress.update(100, "Video saved");
            return destination;
        } finally {
            Transport.INSTANCE.end();
            File[] files = work.listFiles(); if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(work.toPath());
        }
    }
    static String safeName(String title) {
        String name = title == null ? "video" : title.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").trim().replaceAll("[. ]+$", "");
        if (name.isEmpty()) name = "video";
        if (name.length() > 100) name = name.substring(0, 100);
        if (name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) name = "_" + name;
        return name;
    }
    private static void transfer(String url, File target, String referer, int start, int end, Progress progress) throws IOException {
        try (Response response = Transport.INSTANCE.open(url, "GET", Collections.singletonMap("Referer", Collections.singletonList(referer)), null)) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Video server returned HTTP " + response.code());
            String type = response.header("Content-Type", "");
            if (type.contains("text/html") || type.contains("application/json")) throw new IOException("Video server returned a page instead of a video");
            copyResponse(response, target, start, end, progress);
        }
    }
    private static void copyResponse(Response response, File target, int start, int end, Progress progress) throws IOException {
        long total = response.body().contentLength(), done = 0, last = 0;
        try (InputStream input = response.body().byteStream(); OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) {
                Transport.INSTANCE.check(); output.write(buffer, 0, count); done += count;
                if (System.nanoTime() - last > 250000000L) {
                    progress.update(total > 0 ? start + (int)((end - start) * Math.min(1d, (double)done / total)) : start, "Downloading video…");
                    last = System.nanoTime();
                }
            }
        }
        if (total >= 0 && done != total) throw new IOException("Download was incomplete. Please retry.");
        if (done == 0) throw new IOException("Video file was empty");
        progress.update(end, "Preparing video…");
    }
    private static void validateMp4(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (input.length() < 12) throw new IOException("Video file is incomplete");
            input.seek(4); byte[] box = new byte[4]; input.readFully(box);
            if (!Arrays.equals(box, new byte[]{'f','t','y','p'})) throw new IOException("This video format is not supported by the lightweight MP4 downloader");
        }
    }
    // mp4parser reads its box map with ClassLoader.getSystemResourceAsStream, which cannot see
    // APK resources on Android (Properties.load(null): "inStream parameter is null"). Preload it.
    static synchronized void loadBoxMap() throws IOException {
        if (org.mp4parser.PropertyBoxParserImpl.BOX_MAP_CACHE != null) return;
        try (InputStream input = VideoEngine.class.getClassLoader().getResourceAsStream("isoparser2-default.properties")) {
            if (input == null) throw new IOException("MP4 combiner data is missing from this build");
            Properties map = new Properties(); map.load(input);
            org.mp4parser.PropertyBoxParserImpl.BOX_MAP_CACHE = map;
        }
    }
    static void merge(File video, File audio, File output) throws Exception {
        loadBoxMap();
        try (Mp4Source videoSource = new Mp4Source(video); Mp4Source audioSource = new Mp4Source(audio)) {
            Movie result = new Movie();
            for (Track track : videoSource.movie.getTracks()) result.addTrack(track);
            for (Track track : audioSource.movie.getTracks()) result.addTrack(track);
            try (FileChannel channel = new FileOutputStream(output).getChannel()) { new DefaultMp4Builder().build(result).writeContainer(channel); }
        }
    }
    // MovieCreator.build(path) leaves its random-access file open; Track.close() is a no-op.
    // Own both streams explicitly so temporary tracks can be deleted on Windows too.
    static final class Mp4Source implements AutoCloseable {
        final FileInputStream metadata;
        final RandomAccessFile samples;
        final Movie movie;
        Mp4Source(File file) throws IOException {
            metadata = new FileInputStream(file);
            try { samples = new RandomAccessFile(file, "r"); }
            catch (IOException error) { metadata.close(); throw error; }
            try { movie = MovieCreator.build(metadata.getChannel(), new FileRandomAccessSourceImpl(samples), file.toString()); }
            catch (IOException | RuntimeException error) { samples.close(); metadata.close(); throw error; }
        }
        @Override public void close() throws IOException {
            try { samples.close(); } finally { metadata.close(); }
        }
    }
}
