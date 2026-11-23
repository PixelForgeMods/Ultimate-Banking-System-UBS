package net.austizz.ultimatebankingsystem.pc;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class PcLoginSavedData extends SavedData {
    private static final String NAME = "ultimate_banking_system_pc_login";
    private static final SavedData.Factory<PcLoginSavedData> FACTORY =
            new SavedData.Factory<>(PcLoginSavedData::new, PcLoginSavedData::load, null);

    private final Map<UUID, Profile> profiles = new HashMap<>();
    final Set<UUID> pending = new HashSet<>();
    final Cache<UUID, Boolean> retryDelay = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofSeconds(10)).build();

    static PcLoginSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    Profile profile(UUID player) {
        return profiles.get(player);
    }

    void saveProfile(UUID player, Profile profile) {
        profiles.put(player, profile);
        setDirty();
    }

    static PcLoginSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PcLoginSavedData data = new PcLoginSavedData();
        for (Tag entry : tag.getList("profiles", Tag.TAG_COMPOUND)) {
            CompoundTag profile = (CompoundTag) entry;
            if (profile.hasUUID("player")) {
                data.profiles.put(profile.getUUID("player"), new Profile(
                        Credential.load(profile.getCompound("password")),
                        profile.contains("pin", Tag.TAG_COMPOUND) ? Credential.load(profile.getCompound("pin")) : null));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        profiles.forEach((player, profile) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.put("password", profile.password().save());
            if (profile.pin() != null) {
                entry.put("pin", profile.pin().save());
            }
            entries.add(entry);
        });
        tag.put("profiles", entries);
        return tag;
    }

    record Profile(Credential password, Credential pin) {}

    record Credential(byte[] salt, byte[] hash) {
        private static final SecureRandom RANDOM = new SecureRandom();

        static Credential create(String secret) {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            return new Credential(salt, derive(secret, salt));
        }

        boolean matches(String secret) {
            return salt.length == 16 && hash.length == 32 && MessageDigest.isEqual(hash, derive(secret, salt));
        }

        private static byte[] derive(String secret, byte[] salt) {
            char[] characters = secret.toCharArray();
            var spec = new PBEKeySpec(characters, salt, 600_000, 256);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("PC credential hashing is unavailable", e);
            } finally {
                Arrays.fill(characters, '\0');
                spec.clearPassword();
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putByteArray("salt", salt);
            tag.putByteArray("hash", hash);
            return tag;
        }

        private static Credential load(CompoundTag tag) {
            return new Credential(tag.getByteArray("salt"), tag.getByteArray("hash"));
        }
    }
}
