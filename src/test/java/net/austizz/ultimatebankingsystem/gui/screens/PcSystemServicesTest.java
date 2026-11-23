package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcClipboard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PcSystemServicesTest {
    @TempDir Path temp;

    @Test
    void driveHasStableReferencesAndPrivateAppStores() throws Exception {
        var path = temp.resolve("shared");
        var shared = new PcDriveView(new PcFileSystem(path));
        var appOne = new PcDriveView(new PcFileSystem(temp.resolve("app-one")));
        var appTwo = new PcDriveView(new PcFileSystem(temp.resolve("app-two")));
        var id = shared.createText(shared.root(), "report.txt", "original");
        var folder = shared.createFolder(shared.root(), "Reports");
        shared.move(id, folder);
        shared.rename(id, "updated.txt");
        shared.writeText(id, "updated");
        var reopened = new PcDriveView(new PcFileSystem(path));
        assertEquals(id, reopened.resolve(reopened.root(), "Reports/updated.txt").id());
        assertEquals("updated", reopened.readText(id));
        var state = appOne.createText(appOne.root(), "state.txt", "private");
        assertThrows(IOException.class, () -> appTwo.get(state));
        assertThrows(IOException.class, () -> shared.get(state));
        assertThrows(IOException.class, () -> shared.resolve(shared.root(), "D:/host.txt"));
    }

    @Test
    void clipboardCutIsAtomicAndImageDataIsASnapshot() throws Exception {
        var drive = new PcFileSystem(null);
        var clip = new PcClipboardState();
        var a = drive.create(PcFileSystem.ROOT, "a.txt", false, "a");
        var b = drive.create(PcFileSystem.ROOT, "b.txt", false, "b");
        var conflict = drive.create(PcFileSystem.DOCUMENTS, "b.txt", false, "existing");
        clip.set(new PcClipboard.Files(List.of(a, b), true));
        assertThrows(IOException.class, () -> PcClipboardState.paste(clip, drive, PcFileSystem.DOCUMENTS));
        assertEquals(PcFileSystem.ROOT, drive.get(a).parent());
        assertNotNull(clip.get());
        drive.recycle(conflict);
        PcClipboardState.paste(clip, drive, PcFileSystem.DOCUMENTS);
        assertEquals(PcFileSystem.DOCUMENTS, drive.get(a).parent());
        assertNull(clip.get());

        byte[] bytes = {1, 2, 3};
        var image = new PcClipboard.Image("clip.png", bytes);
        bytes[0] = 99;
        clip.set(image);
        PcClipboardState.paste(clip, drive, PcFileSystem.DOCUMENTS);
        PcClipboardState.paste(clip, drive, PcFileSystem.DOCUMENTS);
        assertArrayEquals(new byte[]{1, 2, 3}, drive.resolve(PcFileSystem.DOCUMENTS, "clip.png").payload());
        assertNotNull(drive.resolve(PcFileSystem.DOCUMENTS, "clip (2).png"));
    }
    @Test
    void searchRanksNamesAboveContentsAndSkipsDeletedFolders() throws Exception {
        var drive = new PcFileSystem(null);
        var content = drive.create(PcFileSystem.DOCUMENTS, "Document.txt", false, "Quarterly report");
        var title = drive.create(PcFileSystem.ROOT, "Quarterly report.txt", false, "");
        var removed = drive.create(PcFileSystem.ROOT, "Removed", true, "");
        drive.create(removed, "Quarterly backup.txt", false, "");
        drive.recycle(removed);
        drive.createDesktopShortcut("example:quarterly", "Quarterly shortcut");
        assertEquals(List.of(title, content), PcSearch.files(drive, "quarterly").stream()
                .map(result -> result.file().id()).toList());
    }
}
