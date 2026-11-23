package net.austizz.ultimatebankingsystem.api.pc;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Posted on the client game event bus when a signed-in PC desktop is created. */
public final class PcAppRegistrationEvent extends Event {
    private final GlobalPos computer;
    private final LinkedHashMap<ResourceLocation, java.util.Set<String>> fileHandlers = new LinkedHashMap<>();
    private final LinkedHashMap<ResourceLocation, App> apps = new LinkedHashMap<>();

    public PcAppRegistrationEvent(GlobalPos computer) {
        this.computer = Objects.requireNonNull(computer);
    }

    public GlobalPos computer() {
        return computer;
    }

    /** The factory creates a separate content instance each time a new window opens. */
    public void register(ResourceLocation id, Component title, PcWindowOptions options,
                         Supplier<? extends PcAppContent> factory) {
        register(id, title, null, options, factory);
    }

    /** The optional icon is a full PNG texture resource, including its textures/ prefix and .png suffix. */
    public void register(ResourceLocation id, Component title, ResourceLocation icon, PcWindowOptions options,
                         Supplier<? extends PcAppContent> factory) {
        apps.putIfAbsent(Objects.requireNonNull(id), new App(id, Objects.requireNonNull(title).copy(),
                icon, Objects.requireNonNull(options), Objects.requireNonNull(factory)));
    }

    /** Register literal extensions such as "txt" or ".png" for this app. */
    public void registerFileHandler(ResourceLocation app, String... extensions) {
        var normalized = java.util.Arrays.stream(extensions).map(PcFileDialog::extension).toList();
        var handlers = fileHandlers.computeIfAbsent(Objects.requireNonNull(app), id -> new java.util.LinkedHashSet<>());
        handlers.addAll(normalized);
    }

    public java.util.Map<ResourceLocation, java.util.Set<String>> fileHandlers() {
        var snapshot = new LinkedHashMap<ResourceLocation, java.util.Set<String>>();
        fileHandlers.forEach((app, extensions) -> snapshot.put(app, java.util.Set.copyOf(extensions)));
        return java.util.Collections.unmodifiableMap(snapshot);
    }

    public List<App> apps() {
        return List.copyOf(apps.values());
    }

    public record App(ResourceLocation id, Component title, ResourceLocation icon, PcWindowOptions options,
                      Supplier<? extends PcAppContent> factory) {}
}
