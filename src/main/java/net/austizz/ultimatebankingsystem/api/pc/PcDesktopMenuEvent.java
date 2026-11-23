package net.austizz.ultimatebankingsystem.api.pc;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Posted on the client NeoForge game event bus when a player opens the PC desktop context menu.
 * Subscribe on Dist.CLIENT and register commands with {@link #add(Component, Runnable)}.
 * Entries are scoped to this opening and actions run on the Minecraft client thread.
 */
public final class PcDesktopMenuEvent extends Event {
    private final GlobalPos computer;
    private final List<Entry> entries = new ArrayList<>();
    private final List<AppEntry> apps = new ArrayList<>();

    public PcDesktopMenuEvent(GlobalPos computer) {
        this.computer = Objects.requireNonNull(computer);
    }

    public GlobalPos computer() {
        return computer;
    }

    public void add(Component label, Runnable action) {
        entries.add(new Entry(Objects.requireNonNull(label).copy(), Objects.requireNonNull(action)));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /**
     * Adds a window launcher to this desktop menu. Use an ID in your mod's namespace.
     * An already open window with the same ID is raised. Its options apply until closed.
     */
    public void addApp(ResourceLocation id, Component title, PcWindowOptions options) {
        apps.add(new AppEntry(Objects.requireNonNull(id), Objects.requireNonNull(title).copy(),
                Objects.requireNonNull(options)));
    }

    public List<AppEntry> apps() {
        return List.copyOf(apps);
    }

    public record Entry(Component label, Runnable action) {}

    public record AppEntry(ResourceLocation id, Component title, PcWindowOptions options) {}
}
