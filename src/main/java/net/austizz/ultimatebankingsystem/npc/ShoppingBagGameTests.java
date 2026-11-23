package net.austizz.ultimatebankingsystem.npc;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBagBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBagDataKeys;
import net.austizz.ultimatebankingsystem.menu.ShoppingBagMenu;
import net.austizz.ultimatebankingsystem.shelf.ShelfCartService.BasketEntryView;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(UltimateBankingSystem.MODID)
@PrefixGameTestTemplate(false)
public final class ShoppingBagGameTests {
    @GameTest(template = "empty3x3x3")
    public static void bagsRejectNestingAndKeepOrdinaryItems(GameTestHelper helper) {
        var block = ModBlocks.SHOPPING_BAG.get();
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block);
        var entity = (ShoppingBagBlockEntity) helper.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var bag = new ItemStack(block);
        player.setItemInHand(InteractionHand.OFF_HAND, bag);
        var heldMenu = ShoppingBagMenu.forItem(1, player.getInventory(), InteractionHand.OFF_HAND);
        var placedMenu = new ShoppingBagMenu(2, player.getInventory(), entity);

        helper.assertTrue(heldMenu.stillValid(player), "Opening a fresh held bag must keep the menu valid");
        helper.assertTrue(entity.getItemHandler().insertItem(0, bag, false).getCount() == 1,
                "Automation must reject shopping bags");
        for (var menu : List.of(heldMenu, placedMenu)) {
            int hotbarSlot = menu.slots.size() - 9;
            player.getInventory().setItem(0, new ItemStack(block));
            menu.clicked(0, 0, ClickType.SWAP, player);
            menu.clicked(hotbarSlot, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(!menu.getSlot(0).hasItem(), "Hotbar swaps and shift-clicking must reject nested bags");
            menu.setCarried(new ItemStack(block));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(!menu.getSlot(0).hasItem() && !menu.getCarried().isEmpty(),
                    "Clicking with a bag on the cursor must leave it there");
            menu.setCarried(ItemStack.EMPTY);

            player.getInventory().setItem(0, new ItemStack(Items.APPLE, 2));
            menu.clicked(hotbarSlot, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.APPLE), "Ordinary items must still transfer into the bag");
        }
        heldMenu.removed(player);
        var reopened = ShoppingBagMenu.forItem(3, player.getInventory(), InteractionHand.OFF_HAND);
        helper.assertTrue(reopened.getSlot(0).getItem().getCount() == 2, "Held bag contents must survive closing and reopening");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void checkoutLeavesPurchasedBagsUnpacked(GameTestHelper helper) {
        var bag = new ItemStack(ModBlocks.SHOPPING_BAG.get());
        var bagEntry = new BasketEntryView(bag, 3, 100, "");
        var onlyBags = ShopCashierInteractionManager.buildShoppingBagsForCheckout(List.of(bagEntry), "Test");
        helper.assertTrue(onlyBags.packed().isEmpty(), "A bag-only purchase must require no packaging stock");

        var appleEntry = new BasketEntryView(new ItemStack(Items.APPLE), 2, 100, "");
        var mixed = ShopCashierInteractionManager.buildShoppingBagsForCheckout(List.of(bagEntry, appleEntry), "Test");
        helper.assertTrue(mixed.loose().size() == 1 && mixed.loose().getFirst().is(bag.getItem())
                && mixed.loose().getFirst().getCount() == 3, "Checkout must preserve the purchased bags as loose items");
        helper.assertTrue(mixed.packed().size() == 1 && ItemStackDataCompat.getCustomData(mixed.packed().getFirst())
                .contains(ShoppingBagDataKeys.BAG_DATA_KEY), "Ordinary goods must still be packed");
        helper.succeed();
    }
}
