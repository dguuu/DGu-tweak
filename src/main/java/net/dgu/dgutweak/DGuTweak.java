package net.dgu.dgutweak;

import net.dgu.dgutweak.data.RecordedVillagersData;
import net.dgu.dgutweak.networking.GlowVillagerC2SPayload;
import net.dgu.dgutweak.networking.RecordVillagerC2SPayload;
import net.dgu.dgutweak.networking.RequestTradeListC2SPayload;
import net.dgu.dgutweak.networking.TradeListS2CPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.core.component.DataComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class DGuTweak implements ModInitializer {

    public static final String MOD_ID = "dgutweak";
    public static final String MOD_NAME = "DGu Tweak";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    private static final List<PendingRecord> PENDING_RECORDS = new ArrayList<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing");
        PayloadTypeRegistry.playC2S().register(RecordVillagerC2SPayload.PACKET_ID, RecordVillagerC2SPayload.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestTradeListC2SPayload.PACKET_ID, RequestTradeListC2SPayload.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(GlowVillagerC2SPayload.PACKET_ID, GlowVillagerC2SPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(TradeListS2CPayload.PACKET_ID, TradeListS2CPayload.PACKET_CODEC);

        DGuTweakCommand.register();
        ServerPlayNetworking.registerGlobalReceiver(RecordVillagerC2SPayload.PACKET_ID, (payload, context) ->
                context.server().execute(() -> recordMerchant(context.player(), payload.entityId(), false, 40)));
        ServerPlayNetworking.registerGlobalReceiver(RequestTradeListC2SPayload.PACKET_ID, (payload, context) ->
                context.server().execute(() -> sendTradeList(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GlowVillagerC2SPayload.PACKET_ID, (payload, context) ->
                context.server().execute(() -> locateRecordedVillager(context.player(), payload.uuid(), payload.glow())));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof Villager || entity instanceof WanderingTrader) {
                removeRecordedMerchant(entity);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> tickPendingRecords());
    }

    private static void recordMerchant(ServerPlayer player, int entityId, boolean retry, int attemptsRemaining) {
        ServerLevel level = player.level();
        String recordedBy = player.getGameProfile().name();
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        RecordedVillagersData data = RecordedVillagersData.getOrCreate(level);
        long recordedAt = level.getGameTime();

        if (entity instanceof Villager villager) {
            MerchantOffers lockedOffers = getVisibleTradersLockedOffers(villager);
            if (lockedOffers == null && villager.getVillagerData().level() < 5 && attemptsRemaining > 0) {
                queuePendingRecord(player, entityId, attemptsRemaining - 1);
                if (!retry) {
                    player.sendSystemMessage(Component.literal("[DGu-tweak] Visible Traders locked trades are still generating; recording will finish shortly."));
                }
                return;
            }
            if (lockedOffers == null) {
                lockedOffers = new MerchantOffers();
            }

            String profession = villager.getVillagerData().profession()
                    .unwrapKey()
                    .map((ResourceKey<?> key) -> key.identifier().getPath())
                    .orElse("none");
            int villagerLevel = villager.getVillagerData().level();
            RecordedVillagersData.RecordResult result = data.addOrUpdate(new RecordedVillagersData.VillagerRecord(
                    villager.getUUID(),
                    villager.blockPosition(),
                    level.dimension(),
                    validOffers(villager.getOffers()),
                    validOffers(lockedOffers),
                    profession,
                    villagerLevel,
                    recordedAt,
                    recordedBy
            ));

            player.sendSystemMessage(Component.literal(result == RecordedVillagersData.RecordResult.ADDED
                    ? "[DGu-tweak] Recorded villager."
                    : "[DGu-tweak] Updated villager record."));
            if (lockedOffers.isEmpty() && villagerLevel < 5) {
                player.sendSystemMessage(Component.literal("[DGu-tweak] Warning: locked trades were still empty after waiting."));
            }
            LOGGER.info("Recorded villager {} ({}, level {}) by {}", villager.getUUID(), profession, villagerLevel, recordedBy);
            return;
        }

        if (entity instanceof WanderingTrader trader) {
            RecordedVillagersData.RecordResult result = data.addOrUpdate(new RecordedVillagersData.VillagerRecord(
                    trader.getUUID(),
                    trader.blockPosition(),
                    level.dimension(),
                    validOffers(trader.getOffers()),
                    new MerchantOffers(),
                    "wandering_trader",
                    1,
                    recordedAt,
                    recordedBy
            ));

            player.sendSystemMessage(Component.literal(result == RecordedVillagersData.RecordResult.ADDED
                    ? "[DGu-tweak] Recorded wandering trader."
                    : "[DGu-tweak] Updated wandering trader record."));
            LOGGER.info("Recorded wandering trader {} by {}", trader.getUUID(), recordedBy);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void sendTradeList(ServerPlayer player) {
        List<TradeListS2CPayload.Entry> entries = new ArrayList<>();
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            RecordedVillagersData data = RecordedVillagersData.getOrCreate(level);
            int sanitized = data.sanitize();
            if (sanitized > 0) {
                LOGGER.info("Sanitized {} recorded merchant records in {}", sanitized, level.dimension().identifier());
            }
            for (RecordedVillagersData.VillagerRecord record : data.getRecords().values()) {
                Entity entity = level.getEntity(record.uuid());
                MerchantOffers liveOffers = entity instanceof Villager villager ? villager.getOffers()
                        : entity instanceof WanderingTrader trader ? trader.getOffers()
                        : null;
                addTradeEntries(player, record, record.offers(), liveOffers, false, entries);
                addTradeEntries(player, record, record.lockedOffers(), null, true, entries);
            }
        }
        ServerPlayNetworking.send(player, new TradeListS2CPayload(entries));
    }

    private static void addTradeEntries(ServerPlayer player, RecordedVillagersData.VillagerRecord record, MerchantOffers offers,
                                        MerchantOffers liveOffers, boolean locked, List<TradeListS2CPayload.Entry> entries) {
        int index = 0;
        for (MerchantOffer offer : offers) {
            if (!isValidOffer(offer)) {
                index++;
                continue;
            }
            MerchantOffer liveOffer = liveOffers != null && index < liveOffers.size() && sameResult(offer, liveOffers.get(index))
                    ? liveOffers.get(index)
                    : null;
            ItemStack currentCostA = liveOffer == null ? offer.getBaseCostA() : liveOffer.getCostA();
            ItemStack costB = liveOffer == null ? offer.getCostB() : liveOffer.getCostB();
            double distance = player.level().dimension().equals(record.dimension())
                    ? player.position().distanceTo(record.pos().getCenter())
                    : -1.0D;
            entries.add(new TradeListS2CPayload.Entry(
                    record.uuid(),
                    record.pos(),
                    record.dimension().identifier(),
                    record.profession(),
                    record.level(),
                    stackName(offer.getResult()),
                    translationKey(offer.getResult()),
                    offer.getResult().getCount(),
                    stackName(offer.getBaseCostA()),
                    translationKey(offer.getBaseCostA()),
                    offer.getBaseCostA().getCount(),
                    currentCostA.getCount(),
                    costB.isEmpty() ? "" : stackName(costB),
                    costB.isEmpty() ? "" : translationKey(costB),
                    costB.isEmpty() ? 0 : costB.getCount(),
                    locked,
                    liveOffer != null,
                    distance
            ));
            index++;
        }
    }

    private static void locateRecordedVillager(ServerPlayer player, UUID uuid, boolean glow) {
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof Villager villager) {
                updateRecordedPosition(villager);
                if (glow) {
                    villager.setGlowingTag(false);
                    villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 20, 0, false, true));
                    player.sendSystemMessage(Component.literal("[DGu-tweak] Highlighted villager for 20 seconds."));
                } else {
                    player.sendSystemMessage(Component.literal("[DGu-tweak] Updated villager position."));
                }
                sendTradeList(player);
                return;
            }
            if (entity instanceof WanderingTrader trader) {
                updateRecordedPosition(trader);
                if (glow) {
                    trader.setGlowingTag(false);
                    trader.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 20, 0, false, true));
                    player.sendSystemMessage(Component.literal("[DGu-tweak] Highlighted wandering trader for 20 seconds."));
                } else {
                    player.sendSystemMessage(Component.literal("[DGu-tweak] Updated wandering trader position."));
                }
                sendTradeList(player);
                return;
            }
        }
        player.sendSystemMessage(Component.literal("[DGu-tweak] Merchant is not loaded. Use Track/coordinates to navigate closer."));
    }

    private static void updateRecordedPosition(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            RecordedVillagersData.getOrCreate(level).updatePosition(entity.getUUID(), entity.blockPosition(), level.dimension());
        }
    }

    private static void removeRecordedMerchant(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        RecordedVillagersData data = RecordedVillagersData.getOrCreate(level);
        if (data.remove(entity.getUUID())) {
            LOGGER.info("Removed dead recorded merchant {}", entity.getUUID());
        }
    }

    private static boolean sameResult(MerchantOffer first, MerchantOffer second) {
        if (!isValidOffer(first) || !isValidOffer(second)) {
            return false;
        }
        return ItemStack.isSameItem(first.getResult(), second.getResult())
                && first.getResult().getCount() == second.getResult().getCount();
    }

    private static MerchantOffers validOffers(MerchantOffers offers) {
        MerchantOffers filtered = new MerchantOffers();
        for (MerchantOffer offer : offers) {
            if (isValidOffer(offer)) {
                filtered.add(offer);
            }
        }
        return filtered;
    }

    private static boolean isValidOffer(MerchantOffer offer) {
        return offer != null
                && !offer.getBaseCostA().isEmpty()
                && !offer.getResult().isEmpty();
    }

    private static String stackName(ItemStack stack) {
        String name = stack.getHoverName().getString();
        String enchantments = enchantmentText(stack);
        return enchantments.isEmpty() ? name : name + ": " + enchantments;
    }

    private static String translationKey(ItemStack stack) {
        return stack.isEmpty() ? "" : stack.getItem().getDescriptionId();
    }

    private static String enchantmentText(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) {
            enchantments = stack.getEnchantments();
        }
        if (enchantments == null || enchantments.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : enchantments.entrySet()) {
            lines.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
        }
        return String.join(", ", lines);
    }

    private static MerchantOffers getVisibleTradersLockedOffers(Villager villager) {
        try {
            Class<?> duckClass = Class.forName("net.ramixin.visibletraders.ducks.VillagerDuck");
            Object duck = duckClass.getMethod("of", Villager.class).invoke(null, villager);

            try {
                Object result = duckClass.getMethod("visibleTraders$getCondensedOffers").invoke(duck);
                if (result instanceof java.util.Optional<?> optional) {
                    Object offers = optional.orElse(null);
                    if (offers == null) {
                        duckClass.getMethod("visibleTrades$regenerateTrades").invoke(duck);
                        return null;
                    }
                    if (offers instanceof MerchantOffers merchantOffers) {
                        return merchantOffers.isEmpty() ? null : merchantOffers;
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // Visible Traders 0.1.3.1 exposes combined offers instead.
            }

            try {
                Object result = duckClass.getMethod("visibleTraders$getCombinedOffers").invoke(duck);
                if (result instanceof MerchantOffers combinedOffers) {
                    MerchantOffers currentOffers = villager.getOffers();
                    if (combinedOffers.size() <= currentOffers.size()) {
                        return null;
                    }

                    MerchantOffers lockedOffers = new MerchantOffers();
                    for (int index = currentOffers.size(); index < combinedOffers.size(); index++) {
                        lockedOffers.add(combinedOffers.get(index));
                    }
                    return lockedOffers;
                }
            } catch (NoSuchMethodException ignored) {
                // Fall through to direct locked data access for nearby Visible Traders builds.
            }

            Object lockedDataResult = duckClass.getMethod("visibleTraders$getLockedTradeData").invoke(duck);

            if (lockedDataResult instanceof java.util.Optional<?> optional) {
                Object lockedData = optional.orElse(null);
                if (lockedData == null) {
                    duckClass.getMethod("visibleTrades$regenerateTrades").invoke(duck);
                    return null;
                }

                Object result = lockedData.getClass().getMethod("buildLockedOffers").invoke(lockedData);
                if (result instanceof MerchantOffers offers) {
                    return offers;
                }
            }
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Visible Traders locked-offer bridge was not available for {}", villager.getUUID(), exception);
        }

        return new MerchantOffers();
    }

    private static void queuePendingRecord(ServerPlayer player, int entityId, int attemptsRemaining) {
        UUID playerUuid = player.getUUID();
        PENDING_RECORDS.removeIf(record -> record.playerUuid.equals(playerUuid) && record.entityId == entityId);
        PENDING_RECORDS.add(new PendingRecord(player.level(), playerUuid, entityId, attemptsRemaining, 5));
    }

    private static void tickPendingRecords() {
        Iterator<PendingRecord> iterator = PENDING_RECORDS.iterator();
        List<PendingRecord> readyRecords = new ArrayList<>();
        while (iterator.hasNext()) {
            PendingRecord record = iterator.next();
            if (record.delayTicks > 0) {
                record.delayTicks--;
                continue;
            }

            iterator.remove();
            readyRecords.add(record);
        }

        for (PendingRecord record : readyRecords) {
            ServerPlayer player = record.level.getServer().getPlayerList().getPlayer(record.playerUuid);
            if (player == null) continue;
            recordMerchant(player, record.entityId, true, record.attemptsRemaining);
        }
    }

    private static final class PendingRecord {
        private final ServerLevel level;
        private final UUID playerUuid;
        private final int entityId;
        private final int attemptsRemaining;
        private int delayTicks;

        private PendingRecord(ServerLevel level, UUID playerUuid, int entityId, int attemptsRemaining, int delayTicks) {
            this.level = level;
            this.playerUuid = playerUuid;
            this.entityId = entityId;
            this.attemptsRemaining = attemptsRemaining;
            this.delayTicks = delayTicks;
        }
    }
}
