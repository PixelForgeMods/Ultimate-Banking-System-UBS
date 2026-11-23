package net.austizz.ultimatebankingsystem.gui.screens;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

enum PcFileType {
    AUDIO(false, "aac", "flac", "m4a", "ogg", "opus"),
    CODE(true, "c", "cpp", "cs", "go", "jsx", "kt", "lua", "pcsh", "py", "rs", "sh", "ts", "tsx"),
    DOCUMENT(true, "log", "md", "rtf", "ini", "toml", "yaml", "yml"),
    EMPTY(false),
    IMAGE(false, "bmp", "heic", "ico"),
    IMG(false),
    SPREADSHEETS(false, "ods"),
    VIDEO(false, "wmv"),
    VIDEO_01(false, "mov", "m4v"),
    VIDEO_02(false, "webm"),
    AEP(false), AI(false), AVI(false),
    CSS(true), CSV(true), DMG(false), DOC(false), DOCX(false),
    EPS(false), EXE(false), FIG(false), GIF(false), HTML(true, "htm"),
    INDD(false), JAVA(true), JPEG(false), JPG(false), JS(true, "mjs", "cjs"), JSON(true),
    MKV(false), MP3(false), MP4(false), MPEG(false, "mpg"),
    PDF(false), PDF_SIMPLE(false), PNG(false), PPT(false), PPTX(false), PSD(false),
    RAR(false), RSS(true), SQL(true), SVG(true), TIFF(false, "tif"),
    TXT(true), WAV(false), WEBP(false), XLS(false), XLSX(false), XML(true), ZIP(false);

    private static final Map<String, PcFileType> BY_EXTENSION = new HashMap<>();

    static {
        for (var type : values()) {
            BY_EXTENSION.put(type.icon, type);
            for (String alias : type.aliases) BY_EXTENSION.put(alias, type);
        }
    }

    final String icon = name().toLowerCase(Locale.ROOT).replace('_', '-');
    private final boolean text;
    private final String[] aliases;

    PcFileType(boolean text, String... aliases) {
        this.text = text;
        this.aliases = aliases;
    }

    static java.util.Set<String> extensions() { return BY_EXTENSION.keySet(); }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    static PcFileType iconFor(String name, boolean empty) {
        return BY_EXTENSION.getOrDefault(extension(name), empty ? EMPTY : DOCUMENT);
    }

    static boolean canImportAsText(String name) {
        String extension = extension(name);
        var type = BY_EXTENSION.get(extension);
        return extension.isEmpty() || type != null && type.text;
    }

    static String label(String name) {
        String extension = extension(name);
        if (extension.isEmpty()) return "File";
        return extension.equals("txt") ? "Text document" : extension.toUpperCase(Locale.ROOT) + " file";
    }
}
