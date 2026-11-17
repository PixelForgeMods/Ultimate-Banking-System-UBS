package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Client-safe summary of one Courier job route. */
public record CourierOrderPayload(
        String orderId,
        String shortId,
        String originName,
        String destinationName,
        String destinationDimension,
        int destinationX,
        int destinationY,
        int destinationZ,
        String itemId,
        String itemName,
        int quantity,
        long rewardCents,
        long secondsRemaining,
        String status,
        int routeDistanceBlocks,
        long marketValueCents,
        int moneyMultiplierPercent,
        int xpMultiplierPercent,
        String contractTier
) {
    private static final int MAX_TEXT_LENGTH = 96;

    /** Compatibility constructor for integrations using the original route summary. */
    public CourierOrderPayload(String orderId, String originName, String destinationName,
                               String itemId, String itemName, int quantity, long rewardCents,
                               long secondsRemaining, String status, int routeDistanceBlocks) {
        this(orderId, orderId == null ? "" : orderId.replace("-", "").substring(0, Math.min(8,
                        orderId.replace("-", "").length())).toUpperCase(java.util.Locale.ROOT),
                originName, destinationName, "", 0, 0, 0, itemId, itemName, quantity, rewardCents,
                secondsRemaining, status, routeDistanceBlocks, 0L, 100, 100, "STANDARD");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CourierOrderPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.clean(payload.orderId()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.clean(payload.shortId()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.clean(payload.originName()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.clean(payload.destinationName()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.clean(payload.destinationDimension()));
                        buf.writeInt(payload.destinationX());
                        buf.writeInt(payload.destinationY());
                        buf.writeInt(payload.destinationZ());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.clean(payload.itemId()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.clean(payload.itemName()));
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(1, Math.min(64, payload.quantity())));
                        buf.writeLong(Math.max(0L, payload.rewardCents()));
                        buf.writeLong(Math.max(0L, payload.secondsRemaining()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.clean(payload.status()));
                        ByteBufCodecs.VAR_INT.encode(buf, payload.routeDistanceBlocks());
                        buf.writeLong(Math.max(0L, payload.marketValueCents()));
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(50, Math.min(300,
                                payload.moneyMultiplierPercent())));
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(50, Math.min(300,
                                payload.xpMultiplierPercent())));
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.clean(payload.contractTier()));
                    },
                    buf -> new CourierOrderPayload(
                            readString(buf),
                            readString(buf),
                            readString(buf),
                            readString(buf),
                            readString(buf),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            readString(buf),
                            readString(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            buf.readLong(),
                            buf.readLong(),
                            readString(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            buf.readLong(),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            readString(buf)
                    )
            );

    public CourierOrderPayload {
        orderId = cleanText(orderId);
        shortId = cleanText(shortId);
        originName = cleanText(originName);
        destinationName = cleanText(destinationName);
        destinationDimension = cleanText(destinationDimension);
        itemId = cleanText(itemId);
        itemName = cleanText(itemName);
        quantity = Math.max(1, Math.min(64, quantity));
        rewardCents = Math.max(0L, rewardCents);
        secondsRemaining = Math.max(0L, secondsRemaining);
        status = cleanText(status);
        routeDistanceBlocks = Math.max(-1, Math.min(30_000_000, routeDistanceBlocks));
        marketValueCents = Math.max(0L, marketValueCents);
        moneyMultiplierPercent = Math.max(50, Math.min(300, moneyMultiplierPercent <= 0 ? 100 : moneyMultiplierPercent));
        xpMultiplierPercent = Math.max(50, Math.min(300, xpMultiplierPercent <= 0 ? 100 : xpMultiplierPercent));
        contractTier = cleanText(contractTier);
    }

    private String clean(String value) {
        return cleanText(value);
    }

    private static String cleanText(String value) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() <= MAX_TEXT_LENGTH ? cleaned : cleaned.substring(0, MAX_TEXT_LENGTH);
    }

    private static String readString(RegistryFriendlyByteBuf buf) {
        String value = ByteBufCodecs.STRING_UTF8.decode(buf);
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() <= MAX_TEXT_LENGTH ? cleaned : cleaned.substring(0, MAX_TEXT_LENGTH);
    }
}
