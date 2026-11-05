package com.ezsocial.desktop;

import java.io.File;
import java.nio.file.*;
import java.util.Comparator;

/** Uses file-system Unicode names directly, as the download completion path does. */
public final class ExplorerSmoke {
    public static void main(String[] args) throws Exception {
        File target;
        if (args[0].equals("--fixture")) {
            Path folder = Paths.get(args[1], "Folder, with spaces \u00e9 \uD83D\uDE33");
            Files.createDirectories(folder);
            Path file = folder.resolve("clip, $500 \uD83D\uDE33.mp4");
            Files.write(file, new byte[0]);
            target = file.toFile();
        } else {
            try (java.util.stream.Stream<Path> paths = Files.list(Paths.get(args[1]))) {
                target = paths.filter(path -> path.toString().endsWith(".mp4"))
                        .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                        .orElseThrow().toFile();
            }
        }
        WindowsExplorer.reveal(target);
    }
}
