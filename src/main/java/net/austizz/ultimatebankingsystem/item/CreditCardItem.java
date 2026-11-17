package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class CreditCardItem extends Item {
    public static final String TAG_BACK_VISIBLE = "ubs_cc_back_visible";

    public CreditCardItem(Properties properties) {
        super(properties);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void allowBlockInteraction(PlayerInteractEvent.RightClickBlock event) {
        // Sneaking normally skips blocks; containers and other usable blocks must get first refusal.
        if (event.getEntity().isShiftKeyDown() && event.getItemStack().getItem() instanceof CreditCardItem
                && event.getUseBlock().isDefault()) {
            event.setUseBlock(TriState.TRUE);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        var tag = ItemStackDataCompat.getCustomData(stack);
        tag.putBoolean(TAG_BACK_VISIBLE, !tag.getBoolean(TAG_BACK_VISIBLE));
        ItemStackDataCompat.setCustomData(stack, tag);
        player.getCooldowns().addCooldown(this, 8);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || entity.tickCount % 20 != 0) return;
        var tag = ItemStackDataCompat.getCustomData(stack);
        if (!tag.getString(CreditCardService.TAG_HOLDER_NAME).isBlank()
                || !tag.hasUUID(CreditCardService.TAG_CARD_ID)) return;
        var bank = BankManager.getCentralBank(serverLevel.getServer());
        if (bank == null) return;
        var record = bank.getIssuedCreditCards().get(tag.getUUID(CreditCardService.TAG_CARD_ID));
        if (record != null && !record.getString("holderName").isBlank()) {
            tag.putString(CreditCardService.TAG_HOLDER_NAME, record.getString("holderName"));
            ItemStackDataCompat.setCustomData(stack, tag);
        }
    }
}
