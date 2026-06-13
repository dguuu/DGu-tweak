package net.dgu.dgutweak.client;

import net.dgu.dgutweak.DGuTweak;
import net.dgu.dgutweak.networking.RequestTradeListC2SPayload;
import net.dgu.dgutweak.networking.TradeListS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.List;

public class DGuTweakClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DGuTweak.LOGGER.info("Initializing client");
        DGuTweakClientConfig.load();
        ClientPlayNetworking.registerGlobalReceiver(TradeListS2CPayload.PACKET_ID, (payload, context) ->
                context.client().execute(() -> {
                    Minecraft client = Minecraft.getInstance();
                    if (client.screen instanceof TradeDatabaseScreen screen) {
                        screen.replaceEntries(payload.entries());
                    } else {
                        client.setScreen(new TradeDatabaseScreen(payload.entries()));
                    }
                }));
    }

    public static void requestTradeList() {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof TradeDatabaseScreen)) {
            client.setScreen(new TradeDatabaseScreen(List.of()));
        }
        if (client.player == null) {
            return;
        }
        ClientPlayNetworking.send(new RequestTradeListC2SPayload());
    }
}
