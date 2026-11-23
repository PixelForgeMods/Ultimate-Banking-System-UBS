package net.austizz.ultimatebankingsystem.api.pc;

import java.io.IOException;
import java.util.UUID;

/** Services scoped to the signed-in PC and the app's registered identity. */
public interface PcAppServices {
    int API_VERSION = 1;

    PcDrive drive();
    PcDrive appData();
    PcClipboard clipboard();
    default void pick(PcFileDialog.Request request, PcFileDialog.Accepted accepted) {
        pick(request, accepted, () -> {});
    }
    void pick(PcFileDialog.Request request, PcFileDialog.Accepted accepted, Runnable cancelled);
    void open(UUID file) throws IOException;
}
