package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcClipboard;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PcShell {
    @FunctionalInterface interface Open { void run(PcFileSystem.Entry entry) throws IOException; }
    @FunctionalInterface interface Launch { void run(String app) throws IOException; }
    static final List<String> COMMANDS = List.of("alias", "apps", "cat", "cc", "cd", "clear", "clip", "copy", "df", "echo", "edit",
            "find", "grep", "head", "help", "history", "launch", "list", "mkdir", "move", "notify", "open", "pwd", "recycle",
            "rename", "run", "stat", "time", "touch", "tree", "write", "append");

    private final PcFileSystem drive;
    private final PcClipboard clipboard;
    private final Consumer<String> out;
    private final Runnable clear;
    private final Open open, edit;
    private final Launch launch;
    private final Supplier<List<String>> apps;
    private final Consumer<String> notice;
    private final Supplier<String> hostPaste;
    private final Consumer<String> hostCopy;
    private final boolean ccInstalled;
    final List<String> history = new ArrayList<>();
    final Map<String, String> aliases = new LinkedHashMap<>(Map.of("ls", "list", "dir", "list", "type", "cat", "cls", "clear",
            "cp", "copy", "mv", "move", "rm", "recycle"));
    private UUID cwd = PcFileSystem.DOCUMENTS;

    PcShell(PcFileSystem drive, PcClipboard clipboard, Consumer<String> out, Runnable clear, Open open,
            Open edit, Launch launch, Supplier<List<String>> apps, Consumer<String> notice, boolean ccInstalled,
            Supplier<String> hostPaste, Consumer<String> hostCopy) {
        this.drive = drive;
        this.clipboard = clipboard;
        this.out = out;
        this.clear = clear;
        this.open = open;
        this.edit = edit;
        this.launch = launch;
        this.apps = apps;
        this.notice = notice;
        this.ccInstalled = ccInstalled;
        this.hostPaste = hostPaste;
        this.hostCopy = hostCopy;
    }

    String prompt() {
        try { return drive.path(cwd) + "> "; }
        catch (IOException e) { cwd = PcFileSystem.ROOT; return "C:\\> "; }
    }

    void run(String line) {
        if (line.isBlank()) return;
        if (history.isEmpty() || !history.getLast().equals(line)) history.add(line);
        if (history.size() > 100) history.removeFirst();
        try { execute(line, false); }
        catch (IOException | IllegalArgumentException e) { out.accept("Error: " + e.getMessage()); }
    }

    private void execute(String line, boolean script) throws IOException {
        var parts = tokenize(line);
        if (parts.isEmpty()) return;
        String verb = parts.removeFirst().toLowerCase(Locale.ROOT);
        verb = aliases.getOrDefault(verb, verb);
        switch (verb) {
            case "help" -> help(parts.isEmpty() ? "" : parts.getFirst());
            case "pwd" -> out.accept(drive.path(cwd));
            case "list" -> listing(parts);
            case "cd" -> cwd = parts.isEmpty() ? PcFileSystem.DOCUMENTS : folder(parts.getFirst()).id();
            case "tree" -> tree(parts.isEmpty() ? drive.get(cwd) : folder(parts.getFirst()), 0, new int[]{0});
            case "cat" -> out.accept(text(arg(parts, 0, "cat <file>")));
            case "head" -> text(arg(parts, 0, "head <file>")).lines().limit(10).forEach(out);
            case "find" -> {
                String query = arg(parts, 0, "find <name>");
                var results = drive.list(cwd, query).stream().filter(item -> !item.shortcut()).limit(100).toList();
                for (var result : results) out.accept(drive.path(result.id()));
                if (results.isEmpty()) out.accept("No matching items.");
            }
            case "grep" -> {
                String query = arg(parts, 0, "grep <text> <file>").toLowerCase(Locale.ROOT);
                var lines = text(arg(parts, 1, "grep <text> <file>")).split("\\R");
                int matches = 0;
                for (int i = 0; i < lines.length && matches < 100; i++) {
                    if (lines[i].toLowerCase(Locale.ROOT).contains(query)) { out.accept((i + 1) + "  " + lines[i]); matches++; }
                }
                if (matches == 0) out.accept("No matching lines.");
            }
            case "mkdir" -> create(arg(parts, 0, "mkdir <folder>"), true, "");
            case "touch" -> create(arg(parts, 0, "touch <file>"), false, "");
            case "copy", "move" -> drive.transfer(resolve(arg(parts, 0, verb + " <item> <folder>")).id(),
                    folder(arg(parts, 1, verb + " <item> <folder>")).id(), verb.equals("move"));
            case "rename" -> drive.rename(resolve(arg(parts, 0, "rename <item> <name>")).id(), arg(parts, 1, "rename <item> <name>"));
            case "recycle" -> {
                drive.recycle(resolve(arg(parts, 0, "recycle <item>")).id());
                out.accept("Moved to Recycle Bin.");
            }
            case "stat" -> {
                var entry = resolve(arg(parts, 0, "stat <item>"));
                out.accept(drive.path(entry.id()));
                out.accept(entry.folder() ? "Folder" : PcFileType.label(entry.name()) + " · " + entry.size() + " bytes");
            }
            case "write", "append" -> {
                var file = resolve(arg(parts, 0, verb + " <file> <text>"));
                String value = String.join(" ", parts.subList(1, parts.size()));
                String current = verb.equals("append") ? text(drive.path(file.id())) : "";
                drive.write(file.id(), current + value);
            }
            case "echo" -> out.accept(String.join(" ", parts));
            case "open" -> open.run(resolve(arg(parts, 0, "open <item>")));
            case "edit" -> {
                String path = arg(parts, 0, "edit <file>");
                var item = resolveOrNull(path);
                if (item == null) item = drive.get(create(path, false, ""));
                edit.run(item);
            }
            case "apps" -> apps.get().forEach(out);
            case "launch" -> launch.run(String.join(" ", parts));
            case "clear" -> clear.run();
            case "history" -> {
                for (int i = 0; i < history.size(); i++) out.accept((i + 1) + "  " + history.get(i));
            }
            case "alias" -> alias(parts);
            case "notify" -> {
                if (parts.isEmpty()) throw new IOException("Usage: notify <message>");
                notice.accept(String.join(" ", parts));
            }
            case "time" -> out.accept(LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")));
            case "df" -> {
                long bytes = drive.searchableEntries().stream().mapToLong(PcFileSystem.Entry::size).sum();
                out.accept(bytes + " bytes of live file content · 16 MiB virtual-drive limit");
            }
            case "clip" -> clipboard(parts);
            case "run" -> script(parts, script);
            case "cc" -> {
                if (script && !parts.isEmpty() && List.of("copy", "import").contains(parts.getFirst())) {
                    throw new IOException("Use CC clipboard transfer interactively.");
                }
                cc(parts);
            }
            default -> throw new IOException("Unknown command: " + verb + ". Try help.");
        }
    }

    private void help(String command) {
        String hint = switch (command) {
            case "run" -> "run <file.pcsh> · One PC command per line, # for comments. Stops on the first error.";
            case "cc" -> "cc status | cc new <file.lua> | cc import <file.lua> | cc copy <file.lua>";
            case "clip" -> "clip get | clip set <text> | clip clear";
            case "alias" -> "alias [name command] · List aliases or make one for a built-in command.";
            case "files" -> "list, cd, pwd, tree, find, cat, head, grep, stat, mkdir, touch, copy, move, rename, recycle";
            default -> "";
        };
        if (!hint.isEmpty()) { out.accept(hint); return; }
        out.accept("Files    list · cd · pwd · tree · find · cat · head · grep · stat");
        out.accept("Manage   mkdir · touch · copy · move · rename · recycle · write · append");
        out.accept("Desktop  apps · launch · open · edit · clip · notify · time · df");
        out.accept("Shell    alias · history · clear · run · cc");
        out.accept("Try help run or help cc. Tab completes commands and paths. ↑/↓ recalls history.");
    }

    private void listing(List<String> args) throws IOException {
        UUID folder = args.isEmpty() ? cwd : folder(args.getFirst()).id();
        var items = drive.list(folder, "");
        if (items.isEmpty()) out.accept("(empty)");
        else items.stream().limit(100).forEach(item -> out.accept((item.folder() ? "[dir]  " : "       ") + item.name()));
        if (items.size() > 100) out.accept("Showing 100 of " + items.size() + " items.");
    }

    private void tree(PcFileSystem.Entry folder, int level, int[] printed) throws IOException {
        if (level == 0) out.accept(drive.path(folder.id()));
        if (level >= 4) return;
        for (var item : drive.list(folder.id(), "")) {
            if (printed[0] >= 100) return;
            out.accept("  ".repeat(level + 1) + item.name() + (item.folder() ? "/" : ""));
            printed[0]++;
            if (item.folder()) tree(item, level + 1, printed);
        }
    }

    private void clipboard(List<String> parts) throws IOException {
        switch (parts.isEmpty() ? "get" : parts.getFirst()) {
            case "get" -> {
                switch (clipboard.get()) {
                    case PcClipboard.Text text -> out.accept(text.text());
                    case PcClipboard.Image image -> out.accept("Image: " + image.name());
                    case PcClipboard.Files files -> out.accept(files.ids().size() + (files.cut() ? " cut" : " copied") + " items");
                    case null -> out.accept("Clipboard is empty.");
                }
            }
            case "set" -> clipboard.set(new PcClipboard.Text(String.join(" ", parts.subList(1, parts.size()))));
            case "clear" -> clipboard.clear();
            default -> throw new IOException("Usage: clip get | set <text> | clear");
        }
    }

    private void alias(List<String> parts) throws IOException {
        if (parts.isEmpty()) {
            aliases.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> out.accept(e.getKey() + " = " + e.getValue()));
            return;
        }
        String name = parts.getFirst().toLowerCase(Locale.ROOT);
        String target = arg(parts, 1, "alias <name> <command>").toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z][a-z0-9_-]{0,23}") || COMMANDS.contains(name) || !COMMANDS.contains(target)) {
            throw new IOException("Choose an unused alias for a built-in command.");
        }
        if (!aliases.containsKey(name) && aliases.size() >= 64) throw new IOException("Too many aliases in this tab.");
        aliases.put(name, target);
    }

    private void script(List<String> parts, boolean insideScript) throws IOException {
        if (insideScript) throw new IOException("Scripts cannot call other scripts.");
        String file = arg(parts, 0, "run <file.pcsh>");
        if (!PcFileType.extension(file).equals("pcsh")) throw new IOException("Choose a .pcsh command script.");
        var lines = text(file).split("\\R");
        if (lines.length > 128) throw new IOException("PC command scripts may contain up to 128 lines.");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isBlank() || line.startsWith("#")) continue;
            try { execute(line, true); }
            catch (IOException | IllegalArgumentException e) { throw new IOException("Line " + (i + 1) + ": " + e.getMessage(), e); }
        }
        out.accept("Script finished.");
    }

    private void cc(List<String> parts) throws IOException {
        String command = parts.isEmpty() ? "status" : parts.getFirst();
        if (command.equals("status")) {
            out.accept(ccInstalled ? "CC:Tweaked is installed." : "CC:Tweaked is not installed. Lua source tools are still available.");
            out.accept("Move Lua source with cc copy or cc import. Run it on your own CraftOS computer.");
            return;
        }
        String file = arg(parts, 1, "cc " + command + " <file.lua>");
        if (!PcFileType.extension(file).equals("lua")) throw new IOException("Use a .lua file.");
        switch (command) {
            case "new" -> {
                create(file, false, "print(\"Hello from CraftOS!\")\n");
                out.accept("Lua file created. Use edit to open it.");
            }
            case "import" -> {
                String source = hostPaste.get();
                if (source.isBlank()) throw new IOException("Copy Lua source to your system clipboard first.");
                create(file, false, source);
                out.accept("Lua source imported into the PC drive.");
            }
            case "copy" -> {
                String source = text(file);
                clipboard.set(new PcClipboard.Text(source));
                hostCopy.accept(source);
                out.accept("Lua source copied. Paste into your editor to save a .lua file for CraftOS.");
            }
            default -> throw new IOException("Usage: cc status | new | import | copy");
        }
    }

    private String text(String path) throws IOException {
        var entry = resolve(path);
        if (entry.folder() || entry.binary() || entry.shortcut()) throw new IOException("Choose a text document.");
        return entry.text();
    }

    private PcFileSystem.Entry resolve(String path) throws IOException { return drive.resolve(cwd, path); }

    private PcFileSystem.Entry resolveOrNull(String path) {
        try { return resolve(path); } catch (IOException e) { return null; }
    }

    private PcFileSystem.Entry folder(String path) throws IOException {
        var entry = resolve(path);
        if (!entry.folder()) throw new IOException("Choose a folder.");
        return entry;
    }

    private UUID create(String path, boolean directory, String value) throws IOException {
        String normalized = path.replace('/', '\\');
        int slash = normalized.lastIndexOf('\\');
        var parent = slash == -1 ? cwd : folder(normalized.substring(0, slash + 1)).id();
        return drive.create(parent, normalized.substring(slash + 1), directory, value);
    }

    private static String arg(List<String> args, int index, String usage) throws IOException {
        if (index >= args.size() || args.get(index).isBlank()) throw new IOException("Usage: " + usage);
        return args.get(index);
    }

    List<String> completions(String line) {
        int split = line.indexOf(' ');
        if (split < 0) {
            String prefix = line.toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.concat(COMMANDS.stream(), aliases.keySet().stream())
                    .filter(item -> item.startsWith(prefix)).sorted().toList();
        }
        String base = line.substring(0, split + 1);
        String partial = line.substring(split + 1).replace("\"", "");
        int slash = Math.max(partial.lastIndexOf('/'), partial.lastIndexOf('\\'));
        String parent = slash < 0 ? "" : partial.substring(0, slash + 1);
        String needle = partial.substring(slash + 1).toLowerCase(Locale.ROOT);
        try {
            return drive.list(parent.isEmpty() ? cwd : folder(parent).id(), "").stream()
                    .filter(item -> item.name().toLowerCase(Locale.ROOT).startsWith(needle)).limit(16).map(item -> {
                        String path = parent + item.name() + (item.folder() ? "/" : "");
                        return base + (path.contains(" ") ? "\"" + path + "\"" : path);
                    }).toList();
        } catch (IOException e) { return List.of(); }
    }

    static ArrayList<String> tokenize(String line) throws IOException {
        var result = new ArrayList<String>();
        var token = new StringBuilder();
        char quote = 0;
        boolean started = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quote == 0 && (ch == '"' || ch == '\'')) { quote = ch; started = true; }
            else if (quote == ch) quote = 0;
            else if (quote == 0 && Character.isWhitespace(ch)) {
                if (started) { result.add(token.toString()); token.setLength(0); started = false; }
            } else {
                token.append(ch);
                started = true;
            }
        }
        if (quote != 0) throw new IOException("Close the quotation mark.");
        if (started) result.add(token.toString());
        return result;
    }
}
