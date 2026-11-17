package net.austizz.ultimatebankingsystem.npc.skin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/** World-specific mapping between an NPC role and a skin from the shared skin library. */
public final class NpcSkinSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_banking_system_npc_skins";
    private final Map<String, String> assignments = new LinkedHashMap<>();
    private final Map<String, Boolean> slimModels = new LinkedHashMap<>();

    public static NpcSkinSavedData get(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            throw new IllegalArgumentException("A running server is required.");
        }
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static SavedData.Factory<NpcSkinSavedData> factory() {
        return new SavedData.Factory<>(NpcSkinSavedData::new, NpcSkinSavedData::load, null);
    }

    public String assignedSkinId(NpcSkinTarget target) {
        return target == null ? "" : assignments.getOrDefault(target.id(), "");
    }

    public void assign(NpcSkinTarget target, String skinId) {
        if (target == null) return;
        String normalized = skinId == null ? "" : skinId.trim();
        if (normalized.isBlank()) {
            assignments.remove(target.id());
        } else {
            assignments.put(target.id(), normalized);
        }
        setDirty();
    }

    public boolean usesSlimModel(NpcSkinTarget target) {
        return target != null && slimModels.getOrDefault(target.id(), target.defaultSlimModel());
    }

    public void setSlimModel(NpcSkinTarget target, boolean slim) {
        if (target == null) return;
        if (slim == target.defaultSlimModel()) {
            slimModels.remove(target.id());
        } else {
            slimModels.put(target.id(), slim);
        }
        setDirty();
    }

    private static NpcSkinSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcSkinSavedData data = new NpcSkinSavedData();
        CompoundTag stored = tag.getCompound("assignments");
        for (NpcSkinTarget target : NpcSkinTarget.values()) {
            String skinId = stored.getString(target.id());
            if (!skinId.isBlank()) data.assignments.put(target.id(), skinId);
        }
        CompoundTag storedModels = tag.getCompound("slim_models");
        for (NpcSkinTarget target : NpcSkinTarget.values()) {
            if (storedModels.contains(target.id())) {
                data.slimModels.put(target.id(), storedModels.getBoolean(target.id()));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag stored = new CompoundTag();
        assignments.forEach(stored::putString);
        tag.put("assignments", stored);
        CompoundTag storedModels = new CompoundTag();
        slimModels.forEach(storedModels::putBoolean);
        tag.put("slim_models", storedModels);
        return tag;
    }
}
