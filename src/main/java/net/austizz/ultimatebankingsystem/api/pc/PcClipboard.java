package net.austizz.ultimatebankingsystem.api.pc;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One current clipboard item shared by apps on the signed-in PC. */
public interface PcClipboard {
    sealed interface Item permits Text, Image, Files {}
    record Text(String text) implements Item {
        public Text { Objects.requireNonNull(text); }
    }
    record Image(String name, byte[] bytes) implements Item {
        public Image {
            Objects.requireNonNull(name);
            if (bytes.length > 16 * 1024 * 1024) throw new IllegalArgumentException("Image is too large.");
            bytes = bytes.clone();
        }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    record Files(List<UUID> ids, boolean cut) implements Item {
        public Files {
            ids = ids.stream().distinct().toList();
            if (ids.isEmpty()) throw new IllegalArgumentException("Select files to place on the clipboard.");
        }
    }

    Item get();
    void set(Item item);
    default void clear() { set(null); }
}
