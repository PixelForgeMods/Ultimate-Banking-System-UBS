package net.austizz.ultimatebankingsystem.pc;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Action;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Request;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Result;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(UltimateBankingSystem.MODID)
@PrefixGameTestTemplate(false)
public final class PcLoginGameTests {
    private PcLoginGameTests() {}

    @GameTest(template = "empty3x3x3")
    public static void passwordPrecedesPinAndBothSurviveSave(GameTestHelper helper) {
        helper.assertTrue(!PcLoginService.canAttempt(Action.CREATE_PIN, null), "First use must require a password");
        var setup = PcLoginService.authenticate(request(Action.CREATE_PASSWORD, "test password", ""), null);
        helper.assertTrue(setup.result() == Result.SIGNED_IN, "Password creation must sign in");
        helper.assertTrue(!PcLoginService.canAttempt(Action.CREATE_PASSWORD, setup.profile()), "Password must be protected from replacement");
        helper.assertTrue(!PcLoginService.canAttempt(Action.PIN, setup.profile()), "PIN must be enrolled before use");
        var denied = PcLoginService.authenticate(request(Action.CREATE_PIN, "wrong password", "0042"), setup.profile());
        helper.assertTrue(denied.result() == Result.INCORRECT, "PIN enrollment must verify the password");

        var enrolled = PcLoginService.authenticate(request(Action.CREATE_PIN, "test password", "0042"), setup.profile());
        helper.assertTrue(enrolled.result() == Result.SIGNED_IN, "Correct password must allow PIN enrollment");
        UUID player = UUID.randomUUID();
        var store = new PcLoginSavedData();
        store.saveProfile(player, enrolled.profile());
        var registries = helper.getLevel().registryAccess();
        var restored = PcLoginSavedData.load(store.save(new CompoundTag(), registries), registries);
        helper.assertTrue(restored.profile(UUID.randomUUID()) == null, "Credentials belong to the enrolled player");
        var profile = restored.profile(player);
        helper.assertTrue(PcLoginService.authenticate(request(Action.PIN, "0042", ""), profile).result() == Result.SIGNED_IN,
                "The saved PIN must preserve leading zeros");
        helper.assertTrue(PcLoginService.authenticate(request(Action.PASSWORD, "test password", ""), profile).result() == Result.SIGNED_IN,
                "The saved password must still work after setting up a PIN");
        helper.assertTrue(PcLoginService.authenticate(request(Action.PIN, "0043", ""), profile).result() == Result.INCORRECT,
                "An incorrect PIN must leave the PC locked");
        helper.succeed();
    }

    private static Request request(Action action, String secret, String pin) {
        return new Request(1, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), action, secret, pin);
    }
}
