package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record ShelfFillActionPayload(String action, int x, int y, int z, boolean bulk,
                                     boolean force, String modId, long defaultPriceCents,
                                     List<String> itemIds, List<PriceOverride> prices,
                                     List<InventoryChoice> inventoryChoices) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 2048;
    public static final Type<ShelfFillActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "shelf_fill_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfFillActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUtf(value.action(), 24);
                buf.writeVarInt(value.x());
                buf.writeVarInt(value.y());
                buf.writeVarInt(value.z());
                buf.writeBoolean(value.bulk());
                buf.writeBoolean(value.force());
                buf.writeUtf(value.modId(), 128);
                buf.writeVarLong(value.defaultPriceCents());
                writeItems(buf, value.itemIds());
                buf.writeVarInt(value.prices().size());
                for (PriceOverride price : value.prices()) {
                    buf.writeUtf(price.itemId(), 128);
                    buf.writeVarLong(price.cents());
                }
                buf.writeVarInt(value.inventoryChoices().size());
                for (InventoryChoice choice : value.inventoryChoices()) {
                    buf.writeVarInt(choice.slot());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, choice.stack());
                }
            },
            buf -> {
                String action = buf.readUtf(24);
                int x = buf.readVarInt();
                int y = buf.readVarInt();
                int z = buf.readVarInt();
                boolean bulk = buf.readBoolean();
                boolean force = buf.readBoolean();
                String modId = buf.readUtf(128);
                long defaultPrice = buf.readVarLong();
                List<String> itemIds = readItems(buf);
                int count = boundedCount(buf.readVarInt());
                List<PriceOverride> prices = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    prices.add(new PriceOverride(buf.readUtf(128), buf.readVarLong()));
                }
                int inventoryCount = buf.readVarInt();
                if (inventoryCount < 0 || inventoryCount > 41) {
                    throw new IllegalArgumentException("Invalid inventory selection size");
                }
                List<InventoryChoice> inventoryChoices = new ArrayList<>(inventoryCount);
                for (int i = 0; i < inventoryCount; i++) {
                    inventoryChoices.add(new InventoryChoice(buf.readVarInt(),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
                }
                return new ShelfFillActionPayload(action, x, y, z, bulk, force, modId,
                        defaultPrice, itemIds, prices, inventoryChoices);
            });

    public ShelfFillActionPayload {
        modId = modId == null ? "" : modId;
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
        prices = prices == null ? List.of() : List.copyOf(prices);
        inventoryChoices = inventoryChoices == null ? List.of() : List.copyOf(inventoryChoices);
        if (itemIds.size() > MAX_ENTRIES || prices.size() > MAX_ENTRIES || inventoryChoices.size() > 41) {
            throw new IllegalArgumentException("Too many shelf fill items or prices");
        }
    }

    private static void writeItems(RegistryFriendlyByteBuf buf, List<String> items) {
        buf.writeVarInt(items.size());
        for (String item : items) buf.writeUtf(item, 128);
    }

    private static List<String> readItems(RegistryFriendlyByteBuf buf) {
        int count = boundedCount(buf.readVarInt());
        List<String> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) items.add(buf.readUtf(128));
        return items;
    }

    private static int boundedCount(int count) {
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid shelf fill list size");
        return count;
    }

    @Override public Type<ShelfFillActionPayload> type() { return TYPE; }

    public static ShelfFillActionPayload of(String action) {
        return new ShelfFillActionPayload(action, 0, 0, 0, false, false, "", 0L, List.of(), List.of(), List.of());
    }

    public static ShelfFillActionPayload toggle(int x, int y, int z) {
        return new ShelfFillActionPayload("TOGGLE", x, y, z, false, false, "", 0L, List.of(), List.of(), List.of());
    }

    public static String inventoryKey(int slot) { return "@inventory:" + slot; }

    public record PriceOverride(String itemId, long cents) {}
    public record InventoryChoice(int slot, ItemStack stack) {}
}
