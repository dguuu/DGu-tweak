package net.dgu.dgutweak;

import net.dgu.dgutweak.data.RecordedVillagersData;
import net.dgu.dgutweak.networking.RecordVillagerC2SPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
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

        DGuTweakCommand.register();
        ServerPlayNetworking.registerGlobalReceiver(RecordVillagerC2SPayload.PACKET_ID, (payload, context) ->
                context.server().execute(() -> recordMerchant(context.player(), payload.entityId(), false, 40)));
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
                    villager.getOffers(),
                    lockedOffers,
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
                    trader.getOffers(),
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
