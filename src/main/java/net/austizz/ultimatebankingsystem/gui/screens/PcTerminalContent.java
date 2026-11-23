package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppNotification;
import net.austizz.ultimatebankingsystem.api.pc.PcClipboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PcTerminalContent extends PcUtilityContent {
    private static final ResourceLocation MONO = ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem", "pc_terminal_mono");
    private static final int CONSOLE = 0xFF101824, PROMPT = 0xFF86E7CA;
    private static final int MAX_TABS = 5, MAX_OUTPUT = 400;
    private final PcFileSystem drive;
    private PcShell.Open open, edit;
    private PcShell.Launch launch;
    private Supplier<List<String>> apps;
    private final List<Tab> tabs = new ArrayList<>();
    private final net.minecraft.client.gui.components.EditBox input = field("Enter a command", 1024);
    private Consumer<PcAppNotification> notify = ignored -> {};
    private int active;
    private boolean focused;

    private record Line(String text, int color) {}
    private record Rendered(FormattedCharSequence text, int color) {}

    private final class Tab {
        final List<Line> transcript = new ArrayList<>();
        final PcShell shell;
        List<Rendered> rendered = List.of();
        int renderWidth, scroll, historyPointer;
        boolean dirty = true;
        String input = "", draft = "";

        Tab() {
            var manager = Minecraft.getInstance().keyboardHandler;
            shell = new PcShell(drive, services.clipboard(), this::output, this::clear, entry -> open.run(entry),
                    entry -> edit.run(entry), id -> launch.run(id), () -> apps.get(),
                    message -> notify.accept(new PcAppNotification("PC Terminal", message)),
                    ModList.get().isLoaded("computercraft"), manager::getClipboard, manager::setClipboard);
            output("PC Terminal · Type help for commands.");
        }

        void output(String line) {
            for (String segment : line.split("\\R", -1)) {
                if (transcript.size() == MAX_OUTPUT) transcript.removeFirst();
                String printable = segment.replace("\t", "    ").codePoints()
                        .filter(ch -> !Character.isISOControl(ch))
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
                transcript.add(new Line(printable, printable.startsWith("Error:") ? ERROR : TEXT));
            }
            dirty = true;
            scroll = 0;
        }

        void clear() {
            transcript.clear();
            dirty = true;
            scroll = 0;
        }

        List<Rendered> lines(int width) {
            if (!dirty && width == renderWidth) return rendered;
            var result = new ArrayList<Rendered>();
            for (var line : transcript) {
                if (line.text.isEmpty()) result.add(new Rendered(FormattedCharSequence.EMPTY, line.color));
                else for (var row : font.split(mono(line.text), Math.max(1, width))) result.add(new Rendered(row, line.color));
            }
            rendered = result;
            renderWidth = width;
            dirty = false;
            return rendered;
        }
    }

    PcTerminalContent(PcFileSystem drive, PcShell.Open open, PcShell.Open edit, PcShell.Launch launch, Supplier<List<String>> apps) {
        this.drive = drive;
        input.setHint(mono("Enter a command"));
        input.setFormatter((value, offset) -> mono(value).getVisualOrderText());
        handlers(open, edit, launch, apps);
    }

    void handlers(PcShell.Open open, PcShell.Open edit, PcShell.Launch launch, Supplier<List<String>> apps) {
        this.open = open;
        this.edit = edit;
        this.launch = launch;
        this.apps = apps;
    }

    @Override public void setServices(net.austizz.ultimatebankingsystem.api.pc.PcAppServices services) {
        super.setServices(services);
        if (tabs.isEmpty()) tabs.add(new Tab());
    }
    @Override public void setNotificationPublisher(Consumer<PcAppNotification> publisher) { notify = publisher; }

    @Override public void openFile(UUID id) throws IOException {
        var entry = drive.get(id);
        if (entry.folder() || entry.binary() || !PcFileType.extension(entry.name()).equals("pcsh")) {
            throw new IOException("Choose a PC command script.");
        }
        loadInput("run \"" + drive.path(id) + "\"");
        tab().output("Review the command and press Enter to run the script.");
    }

    private Tab tab() { return tabs.get(active); }

    private void loadInput(String value) {
        input.setValue(value);
        input.moveCursorToEnd(false);
        input.setFocused(focused);
    }

    private void switchTab(int index) {
        if (index == active) return;
        tab().input = input.getValue();
        active = index;
        loadInput(tab().input);
    }

    private void addTab() {
        if (tabs.size() == MAX_TABS) return;
        tabs.add(new Tab());
        switchTab(tabs.size() - 1);
    }

    private void closeTab() {
        if (tabs.size() == 1) {
            tabs.set(0, new Tab());
            loadInput("");
            return;
        }
        tabs.remove(active);
        active = Math.min(active, tabs.size() - 1);
        loadInput(tab().input);
    }

    @Override public void focusChanged(boolean focused) {
        this.focused = focused;
        input.setFocused(focused);
    }

    private void execute() {
        var session = tab();
        String command = input.getValue();
        if (command.isBlank()) return;
        session.output(session.shell.prompt() + command);
        loadInput("");
        session.shell.run(command);
        session.historyPointer = session.shell.history.size();
        session.draft = "";
    }

    private void recall(int delta) {
        var session = tab();
        var history = session.shell.history;
        int index = Math.clamp(session.historyPointer + delta, 0, history.size());
        if (session.historyPointer == history.size()) session.draft = input.getValue();
        session.historyPointer = index;
        loadInput(index == history.size() ? session.draft : history.get(index));
    }

    private void complete() {
        var matches = tab().shell.completions(input.getValue());
        if (matches.size() == 1) loadInput(matches.getFirst());
        else if (!matches.isEmpty()) {
            String prefix = matches.getFirst();
            for (var candidate : matches) {
                while (!candidate.startsWith(prefix) && !prefix.isEmpty()) prefix = prefix.substring(0, prefix.length() - 1);
            }
            if (prefix.length() > input.getValue().length()) loadInput(prefix);
            else tab().output(String.join("    ", matches));
        }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        background(g);
        g.fill(x, y + 30, x + w, y + h, CONSOLE);
        int step = Math.min(92, (w - 46) / Math.max(3, tabs.size()));
        for (int i = 0; i < tabs.size(); i++) {
            int index = i;
            button(g, x + 7 + step * i, y + 6, step - 3, 19, "Shell " + (i + 1), () -> switchTab(index), mx, my, i == active);
        }
        button(g, x + 7 + step * tabs.size(), y + 6, 20, 19, "+", tabs.size() < MAX_TABS ? this::addTab : null, mx, my, false);
        button(g, x + w - 25, y + 6, 19, 19, "×", this::closeTab, mx, my, false);
        var session = tab();
        var lines = session.lines(w - 24);
        int capacity = visibleRows();
        int max = Math.max(0, lines.size() - capacity);
        session.scroll = Math.clamp(session.scroll, 0, max);
        int first = max - session.scroll;
        g.enableScissor(x + 2, y + 32, x + w - 2, y + h - 27);
        for (int i = 0; i < Math.min(capacity, lines.size() - first); i++) {
            var row = lines.get(first + i);
            g.drawString(font, row.text, x + 9, y + 36 + i * 12, row.color, false);
        }
        g.disableScissor();
        if (max > 0) {
            int track = Math.max(1, h - 67);
            int thumb = Math.max(7, track * capacity / lines.size());
            int yy = y + 35 + (track - thumb) * (max - session.scroll) / max;
            g.fill(x + w - 7, yy, x + w - 4, yy + thumb, LINE);
        }
        String prompt = session.shell.prompt();
        int full = font.width(mono(prompt));
        int limit = Math.max(45, w / 2);
        if (full > limit) prompt = "…" + prompt.substring(Math.max(0, prompt.length() - 24));
        int promptWidth = Math.min(limit, font.width(mono(prompt)) + 2);
        var shownPrompt = net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(mono(prompt), promptWidth));
        g.drawString(font, shownPrompt, x + 9, y + h - 17, PROMPT, false);
        drawField(g, input, x + 11 + promptWidth, y + h - 22, w - promptWidth - 20, mx, my, partial);
    }

    private static Component mono(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(MONO));
    }

    private int visibleRows() { return Math.max(1, (h - 62) / 12); }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        tab().scroll = Math.max(0, tab().scroll + (dy > 0 ? 3 : -3));
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control) {
            switch (key) {
                case GLFW.GLFW_KEY_T -> { addTab(); return true; }
                case GLFW.GLFW_KEY_W -> { closeTab(); return true; }
                case GLFW.GLFW_KEY_L -> { tab().clear(); return true; }
                case GLFW.GLFW_KEY_TAB -> { switchTab((active + 1) % tabs.size()); return true; }
                case GLFW.GLFW_KEY_V -> {
                    var item = services.clipboard().get();
                    if (item instanceof PcClipboard.Text text) input.insertText(text.text().replaceAll("\\R", " "));
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    String text = input.getHighlighted();
                    if (!text.isEmpty()) services.clipboard().set(new PcClipboard.Text(text));
                    else loadInput("");
                    return true;
                }
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> execute();
            case GLFW.GLFW_KEY_TAB -> complete();
            case GLFW.GLFW_KEY_UP -> recall(-1);
            case GLFW.GLFW_KEY_DOWN -> recall(1);
            case GLFW.GLFW_KEY_PAGE_UP -> tab().scroll += visibleRows();
            case GLFW.GLFW_KEY_PAGE_DOWN -> tab().scroll = Math.max(0, tab().scroll - visibleRows());
            case GLFW.GLFW_KEY_F1 -> tab().shell.run("help");
            default -> { return input.keyPressed(key, scan, modifiers); }
        }
        return true;
    }

    @Override public boolean charTyped(char c, int modifiers) { return input.charTyped(c, modifiers); }
}
