package com.ezsocial.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.muxer.builder.DefaultMp4Builder;

class EngineTest {
    @TempDir Path temp;
    @Test void extractsSharedLink() {
        assertEquals("https://youtu.be/abc?t=4", VideoEngine.extractUrl("Watch this: https://youtu.be/abc?t=4\nSent from YouTube"));
        assertEquals("https://instagram.com/reel/abc/", VideoEngine.extractUrl("(https://instagram.com/reel/abc/)."));
    }
    @Test void rejectsInvalidLinks() {
        for (String input : new String[]{"hello", "file:///video.mp4", "https://", "https://user:pass@example.com"})
            assertThrows(IllegalArgumentException.class, () -> VideoEngine.extractUrl(input));
    }
    @Test void safeWindowsFilenames() {
        assertEquals("_CON", VideoEngine.safeName("CON"));
        assertEquals("hello_world_", VideoEngine.safeName("hello:world?"));
        assertEquals("video", VideoEngine.safeName("..."));
        assertEquals(100, VideoEngine.safeName("a".repeat(200)).length());
    }
    @Test void extractsTikTokHydrationData() {
        String html = "<title>My clip</title><script id='__UNIVERSAL_DATA_FOR_REHYDRATION__' type='application/json'>"
                + "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"itemInfo\":{\"itemStruct\":{\"video\":{\"playAddr\":\"https://cdn.example/video.mp4\"}}}}}}</script>";
        PageMedia video = PageMedia.parse(html, "https://www.tiktok.com/@user/video/123");
        assertNotNull(video); assertEquals("https://cdn.example/video.mp4", video.url);
    }
    @Test void extractsInstagramEmbeddedVideo() {
        PageMedia media = PageMedia.parse("<title>Reel</title><script type='application/json'>{\"data\":{\"video_versions\":[{\"url\":\"https://cdn.example/reel.mp4\"}]}}</script>", "https://instagram.com/reel/abc/");
        assertNotNull(media); assertEquals("https://cdn.example/reel.mp4", media.url);
    }
    @Test void decodesInstagramMediaId() {
        assertEquals("2913440072144448240", Instagram.mediaId("Chunk8-jurw"));
        assertEquals("2913440072144448240", Instagram.mediaId("Chunk8-jurw" + "a".repeat(28)));
        assertThrows(IllegalArgumentException.class, () -> Instagram.mediaId("bad!"));
    }
    @Test void extractsInstagramShortcodes() {
        for (String path : new String[]{"reel/Chunk8-jurw/?igsh=abc", "someuser/reel/Chunk8-jurw/",
                "p/Chunk8-jurw/", "reels/Chunk8-jurw", "tv/Chunk8-jurw/"})
            assertEquals("Chunk8-jurw", Instagram.shortcode("https://www.instagram.com/" + path));
        assertNull(Instagram.shortcode("https://www.instagram.com/share/reel/XYZ/"));
        assertNull(Instagram.shortcode("https://www.instagram.com/?next=/reel/XYZ/"));
    }
    @Test void extractsInstagramSessionToken() throws Exception {
        assertEquals("token-123", Instagram.lsd("<script>requireLazy([\"LSD\",[],{\"token\":\"token-123\"}]);</script>"));
        assertEquals("eqmc-token", Instagram.lsd("<script type='application/json' id='__eqmc'>{\"l\":\"eqmc-token\"}</script>"));
        assertThrows(IOException.class, () -> Instagram.lsd("<html></html>"));
    }
    private static String instagramFixture(String media) {
        return "{\"data\":{\"xig_polaris_media\":{\"if_not_gated_logged_out\":" + media + "}}}";
    }
    @Test void extractsInstagramGraphqlVideo() throws Exception {
        PageMedia media = Instagram.parse(instagramFixture("{\"caption\":{\"text\":\"  My reel  \\nMore text\"},"
                + "\"video_versions\":[{\"url\":\"https://cdn.example/reel.mp4\"}]}"), "Chunk8-jurw");
        assertEquals("https://cdn.example/reel.mp4", media.url);
        assertEquals("My reel", media.title);
    }
    @Test void extractsFirstInstagramCarouselVideo() throws Exception {
        PageMedia media = Instagram.parse(instagramFixture("{\"caption\":null,\"carousel_media\":[{\"image_versions2\":{}},"
                + "{\"video_versions\":[{\"url\":\"https://cdn.example/first.mp4\"}]},"
                + "{\"video_versions\":[{\"url\":\"https://cdn.example/second.mp4\"}]}]}"), "Chunk8-jurw");
        assertEquals("https://cdn.example/first.mp4", media.url);
        assertEquals("Instagram Chunk8-jurw", media.title);
    }
    @Test void reportsUnavailableInstagramVideo() {
        for (String json : new String[]{"<html></html>", "{\"data\":null}", instagramFixture("{}"), instagramFixture("null")}) {
            IOException error = assertThrows(IOException.class, () -> Instagram.parse(json, "abc"));
            assertTrue(error.getMessage().contains("private, age-restricted"));
        }
    }
    @Test void extractsEscapedVideoUrl() {
        PageMedia media = PageMedia.parse("<script>window.video = {\"video_url\":\"https:\\/\\/cdn.example\\/reel.mp4?x=1\\u0026y=2\"};</script>", "https://instagram.com/p/abc/embed/");
        assertNotNull(media); assertEquals("https://cdn.example/reel.mp4?x=1&y=2", media.url);
    }
    @Test void extractsOpenGraphVideo() {
        PageMedia media = PageMedia.parse("<meta property='og:video' content='https://cdn.example/video.mp4?a=1&amp;b=2'>", "https://example.com/watch");
        assertEquals("https://cdn.example/video.mp4?a=1&b=2", media.url);
    }
    @Test void doesNotTreatPageImagesAsVideos() {
        assertNull(PageMedia.parse("<script type='application/json'>{\"image\":{\"url\":\"https://cdn.example/thumbnail.jpg\"}}</script>", "https://example.com"));
    }
    @Test void cancellationBeforeNetworkLeavesNoPartialFiles() throws Exception {
        assertThrows(IOException.class, () -> VideoEngine.download("https://example.com/video.mp4", temp.toFile(), 720,
                (percent, text) -> {}, new AtomicBoolean(true)));
        try (java.util.stream.Stream<Path> files = Files.list(temp)) { assertEquals(0, files.count()); }
    }
    @Test @EnabledIfSystemProperty(named="ezsocial.fixture", matches=".+")
    void muxesVideoAndAudioWithoutExternalExecutable() throws Exception {
        File video = temp.resolve("video.mp4").toFile(), audio = temp.resolve("audio.m4a").toFile();
        int expectedTracks;
        try (VideoEngine.Mp4Source source = new VideoEngine.Mp4Source(new File(System.getProperty("ezsocial.fixture")))) {
            Movie original = source.movie;
            expectedTracks = (int)original.getTracks().stream().filter(track -> track.getHandler().equals("vide") || track.getHandler().equals("soun")).count();
            for (String handler : new String[]{"vide", "soun"}) {
                Movie selected = new Movie();
                original.getTracks().stream().filter(track -> track.getHandler().equals(handler)).forEach(selected::addTrack);
                assertFalse(selected.getTracks().isEmpty());
                try (FileChannel channel = new FileOutputStream(handler.equals("vide") ? video : audio).getChannel()) {
                    new DefaultMp4Builder().build(selected).writeContainer(channel);
                }
            }
        }
        File result = temp.resolve("merged.mp4").toFile();
        VideoEngine.merge(video, audio, result);
        try (VideoEngine.Mp4Source source = new VideoEngine.Mp4Source(result)) {
            Movie merged = source.movie;
            assertEquals(expectedTracks, merged.getTracks().size());
            assertTrue(merged.getTracks().stream().anyMatch(track -> track.getHandler().equals("vide")));
            assertTrue(merged.getTracks().stream().anyMatch(track -> track.getHandler().equals("soun")));
        }
        // Closed input/output handles must permit immediate cleanup on Windows.
        Files.delete(video.toPath()); Files.delete(audio.toPath()); Files.delete(result.toPath());
    }
}
