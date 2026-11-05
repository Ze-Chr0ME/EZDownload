package com.ezsocial.desktop;

import com.ezsocial.engine.VideoEngine;
import com.google.gson.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public final class App {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = Paths.get(System.getenv("APPDATA") == null ? System.getProperty("user.home") + "/.config" : System.getenv("APPDATA"), "EzSocialToVideo", "settings.json");
    static JsonObject settings() {
        JsonObject result = new JsonObject();
        result.addProperty("folder", Paths.get(System.getProperty("user.home"), "Videos", "EzSocialToVideo").toString());
        result.addProperty("quality", "1080p");
        try {
            JsonObject stored = JsonParser.parseString(Files.readString(CONFIG)).getAsJsonObject();
            String folder = stored.get("folder").getAsString(); if (!folder.isBlank()) result.addProperty("folder", folder);
            String quality = stored.get("quality").getAsString();
            if (quality.equals("Best") || quality.equals("1080p") || quality.equals("720p")) result.addProperty("quality", quality);
        } catch (Exception ignored) { }
        return result;
    }
    private static void save(JsonObject settings) throws IOException {
        Files.createDirectories(CONFIG.getParent());
        Path temporary = Files.createTempFile(CONFIG.getParent(), "settings-", ".tmp");
        try { Files.writeString(temporary, JSON.toJson(settings)); Files.move(temporary, CONFIG, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temporary); }
    }
    static int quality(String value) { return value.equals("Best") ? Integer.MAX_VALUE : value.equals("720p") ? 720 : 1080; }
    private static void reveal(File file) throws IOException {
        WindowsExplorer.reveal(file);
    }
    private static void log(String url, String result) {
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG.resolveSibling("background.log"), Instant.now() + " " + url + " " + result.replace('\n', ' ') + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }
    static void writeMessage(JsonObject message) throws IOException {
        byte[] body = JSON.toJson(message).getBytes(StandardCharsets.UTF_8);
        System.out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.length).array());
        System.out.write(body); System.out.flush();
    }
    static void nativeHost() {
        boolean[] connected = {true};
        try {
            byte[] header = System.in.readNBytes(4);
            if (header.length != 4) throw new IOException("Incomplete browser message");
            int size = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (size <= 0 || size > 65536) throw new IOException("Invalid browser message size");
            byte[] input = System.in.readNBytes(size);
            if (input.length != size) throw new IOException("Incomplete browser message");
            JsonObject request = JsonParser.parseString(new String(input, StandardCharsets.UTF_8)).getAsJsonObject();
            String url = VideoEngine.extractUrl(request.get("url").getAsString());
            boolean stream = request.has("stream") && request.get("stream").getAsBoolean();
            JsonObject settings = settings();
            VideoEngine.Progress progress = (percent, text) -> {
                if (!stream || !connected[0]) return;
                JsonObject update = new JsonObject(); update.addProperty("type", "progress");
                update.addProperty("percent", Math.min(99, percent)); update.addProperty("message", text);
                try { writeMessage(update); } catch (IOException ignored) { connected[0] = false; }
            };
            File file = VideoEngine.download(url, new File(settings.get("folder").getAsString()), quality(settings.get("quality").getAsString()), progress, new AtomicBoolean());
            if (!request.has("reveal") || request.get("reveal").getAsBoolean()) reveal(file);
            log(url, "Saved: " + file);
            JsonObject result = new JsonObject(); result.addProperty("type", "done"); result.addProperty("ok", true); result.addProperty("path", file.toString());
            if (connected[0]) writeMessage(result);
        } catch (Exception error) {
            JsonObject result = new JsonObject(); result.addProperty("type", "done"); result.addProperty("ok", false); result.addProperty("error", error.getMessage() == null ? error.toString() : error.getMessage());
            log("browser", "Error: " + error);
            try { if (connected[0]) writeMessage(result); } catch (IOException ignored) { }
        }
    }
    private static void window() {
        JsonObject settings = settings();
        JFrame frame = new JFrame("EzSocial · Save a video");
        JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setBorder(new EmptyBorder(28, 30, 28, 30));
        Color background = new Color(16, 24, 32), mint = new Color(76, 219, 171);
        panel.setBackground(background);
        JLabel brand = new JLabel("E Z S O C I A L   /   V I D E O"); brand.setForeground(mint); panel.add(brand); panel.add(Box.createVerticalStrut(18));
        JLabel heading = new JLabel("Keep the good ones."); heading.setFont(new Font("Segoe UI", Font.BOLD, 30)); heading.setForeground(Color.WHITE); panel.add(heading);
        JLabel subtitle = new JLabel("Paste a video link to save it locally."); subtitle.setForeground(Color.LIGHT_GRAY); panel.add(subtitle); panel.add(Box.createVerticalStrut(22));
        JTextField url = new JTextField(); url.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36)); panel.add(url); panel.add(Box.createVerticalStrut(10));
        JButton paste = new JButton("Paste link"); panel.add(paste);
        paste.addActionListener(event -> {
            try { url.setText(VideoEngine.extractUrl((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor))); }
            catch (Exception error) { JOptionPane.showMessageDialog(frame, "Clipboard does not contain a video link."); }
        });
        panel.add(Box.createVerticalStrut(20));
        JLabel destination = new JLabel("SAVE TO"); destination.setForeground(Color.LIGHT_GRAY); panel.add(destination);
        JTextField folder = new JTextField(settings.get("folder").getAsString()); folder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36)); panel.add(folder);
        JButton choose = new JButton("Choose folder…"); panel.add(choose);
        JComboBox<String> quality = new JComboBox<>(new String[]{"1080p", "720p", "Best"}); quality.setSelectedItem(settings.get("quality").getAsString());
        quality.setMaximumSize(new Dimension(130, 36)); panel.add(Box.createVerticalStrut(14)); panel.add(quality);
        JLabel status = new JLabel("Ready when you are."); status.setForeground(mint);
        Runnable persist = () -> {
            settings.addProperty("folder", folder.getText()); settings.addProperty("quality", (String)quality.getSelectedItem());
            try { save(settings); } catch (IOException error) { status.setText("Could not save settings: " + error.getMessage()); }
        };
        choose.addActionListener(event -> { JFileChooser chooser = new JFileChooser(folder.getText()); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) { folder.setText(chooser.getSelectedFile().toString()); persist.run(); }
        });
        quality.addActionListener(event -> persist.run());
        folder.addFocusListener(new java.awt.event.FocusAdapter() { @Override public void focusLost(java.awt.event.FocusEvent event) { persist.run(); } });
        panel.add(Box.createVerticalStrut(14)); JProgressBar bar = new JProgressBar(0, 100); bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18)); panel.add(bar);
        panel.add(Box.createVerticalStrut(10)); panel.add(status); panel.add(Box.createVerticalStrut(14));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT)); buttons.setBackground(background); buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton download = new JButton("Download video ↓"), cancel = new JButton("Cancel"), open = new JButton("Open folder");
        cancel.setEnabled(false); buttons.add(download); buttons.add(cancel); buttons.add(open); panel.add(buttons);
        AtomicBoolean cancelled = new AtomicBoolean(); boolean[] busy = {false};
        open.addActionListener(event -> { try { File dir = new File(folder.getText()); dir.mkdirs(); Desktop.getDesktop().open(dir); } catch (IOException error) { status.setText(error.getMessage()); } });
        cancel.addActionListener(event -> { cancelled.set(true); cancel.setEnabled(false); status.setText("Cancelling…"); });
        download.addActionListener(event -> {
            final String link; try { link = VideoEngine.extractUrl(url.getText()); if (folder.getText().isBlank()) throw new IllegalArgumentException("Choose a save folder"); }
            catch (Exception error) { status.setText(error.getMessage()); return; }
            persist.run(); String target = folder.getText(); int limit = quality((String)quality.getSelectedItem());
            busy[0] = true; cancelled.set(false); download.setEnabled(false); cancel.setEnabled(true); bar.setValue(0); status.setText("Finding video…");
            new Thread(() -> {
                String result;
                try { File file = VideoEngine.download(link, new File(target), limit, (percent, text) -> SwingUtilities.invokeLater(() -> { bar.setValue(percent); status.setText(text); }), cancelled); result = "Saved: " + file.getName(); }
                catch (Exception error) { result = cancelled.get() ? "Cancelled." : "Failed: " + error.getMessage(); }
                final String text = result;
                SwingUtilities.invokeLater(() -> { status.setText(text); busy[0] = false; download.setEnabled(true); cancel.setEnabled(false); });
            }, "video-download").start();
        });
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent event) {
            if (busy[0] && JOptionPane.showConfirmDialog(frame, "Cancel the download and close?", "Download in progress", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            persist.run(); cancelled.set(true); frame.dispose(); System.exit(0);
        } });
        frame.setContentPane(panel); frame.setSize(720, 600); frame.setMinimumSize(new Dimension(620, 540)); frame.setLocationRelativeTo(null); frame.setVisible(true);
    }
    public static void main(String[] args) throws Exception {
        for (String arg : args) if (arg.equals("--native-host") || arg.startsWith("chrome-extension://")) { nativeHost(); return; }
        if (args.length == 2 && args[0].equals("--reveal")) {
            reveal(new File(args[1])); return;
        }
        if (args.length >= 3 && args[0].equals("--download")) {
            File file = VideoEngine.download(args[1], new File(args[2]), 1080, (p, text) -> System.out.println(p + "% " + text), new AtomicBoolean());
            System.out.println("Saved: " + file); return;
        }
        SwingUtilities.invokeLater(App::window);
    }
}
