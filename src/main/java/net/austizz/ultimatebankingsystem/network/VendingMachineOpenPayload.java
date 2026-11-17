package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public record VendingMachineOpenPayload(
        String dimensionId,
        int x,
        int y,
        int z,
        String ownerName,
        boolean configureAllowed,
        boolean creative,
        String settlementAccountId,
        long totalSalesDollars,
        int cashOccupiedSlots,
        int cashValueCents,
        List<ItemStack> displayItems,
        List<Long> pricesDollars,
        List<Integer> stockCounts,
        List<ShopTerminalAccountSummary> accounts
) implements CustomPacketPayload {
    public static final Type<VendingMachineOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "vending_machine_open"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);
    private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> ITEM_STACK_CODEC = ItemStack.OPTIONAL_STREAM_CODEC;

    public static final StreamCodec<RegistryFriendlyByteBuf, VendingMachineOpenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.ownerName());
                        ByteBufCodecs.BOOL.encode(buf, payload.configureAllowed());
                        ByteBufCodecs.BOOL.encode(buf, payload.creative());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.settlementAccountId());
                        LONG_CODEC.encode(buf, payload.totalSalesDollars());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.cashOccupiedSlots());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.cashValueCents());
                        ITEM_STACK_CODEC.apply(ByteBufCodecs.list(8)).encode(buf, payload.displayItems());
                        LONG_CODEC.apply(ByteBufCodecs.list(8)).encode(buf, payload.pricesDollars());
                        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)).encode(buf, payload.stockCounts());
                        ShopTerminalAccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(64)).encode(buf, payload.accounts());
                    },
                    buf -> new VendingMachineOpenPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ITEM_STACK_CODEC.apply(ByteBufCodecs.list(8)).decode(buf),
                            LONG_CODEC.apply(ByteBufCodecs.list(8)).decode(buf),
                            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)).decode(buf),
                            ShopTerminalAccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(buf)
                    )
            );

    public ItemStack displayItem(int slot) {
        return slot >= 0 && slot < displayItems.size() ? displayItems.get(slot) : ItemStack.EMPTY;
    }

    public long priceDollars(int slot) {
        return slot >= 0 && slot < pricesDollars.size() ? pricesDollars.get(slot) : 1L;
    }

    public int stockCount(int slot) {
        return slot >= 0 && slot < stockCounts.size() ? stockCounts.get(slot) : 0;
    }

    public UUID parseSettlementAccountId() {
        if (settlementAccountId == null || settlementAccountId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(settlementAccountId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public Type<VendingMachineOpenPayload> type() {
        return TYPE;
    }
}
