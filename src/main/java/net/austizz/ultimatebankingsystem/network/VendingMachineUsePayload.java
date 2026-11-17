package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public record VendingMachineUsePayload(
        String dimensionId,
        int x,
        int y,
        int z,
        boolean configureAction
) implements CustomPacketPayload {
    public static final Type<VendingMachineUsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "vending_machine_use"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VendingMachineUsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        ByteBufCodecs.BOOL.encode(buf, payload.configureAction());
                    },
                    buf -> new VendingMachineUsePayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    public static void sendToServer(Level level, BlockPos pos, boolean configureAction) {
        if (level == null || pos == null || !level.isClientSide()) {
            return;
        }
        PacketDistributor.sendToServer(new VendingMachineUsePayload(
                level.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(), configureAction));
    }

    @Override
    public Type<VendingMachineUsePayload> type() {
        return TYPE;
    }
}
