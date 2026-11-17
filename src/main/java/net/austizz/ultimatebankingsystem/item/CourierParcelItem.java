package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Non-stackable parcel issued by a server-authoritative Courier order. */
public final class CourierParcelItem extends Item {
    public static final String ORDER_ID_KEY = "ubs_courier_order_id";
    public static final String DESTINATION_KEY = "ubs_courier_destination";
    public static final String CONTENT_NAME_KEY = "ubs_courier_content_name";
    public static final String CONTENT_COUNT_KEY = "ubs_courier_content_count";

    public CourierParcelItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static String orderId(ItemStack stack) {
        return ItemStackDataCompat.getCustomString(ORDER_ID_KEY, stack).trim();
    }

    public static boolean isParcel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof CourierParcelItem;
    }

    public static void writeAssignment(ItemStack stack, String orderId, String destination,
                                       String contentName, int contentCount) {
        if (!isParcel(stack)) return;
        ItemStackDataCompat.putCustomString(ORDER_ID_KEY, stack, orderId);
        ItemStackDataCompat.putCustomString(DESTINATION_KEY, stack, destination);
        ItemStackDataCompat.putCustomString(CONTENT_NAME_KEY, stack, contentName);
        ItemStackDataCompat.putCustomString(CONTENT_COUNT_KEY, stack, String.valueOf(Math.max(1, contentCount)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String destination = ItemStackDataCompat.getCustomString(DESTINATION_KEY, stack);
        String content = ItemStackDataCompat.getCustomString(CONTENT_NAME_KEY, stack);
        String count = ItemStackDataCompat.getCustomString(CONTENT_COUNT_KEY, stack);
        if (!destination.isBlank()) {
            tooltip.add(Component.literal("Deliver to: " + destination).withStyle(ChatFormatting.AQUA));
        }
        if (!content.isBlank()) {
            tooltip.add(Component.literal("Manifest: " + count + " x " + content)
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Hand to the named NPC to complete the route.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
