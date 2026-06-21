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
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
            source.sendFailure(Component.translatable("command.dgutweak.player_only"));
            return 0;
        }
        DGuTweak.sendTradeList(player);
        return 1;
    }

    private static int verifyRecordedMerchants(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.dgutweak.player_only"));
            return 0;
        }

        int kept = 0;
        int removed = 0;
        int skipped = 0;
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            RecordedVillagersData data = RecordedVillagersData.getOrCreate(level);
            data.sanitize();
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
        source.sendSuccess(() -> Component.translatable("command.dgutweak.verify.complete",
                finalKept, finalRemoved, finalSkipped), false);
        return removed;
    }

    private static int listVillagers(CommandSourceStack source, String professionFilter, String sort) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.dgutweak.player_only"));
            return 0;
        }

        Map<UUID, RecordedVillagersData.VillagerRecord> records = RecordedVillagersData.getOrCreate(player.level()).getRecords();
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dgutweak.list.empty"), false);
            return 0;
        }

        List<RecordedVillagersData.VillagerRecord> filtered = records.values().stream()
                .filter(record -> professionFilter == null || record.profession().equalsIgnoreCase(professionFilter))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (filtered.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dgutweak.list.empty_profession", professionFilter), false);
            return 0;
        }

        Vec3 playerPos = source.getPosition();
        switch (sort.toLowerCase()) {
            case "level" -> filtered.sort(Comparator.comparingInt(RecordedVillagersData.VillagerRecord::level).reversed());
            case "recent" -> filtered.sort(Comparator.comparingLong(RecordedVillagersData.VillagerRecord::recordedAt).reversed());
            default -> filtered.sort(Comparator.comparingDouble(record -> playerPos.distanceTo(posCenter(record))));
        }

        source.sendSuccess(() -> Component.translatable("command.dgutweak.list.header", filtered.size(), sort), false);
        for (RecordedVillagersData.VillagerRecord record : filtered) {
            double distance = playerPos.distanceTo(posCenter(record));
            MutableComponent coords = Component.literal(formatPos(record))
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent.RunCommand("/tp @s " + record.pos().getX() + " " + record.pos().getY() + " " + record.pos().getZ()))
                            .withUnderlined(true));
            MutableComponent details = Component.translatable("command.dgutweak.list.detail")
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent.RunCommand("/vt detail " + record.uuid()))
                            .withUnderlined(true));
            source.sendSuccess(() -> Component.literal(record.dimension().identifier().getPath() + " ")
                    .append(professionComponent(record.profession()))
                    .append(Component.literal(" L" + record.level() + " "))
                    .append(coords)
                    .append(Component.literal(String.format(" %.0fm", distance)))
                    .append(details), false);
        }

        return filtered.size();
    }

    private static int showDetail(CommandSourceStack source, String uuidText) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.dgutweak.player_only"));
            return 0;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("command.dgutweak.detail.invalid_uuid"));
            return 0;
        }

        RecordedVillagersData.VillagerRecord record = RecordedVillagersData.getOrCreate(player.level()).getRecords().get(uuid);
        if (record == null) {
            source.sendFailure(Component.translatable("command.dgutweak.detail.not_found"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.dgutweak.detail.header",
                professionComponent(record.profession()), record.level(), formatPos(record)), false);
        source.sendSuccess(() -> Component.translatable("command.dgutweak.detail.recorded_by",
                record.recordedBy(), record.dimension().identifier().getPath()), false);
        sendOffers(source, "command.dgutweak.detail.trades", record.offers(), findLiveOffers(source, record));
        sendOffers(source, "command.dgutweak.detail.locked_trades", record.lockedOffers(), null);
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

    private static void sendOffers(CommandSourceStack source, String titleKey, Iterable<MerchantOffer> offers, MerchantOffers liveOffers) {
        source.sendSuccess(() -> Component.translatable(titleKey), false);
        int index = 0;
        for (MerchantOffer offer : offers) {
            MerchantOffer liveOffer = liveOffers != null && index < liveOffers.size() ? liveOffers.get(index) : null;
            Component line = formatRecordedOffer(offer).append(formatLivePriceSuffix(offer, liveOffer));
            source.sendSuccess(() -> Component.literal("  ").append(line), false);
            index++;
        }
    }

    private static MutableComponent formatRecordedOffer(MerchantOffer offer) {
        return formatCost(offer.getBaseCostA(), offer.getCostB())
                .append(Component.literal(" -> ")).append(formatStack(offer.getResult()));
    }

    private static Component formatLivePriceSuffix(MerchantOffer recordedOffer, MerchantOffer liveOffer) {
        if (liveOffer == null || !sameResult(recordedOffer, liveOffer) || !hasDifferentCurrentPrice(recordedOffer, liveOffer)) {
            return Component.empty();
        }
        return Component.translatable("command.dgutweak.detail.live_price",
                formatCost(liveOffer.getCostA(), liveOffer.getCostB()));
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

    private static MutableComponent formatCost(ItemStack costA, ItemStack costB) {
        MutableComponent line = formatStack(costA);
        if (!costB.isEmpty()) {
            line.append(Component.literal(" + ")).append(formatStack(costB));
        }
        return line;
    }

    private static MutableComponent formatStack(ItemStack stack) {
        MutableComponent result = Component.literal(stack.getCount() + "x ").append(stack.getHoverName());
        ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) enchantments = stack.getEnchantments();
        if (enchantments != null && !enchantments.isEmpty()) {
            result.append(Component.literal(": "));
            boolean first = true;
            for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : enchantments.entrySet()) {
                if (!first) result.append(Component.literal(", "));
                result.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
                first = false;
            }
        }
        return result;
    }

    private static Vec3 posCenter(RecordedVillagersData.VillagerRecord record) {
        return new Vec3(record.pos().getX() + 0.5, record.pos().getY() + 0.5, record.pos().getZ() + 0.5);
    }

    private static String formatPos(RecordedVillagersData.VillagerRecord record) {
        return record.pos().getX() + ", " + record.pos().getY() + ", " + record.pos().getZ();
    }

    private static Component professionComponent(String profession) {
        return Component.translatable(profession.equals("wandering_trader")
                ? "entity.minecraft.wandering_trader"
                : "entity.minecraft.villager." + profession);
    }
}
