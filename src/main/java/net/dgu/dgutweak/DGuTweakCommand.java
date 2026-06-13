package net.dgu.dgutweak;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.dgu.dgutweak.data.RecordedVillagersData;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DGuTweakCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("vt")
                        .then(Commands.literal("list")
                                .executes(context -> listVillagers(context.getSource(), null, "distance"))
                                .then(Commands.argument("profession", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            if (context.getSource().getEntity() instanceof ServerPlayer player) {
                                                RecordedVillagersData data = RecordedVillagersData.getOrCreate(player.level());
                                                return SharedSuggestionProvider.suggest(data.getRecords().values().stream()
                                                        .map(RecordedVillagersData.VillagerRecord::profession)
                                                        .filter(profession -> !profession.equals("none"))
                                                        .distinct(), builder);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> listVillagers(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "profession"),
                                                "distance"
                                        ))
                                        .then(Commands.argument("sort", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("distance", "level", "recent"), builder))
                                                .executes(context -> listVillagers(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "profession"),
                                                        StringArgumentType.getString(context, "sort")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("detail")
                                .then(Commands.argument("uuid", StringArgumentType.word())
                                        .executes(context -> showDetail(context.getSource(), StringArgumentType.getString(context, "uuid")))
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("dgu")
                        .then(Commands.literal("trades")
                                .executes(context -> openTradeUi(context.getSource()))
                        )
                        .then(Commands.literal("verify")
                                .executes(context -> verifyRecordedMerchants(context.getSource()))
                        )
        );
    }

    private static int openTradeUi(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        DGuTweak.sendTradeList(player);
        return 1;
    }

    private static int verifyRecordedMerchants(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        int kept = 0;
        int removed = 0;
        int skipped = 0;
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            RecordedVillagersData data = RecordedVillagersData.getOrCreate(level);
            List<UUID> stale = new ArrayList<>();
            for (RecordedVillagersData.VillagerRecord record : data.getRecords().values()) {
                if (!level.isLoaded(record.pos())) {
                    skipped++;
                    continue;
                }
                Entity entity = level.getEntity(record.uuid());
                if (entity instanceof Villager || entity instanceof WanderingTrader) {
                    kept++;
                } else {
                    stale.add(record.uuid());
                }
            }
            for (UUID uuid : stale) {
                if (data.remove(uuid)) {
                    removed++;
                }
            }
        }

        int finalKept = kept;
        int finalRemoved = removed;
        int finalSkipped = skipped;
        source.sendSuccess(() -> Component.literal("[DGu-tweak] Verify complete. Alive/loaded: " + finalKept + ", removed missing in loaded chunks: " + finalRemoved + ", skipped unloaded: " + finalSkipped + "."), false);
        return removed;
    }

    private static int listVillagers(CommandSourceStack source, String professionFilter, String sort) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        Map<UUID, RecordedVillagersData.VillagerRecord> records = RecordedVillagersData.getOrCreate(player.level()).getRecords();
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No recorded villagers yet."), false);
            return 0;
        }

        List<RecordedVillagersData.VillagerRecord> filtered = records.values().stream()
                .filter(record -> professionFilter == null || record.profession().equalsIgnoreCase(professionFilter))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (filtered.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No recorded villagers for profession: " + professionFilter), false);
            return 0;
        }

        Vec3 playerPos = source.getPosition();
        switch (sort.toLowerCase()) {
            case "level" -> filtered.sort(Comparator.comparingInt(RecordedVillagersData.VillagerRecord::level).reversed());
            case "recent" -> filtered.sort(Comparator.comparingLong(RecordedVillagersData.VillagerRecord::recordedAt).reversed());
            default -> filtered.sort(Comparator.comparingDouble(record -> playerPos.distanceTo(posCenter(record))));
        }

        source.sendSuccess(() -> Component.literal("=== Recorded villagers (" + filtered.size() + "), sorted by " + sort + " ==="), false);
        for (RecordedVillagersData.VillagerRecord record : filtered) {
            double distance = playerPos.distanceTo(posCenter(record));
            MutableComponent coords = Component.literal(formatPos(record))
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent.RunCommand("/tp @s " + record.pos().getX() + " " + record.pos().getY() + " " + record.pos().getZ()))
                            .withUnderlined(true));
            MutableComponent details = Component.literal(" [detail]")
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent.RunCommand("/vt detail " + record.uuid()))
                            .withUnderlined(true));
            source.sendSuccess(() -> Component.literal(record.dimension().identifier().getPath() + " " + record.profession() + " L" + record.level() + " ")
                    .append(coords)
                    .append(Component.literal(String.format(" %.0fm", distance)))
                    .append(details), false);
        }

        return filtered.size();
    }

    private static int showDetail(CommandSourceStack source, String uuidText) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Invalid UUID."));
            return 0;
        }

        RecordedVillagersData.VillagerRecord record = RecordedVillagersData.getOrCreate(player.level()).getRecords().get(uuid);
        if (record == null) {
            source.sendFailure(Component.literal("No recorded villager found for that UUID."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== " + record.profession() + " L" + record.level() + " @ " + formatPos(record) + " ==="), false);
        source.sendSuccess(() -> Component.literal("Recorded by " + record.recordedBy() + " in " + record.dimension().identifier().getPath()), false);
        sendOffers(source, "Trades", record.offers(), findLiveOffers(source, record));
        sendOffers(source, "Locked trades", record.lockedOffers(), null);
        return 1;
    }

    private static MerchantOffers findLiveOffers(CommandSourceStack source, RecordedVillagersData.VillagerRecord record) {
        ServerLevel level = source.getServer().getLevel(record.dimension());
        if (level == null) {
            return null;
        }

        Entity entity = level.getEntity(record.uuid());
        if (entity instanceof Villager villager) {
            return villager.getOffers();
        }
        return null;
    }

    private static void sendOffers(CommandSourceStack source, String title, Iterable<MerchantOffer> offers, MerchantOffers liveOffers) {
        source.sendSuccess(() -> Component.literal(title + ":"), false);
        int index = 0;
        for (MerchantOffer offer : offers) {
            MerchantOffer liveOffer = liveOffers != null && index < liveOffers.size() ? liveOffers.get(index) : null;
            String line = formatRecordedOffer(offer) + formatLivePriceSuffix(offer, liveOffer);
            source.sendSuccess(() -> Component.literal("  " + line), false);
            index++;
        }
    }

    private static String formatRecordedOffer(MerchantOffer offer) {
        return formatCost(offer.getBaseCostA(), offer.getCostB()) + " -> " + formatStack(offer.getResult());
    }

    private static String formatLivePriceSuffix(MerchantOffer recordedOffer, MerchantOffer liveOffer) {
        if (liveOffer == null || !sameResult(recordedOffer, liveOffer) || !hasDifferentCurrentPrice(recordedOffer, liveOffer)) {
            return "";
        }
        return " (目前 " + formatCost(liveOffer.getCostA(), liveOffer.getCostB()) + ")";
    }

    private static boolean hasDifferentCurrentPrice(MerchantOffer recordedOffer, MerchantOffer liveOffer) {
        return !sameStackCountAndName(recordedOffer.getBaseCostA(), liveOffer.getCostA())
                || !sameStackCountAndName(recordedOffer.getCostB(), liveOffer.getCostB());
    }

    private static boolean sameResult(MerchantOffer first, MerchantOffer second) {
        return sameStackCountAndName(first.getResult(), second.getResult());
    }

    private static boolean sameStackCountAndName(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getCount() == second.getCount()
                && first.getHoverName().getString().equals(second.getHoverName().getString());
    }

    private static String formatCost(ItemStack costA, ItemStack costB) {
        StringBuilder line = new StringBuilder(formatStack(costA));
        if (!costB.isEmpty()) {
            line.append(" + ").append(formatStack(costB));
        }
        return line.toString();
    }

    private static String formatStack(ItemStack stack) {
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }

    private static Vec3 posCenter(RecordedVillagersData.VillagerRecord record) {
        return new Vec3(record.pos().getX() + 0.5, record.pos().getY() + 0.5, record.pos().getZ() + 0.5);
    }

    private static String formatPos(RecordedVillagersData.VillagerRecord record) {
        return record.pos().getX() + ", " + record.pos().getY() + ", " + record.pos().getZ();
    }
}
