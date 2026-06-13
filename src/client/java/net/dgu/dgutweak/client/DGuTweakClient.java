package net.dgu.dgutweak.client;

import net.dgu.dgutweak.DGuTweak;
import net.dgu.dgutweak.networking.RequestTradeListC2SPayload;
import net.dgu.dgutweak.networking.TradeListS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DGuTweakClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DGuTweak.LOGGER.info("Initializing client");
        ClientPlayNetworking.registerGlobalReceiver(TradeListS2CPayload.PACKET_ID, (payload, context) ->
                context.client().execute(() -> Minecraft.getInstance().setScreen(new TradeDatabaseScreen(payload.entries()))));
    }

    public static void requestTradeList() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new TradeDatabaseScreen(List.of()));
        if (client.player == null) {
            return;
        }
        ClientPlayNetworking.send(new RequestTradeListC2SPayload());
    }
}
