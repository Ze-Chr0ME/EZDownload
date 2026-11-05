package com.ezsocial.desktop;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Select a file by its Unicode shell item ID, without Explorer command-line parsing. */
final class WindowsExplorer {
    interface Shell32 extends StdCallLibrary {
        Shell32 INSTANCE = Native.load("shell32", Shell32.class);
        int SHParseDisplayName(WString name, Pointer bindContext, PointerByReference item, int attributes, Pointer outAttributes);
        int SHOpenFolderAndSelectItems(Pointer item, int count, Pointer children, int flags);
    }
    interface Ole32 extends StdCallLibrary {
        Ole32 INSTANCE = Native.load("ole32", Ole32.class);
        int CoInitializeEx(Pointer reserved, int flags);
        void CoUninitialize();
        void CoTaskMemFree(Pointer item);
    }
    static void reveal(File file) throws IOException {
        Path path = file.toPath().toRealPath();
        if (!Files.isRegularFile(path)) throw new IOException("Video file does not exist: " + path);
        // COM initialization is per thread. Do not uninitialize an apartment owned by another caller.
        int initialized = Ole32.INSTANCE.CoInitializeEx(null, 0x2 | 0x4);
        if (initialized < 0 && initialized != 0x80010106) check(initialized, "Initialize Windows file selection");
        PointerByReference item = new PointerByReference();
        try {
            check(Shell32.INSTANCE.SHParseDisplayName(new WString(path.toString()), null, item, 0, null), "Locate saved video");
            // With count=0 the absolute PIDL identifies a file; Windows opens its parent and selects it.
            check(Shell32.INSTANCE.SHOpenFolderAndSelectItems(item.getValue(), 0, null, 0), "Select saved video in Explorer");
        } finally {
            if (item.getValue() != null) Ole32.INSTANCE.CoTaskMemFree(item.getValue());
            if (initialized >= 0) Ole32.INSTANCE.CoUninitialize();
        }
    }
    private static void check(int result, String operation) throws IOException {
        if (result < 0) throw new IOException(operation + " failed (Windows code " + String.format("0x%08X", result) + ")");
    }
}
