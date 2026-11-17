package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Compact client representation of one job-sale catalog entry. */
public record JobCatalogEntryPayload(
        String itemId,
        String displayName,
        String family,
        String category,
        long quoteCents,
        int ownedCount,
        int rawUnitsMilli
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, JobCatalogEntryPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.itemId()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.displayName()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.family()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.category()));
                        LONG_CODEC.encode(buf, Math.max(0L, payload.quoteCents()));
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(0, payload.ownedCount()));
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(1, payload.rawUnitsMilli()));
                    },
                    buf -> new JobCatalogEntryPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)
                    )
            );

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
