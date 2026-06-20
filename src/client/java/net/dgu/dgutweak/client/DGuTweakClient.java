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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DGuTweakClient implements ClientModInitializer {
    private static final Map<UUID, List<LiveTradePrice>> LIVE_TRADE_PRICES = new HashMap<>();

    @Override
    public void onInitializeClient() {
        DGuTweak.LOGGER.info("Initializing client");
        DGuTweakClientConfig.load();
        AutoVillagerFilter.initialize();
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

    public static void rememberMerchantOffers(UUID uuid, MerchantOffers offers) {
        List<LiveTradePrice> prices = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            if (offer == null || offer.getBaseCostA().isEmpty() || offer.getResult().isEmpty()) {
                continue;
            }
            prices.add(new LiveTradePrice(
                    itemKey(offer.getResult()),
                    offer.getResult().getCount(),
                    itemKey(offer.getBaseCostA()),
                    offer.getBaseCostA().getCount(),
                    offer.getCostA().getCount(),
                    offer.getCostB().isEmpty() ? "" : itemKey(offer.getCostB()),
                    offer.getCostB().isEmpty() ? 0 : offer.getCostB().getCount()
            ));
        }
        LIVE_TRADE_PRICES.put(uuid, prices);
    }

    public static List<TradeListS2CPayload.Entry> applyRememberedMerchantPrices(List<TradeListS2CPayload.Entry> entries) {
        if (LIVE_TRADE_PRICES.isEmpty()) {
            return entries;
        }

        List<TradeListS2CPayload.Entry> updated = new ArrayList<>(entries.size());
        for (TradeListS2CPayload.Entry entry : entries) {
            LiveTradePrice price = findRememberedPrice(entry);
            updated.add(price == null ? entry : withRememberedPrice(entry, price));
        }
        return updated;
    }

    private static LiveTradePrice findRememberedPrice(TradeListS2CPayload.Entry entry) {
        if (entry.locked()) {
            return null;
        }
        List<LiveTradePrice> prices = LIVE_TRADE_PRICES.get(entry.uuid());
        if (prices == null) {
            return null;
        }
        for (LiveTradePrice price : prices) {
            if (price.matches(entry)) {
                return price;
            }
        }
        return null;
    }

    private static TradeListS2CPayload.Entry withRememberedPrice(TradeListS2CPayload.Entry entry, LiveTradePrice price) {
        return new TradeListS2CPayload.Entry(
                entry.uuid(),
                entry.pos(),
                entry.dimension(),
                entry.profession(),
                entry.level(),
                entry.resultName(),
                entry.resultTranslationKey(),
                entry.resultCount(),
                entry.costAName(),
                entry.costATranslationKey(),
                entry.baseCostA(),
                price.currentCostA(),
                entry.costBName(),
                entry.costBTranslationKey(),
                price.costB(),
                entry.locked(),
                true,
                entry.distance()
        );
    }

    private static String itemKey(ItemStack stack) {
        return stack.getItem().getDescriptionId();
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

    private record LiveTradePrice(
            String resultKey,
            int resultCount,
            String costAKey,
            int baseCostA,
            int currentCostA,
            String costBKey,
            int costB
    ) {
        private boolean matches(TradeListS2CPayload.Entry entry) {
            String entryCostBKey = entry.costB() <= 0 ? "" : entry.costBTranslationKey();
            return this.resultKey.equals(entry.resultTranslationKey())
                    && this.resultCount == entry.resultCount()
                    && this.costAKey.equals(entry.costATranslationKey())
                    && this.baseCostA == entry.baseCostA()
                    && this.costBKey.equals(entryCostBKey);
        }
    }
}
