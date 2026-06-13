package net.dgu.dgutweak.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.dgu.dgutweak.DGuTweak;
import net.dgu.dgutweak.networking.RequestTradeListC2SPayload;
import net.dgu.dgutweak.networking.TradeListS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DGuTweakClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DGuTweak.LOGGER.info("Initializing client");
        DGuTweakClientConfig.load();
        registerClientCommands();
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

    private static void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("dgulang")
                        .executes(context -> showLanguage(context.getSource()))
                        .then(ClientCommandManager.argument("language", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("zh_tw", "en_us"), builder))
                                .executes(context -> setLanguage(context.getSource(), StringArgumentType.getString(context, "language"))))
        ));
    }

    private static int showLanguage(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("[DGu-tweak] language=" + DGuTweakClientConfig.language() + " (zh_tw, en_us)"));
        return 1;
    }

    private static int setLanguage(FabricClientCommandSource source, String language) {
        if (!DGuTweakClientConfig.setLanguage(language)) {
            source.sendError(Component.literal("[DGu-tweak] Unsupported language. Use zh_tw or en_us."));
            return 0;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof TradeDatabaseScreen screen) {
            client.setScreen(new TradeDatabaseScreen(screen.entriesSnapshot()));
        }
        source.sendFeedback(Component.literal("[DGu-tweak] language=" + DGuTweakClientConfig.language()));
        return 1;
    }
}
