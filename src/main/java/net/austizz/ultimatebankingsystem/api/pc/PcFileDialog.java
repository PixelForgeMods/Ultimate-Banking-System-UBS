package net.austizz.ultimatebankingsystem.api.pc;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Requests and results for the shared in-game file dialogs. */
public final class PcFileDialog {
    private PcFileDialog() {}

    public enum Mode { OPEN, SAVE, FOLDER }
    public record Filter(String label, List<String> extensions) {
        public Filter {
            Objects.requireNonNull(label);
            extensions = extensions.stream().map(PcFileDialog::extension).distinct().toList();
        }
    }

    public record Request(Mode mode, UUID initialFolder, String suggestedName, List<Filter> filters, boolean multiple) {
        public Request {
            Objects.requireNonNull(mode);
            suggestedName = Objects.requireNonNullElse(suggestedName, "");
            filters = List.copyOf(filters);
            if (multiple && mode != Mode.OPEN) throw new IllegalArgumentException("Only Open supports multiple files.");
        }
    }

    /** Open and Folder return item IDs. Save returns a destination and performs no write. */
    public record Selection(List<UUID> items, UUID folder, String name, UUID replace) {
        public Selection { items = List.copyOf(items); }
    }

    @FunctionalInterface public interface Accepted {
        /** Throw IOException to keep the dialog open and show the error. */
        void accept(Selection result) throws IOException;
    }

    static String extension(String value) {
        String clean = value.strip().toLowerCase(Locale.ROOT);
        if (clean.startsWith(".")) clean = clean.substring(1);
        if (!clean.matches("[a-z0-9][a-z0-9_+\\-]{0,31}")) throw new IllegalArgumentException("Invalid file extension: " + value);
        return clean;
    }
}
