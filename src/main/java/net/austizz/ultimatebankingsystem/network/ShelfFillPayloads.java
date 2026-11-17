package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.shelf.ShelfFillService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ShelfFillPayloads {
    private ShelfFillPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ShelfFillOpenPayload.TYPE, ShelfFillOpenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientPayloadInvoker.invoke("handleShelfFillOpen", payload)));
        registrar.playToServer(ShelfFillActionPayload.TYPE, ShelfFillActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) ShelfFillService.handle(player, payload);
                }));
    }
}
