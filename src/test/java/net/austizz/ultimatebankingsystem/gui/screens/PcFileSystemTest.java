package net.austizz.ultimatebankingsystem.gui.screens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.DataOutputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static net.austizz.ultimatebankingsystem.gui.screens.PcFileSystem.DOCUMENTS;
import static net.austizz.ultimatebankingsystem.gui.screens.PcFileSystem.ROOT;

class PcFileSystemTest {
    @TempDir
    Path directory;

    @Test
    void addsPictureSamplesWithoutReplacingExistingFilesOrCreatingDuplicates() throws IOException {
        Path disk = directory.resolve("samples.drive");
        var drive = new PcFileSystem(disk);
        UUID pictures = drive.resolve(ROOT, "Pictures").id();
        byte[] existing = {1};
        drive.createBinary(pictures, "blue marble.jpg", existing);
        byte[] sample = {2};
        var samples = Map.of("Blue Marble.jpg", sample, "Earthrise.jpg", sample);
        drive.addPictureSamples(samples);

        var reloaded = new PcFileSystem(disk);
        reloaded.addPictureSamples(samples);
        assertEquals(2, reloaded.list(pictures, "").size());
        assertArrayEquals(existing, reloaded.resolve(pictures, "Blue Marble.jpg").payload());
        assertArrayEquals(sample, reloaded.resolve(pictures, "Earthrise.jpg").payload());
    }

    @Test
    void importsBinaryFilesWithoutChangingTheirBytesWhenCopiedOrRenamed() throws IOException {
        Path source = directory.resolve("Example.PNG");
        byte[] contents = {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xFF};
        Files.write(source, contents);
        Path disk = directory.resolve("import.drive");
        var drive = new PcFileSystem(disk);
        UUID id = drive.importFile(DOCUMENTS, source);
        assertEquals(PcFileType.PNG, PcFileType.iconFor(drive.get(id).name(), false));
        assertTrue(drive.get(id).binary());
        assertEquals(contents.length, drive.get(id).size());
        assertThrows(IOException.class, () -> drive.importFile(DOCUMENTS, source));
        assertThrows(IOException.class, () -> drive.write(id, "This must not replace binary data."));

        drive.transfer(id, ROOT, false);
        drive.rename(id, "Saved image.png");
        drive.recycle(id);
        var reloaded = new PcFileSystem(disk);
        reloaded.restore(id);
        assertArrayEquals(contents, reloaded.resolve(ROOT, "c:\\example.png").payload());
        assertArrayEquals(contents, reloaded.get(id).payload());
        assertEquals("Saved image.png", reloaded.get(id).name());
    }

    @Test
    void importsUtf8SourceAsAnEditableDocument() throws IOException {
        Path source = Files.writeString(directory.resolve("Example.JSON"), "{ \"name\": \"value\" }\r\n");
        var drive = new PcFileSystem(directory.resolve("source.drive"));
        var entry = drive.get(drive.importFile(DOCUMENTS, source));
        assertFalse(entry.binary());
        assertEquals(Files.readString(source), entry.text());
        assertEquals(Files.size(source), entry.size());
        assertEquals(PcFileType.JSON, PcFileType.iconFor(entry.name(), entry.empty()));
        assertEquals("JSON file", PcFileType.label(entry.name()));
    }

    @Test
    void fileTypesHaveTransparentTexturesAndRecognizeVariants() throws IOException {
        for (var type : PcFileType.values()) {
            assertEquals(type, PcFileType.iconFor("Sample." + type.icon.toUpperCase(java.util.Locale.ROOT), false));
            assertFileTexture(type, "", PcDesktopScreen.FILE_ICON_PIXELS, 0xFFFFFFFF);
            assertFileTexture(type, "dark/", PcDesktopScreen.FILE_ICON_PIXELS, 0xFF252A34);
            assertFileTexture(type, "high_res/", PcDesktopScreen.HIGH_RES_FILE_ICON_PIXELS, 0xFFFFFFFF);
            assertFileTexture(type, "dark/high_res/", PcDesktopScreen.HIGH_RES_FILE_ICON_PIXELS, 0xFF252A34);
        }
        assertEquals(PcFileType.VIDEO_01, PcFileType.iconFor("movie.MOV", false));
        assertEquals(PcFileType.EMPTY, PcFileType.iconFor("Untitled", true));
        assertEquals(PcFileType.DOCUMENT, PcFileType.iconFor("file.unknown", false));
    }

    private void assertFileTexture(PcFileType type, String directory, int pixels, int pageFill) throws IOException {
        String path = "/assets/ultimatebankingsystem/textures/gui/pc/file_types/" + directory + type.icon + ".png";
        try (var texture = getClass().getResourceAsStream(path)) {
            assertNotNull(texture, path);
            var image = ImageIO.read(texture);
            assertEquals(pixels, image.getWidth(), path);
            assertEquals(pixels, image.getHeight(), path);
            assertTrue(image.getColorModel().hasAlpha(), path);
            assertEquals(0, image.getRGB(0, 0) >>> 24, path);
            assertEquals(pageFill, image.getRGB(pixels * 18 / 40, pixels / 5), path + " page fill");
        }
    }

    @Test
    void resolvesCanonicalAndRelativePathsAgainstTheVirtualDrive() throws IOException {
        var drive = new PcFileSystem(directory.resolve("paths.drive"));
        var folder = drive.create(DOCUMENTS, "Saved Photos", true, "");
        var file = drive.create(folder, "Notes.txt", false, "content");

        assertEquals("C:\\", drive.path(ROOT));
        assertEquals("C:\\Documents\\Saved Photos\\Notes.txt", drive.path(file));
        assertEquals(List.of(ROOT, DOCUMENTS, folder, file),
                drive.pathEntries(file).stream().map(PcFileSystem.Entry::id).toList());
        assertEquals(file, drive.resolve(ROOT, "\"c:/DOCUMENTS/saved photos/NOTES.TXT\"").id());
        assertEquals(folder, drive.resolve(folder, "..\\Saved Photos\\.\\").id());
        assertEquals(DOCUMENTS, drive.resolve(folder, "\\Documents").id());
        assertEquals(ROOT, drive.resolve(ROOT, "..\\..").id());
        assertThrows(IOException.class, () -> drive.resolve(ROOT, "D:\\Documents"));
        assertThrows(IOException.class, () -> drive.resolve(ROOT, "C:Documents"));
        assertThrows(IOException.class, () -> drive.resolve(ROOT, "\\\\host\\share"));
        assertThrows(IOException.class, () -> drive.resolve(folder, "Notes.txt\\.."));

        drive.rename(folder, "Pictures");
        drive.transfer(file, ROOT, true);
        assertEquals("C:\\Notes.txt", drive.path(file));
        assertThrows(IOException.class, () -> drive.resolve(ROOT, "C:\\Documents\\Saved Photos"));
        drive.recycle(file);
        assertThrows(IOException.class, () -> drive.resolve(ROOT, "C:\\Notes.txt"));
        assertThrows(IOException.class, () -> drive.path(file));
    }

    @Test
    void upgradesTheExistingDesktopAndKeepsShortcutTargetsSeparateFromTheirNames() throws IOException {
        Path disk = directory.resolve("legacy.drive");
        var desktop = UUID.randomUUID();
        var note = UUID.randomUUID();
        try (var out = new DataOutputStream(Files.newOutputStream(disk))) {
            out.writeInt(0x55424601);
            out.writeInt(3);
            writeLegacy(out, DOCUMENTS, ROOT, "Documents", true, "");
            writeLegacy(out, desktop, ROOT, "Desktop", true, "");
            writeLegacy(out, note, desktop, "Readme.txt", false, "Original text");
        }

        var drive = new PcFileSystem(disk);
        drive.initializeDesktop(Map.of("ubs:files", "Files"));
        assertEquals(desktop, drive.desktopFolder());
        assertEquals("Original text", drive.get(note).text());
        var link = drive.list(desktop, "").stream().filter(PcFileSystem.Entry::shortcut).findFirst().orElseThrow();
        drive.rename(link.id(), "My files");
        drive.transfer(link.id(), DOCUMENTS, false);
        assertEquals("ubs:files", drive.list(DOCUMENTS, "").getFirst().appTarget());
        drive.recycle(link.id());
        assertTrue(drive.trash().getFirst().shortcut());
        drive.restore(link.id());
        assertEquals("My files", drive.get(link.id()).name());
        drive.recycle(link.id());
        drive.emptyTrash();

        var reopened = new PcFileSystem(disk);
        reopened.initializeDesktop(Map.of("ubs:files", "Files"));
        assertEquals(List.of(note), reopened.list(desktop, "").stream().map(PcFileSystem.Entry::id).toList());
        assertEquals("ubs:files", reopened.list(DOCUMENTS, "").getFirst().appTarget());
        assertThrows(IOException.class, () -> reopened.recycle(desktop));
    }

    private static void writeLegacy(DataOutputStream out, UUID id, UUID parent, String name,
                                    boolean folder, String text) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
        out.writeLong(parent.getMostSignificantBits());
        out.writeLong(parent.getLeastSignificantBits());
        out.writeUTF(name);
        out.writeBoolean(folder);
        out.writeUTF(text);
        out.writeLong(0);
        out.writeBoolean(false);
    }

    @Test
    void documentsKeepTheirIdentityThroughRenameMoveRecycleAndReload() throws IOException {
        Path file = directory.resolve("desktop.drive");
        var drive = new PcFileSystem(file);
        var work = drive.create(DOCUMENTS, "Work", true, "");
        var note = drive.create(DOCUMENTS, "Hello.txt", false, "line 1\nline 2");
        drive.rename(note, "HELLO.txt");
        drive.transfer(note, work, true);
        drive.recycle(work);

        var reopened = new PcFileSystem(file);
        assertThrows(IOException.class, () -> reopened.get(note));
        assertEquals(List.of(work), reopened.trash().stream().map(PcFileSystem.Entry::id).toList());
        reopened.restore(work);
        assertEquals(work, reopened.get(note).parent());
        assertEquals("HELLO.txt", reopened.get(note).name());
        assertEquals("line 1\nline 2", reopened.get(note).text());
        assertEquals(List.of(note), reopened.list(DOCUMENTS, "hello").stream().map(PcFileSystem.Entry::id).toList());
    }

    @Test
    void collisionsAndInvalidMovesDoNotChangeTheDrive() throws IOException {
        var drive = new PcFileSystem(directory.resolve("desktop.drive"));
        var a = drive.create(DOCUMENTS, "Folder", true, "");
        var b = drive.create(a, "Subfolder", true, "");
        var note = drive.create(a, "Report.txt", false, "original");
        assertThrows(IOException.class, () -> drive.create(a, "REPORT.txt", false, "replacement"));
        assertThrows(IOException.class, () -> drive.create(a, "../escape", false, ""));
        assertThrows(IOException.class, () -> drive.create(a, "NUL.txt", false, ""));
        assertThrows(IOException.class, () -> drive.transfer(a, b, true));
        assertThrows(IOException.class, () -> drive.transfer(a, b, false));
        assertEquals("original", drive.get(note).text());
        assertEquals(DOCUMENTS, drive.get(a).parent());
    }

    @Test
    void copiesAreIndependentAndEmptyingTrashRemovesTheWholeTree() throws IOException {
        var drive = new PcFileSystem(directory.resolve("desktop.drive"));
        var folder = drive.create(DOCUMENTS, "Folder", true, "");
        var notes = drive.create(folder, "Notes.txt", false, "draft");
        drive.transfer(folder, ROOT, false);
        var copy = drive.list(ROOT, "").stream().filter(item -> item.name().equals("Folder")).findFirst().orElseThrow();
        var copiedNotes = drive.list(copy.id(), "").getFirst();
        assertNotEquals(notes, copiedNotes.id());
        drive.write(copiedNotes.id(), "copy only");
        assertEquals("draft", drive.get(notes).text());
        drive.recycle(folder);
        drive.emptyTrash();
        assertTrue(drive.trash().isEmpty());
        assertThrows(IOException.class, () -> drive.restore(folder));
        assertEquals("copy only", drive.get(copiedNotes.id()).text());
    }

    @Test
    void corruptDriveIsNeverReplacedByAnEmptyOne() throws IOException {
        Path path = directory.resolve("desktop.drive");
        byte[] invalid = {1, 2, 3, 4, 5, 6, 7, 8};
        Files.write(path, invalid);
        var drive = new PcFileSystem(path);
        assertThrows(IOException.class, () -> drive.create(DOCUMENTS, "Report.txt", false, ""));
        assertArrayEquals(invalid, Files.readAllBytes(path));
    }
}
