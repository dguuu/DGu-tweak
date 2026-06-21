package net.dgu.dgutweak.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.villager.Villager;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AutoVillagerFilter {
    private static final int CYCLE_DELAY_TICKS = 0;
    private static final int RESPONSE_TIMEOUT_TICKS = 100;

    private static final List<Target> DEFAULT_TARGETS = List.of(
            new Target("librarian", id("minecraft:enchanted_book"), List.of(new EnchantmentRequirement(id("minecraft:mending"), 1)), 20),
            new Target("librarian", id("minecraft:enchanted_book"), List.of(new EnchantmentRequirement(id("minecraft:unbreaking"), 3)), 15)
    );
    private static List<Target> targets = new ArrayList<>(DEFAULT_TARGETS);

    private static boolean active;
    private static boolean awaitingResponse;
    private static int ticksUntilCycle;
    private static int responseTimeout;
    private static int cycleCount;
    private static Button toggleButton;
    private static String activeProfession;

    private AutoVillagerFilter() {
    }

    public static void initialize() {
        targets = new ArrayList<>(AutoFilterConfig.load(DEFAULT_TARGETS));
        ClientTickEvents.END_CLIENT_TICK.register(AutoVillagerFilter::tick);
    }

    public static void bindButton(Button button) {
        toggleButton = button;
        updateButtonLabel();
    }

    public static void openSettings() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new AutoFilterSettingsScreen(client.screen));
    }

    public static List<Target> targets() {
        return List.copyOf(targets);
    }

    public static void setTargets(List<Target> newTargets) {
        targets = new ArrayList<>(newTargets);
        AutoFilterConfig.save(targets);
    }

    public static void toggle() {
        if (active) {
            stop("message.dgutweak.auto_filter.disabled");
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof MerchantScreen screen)
                || !screen.getMenu().showProgressBar()
                || screen.getMenu().getTraderXp() > 0) {
            notifyPlayer(Component.translatable("message.dgutweak.auto_filter.unavailable"));
            return;
        }

        activeProfession = findNearestProfession(client);
        if (activeProfession == null || targets.stream().noneMatch(target -> target.profession().equals(activeProfession))) {
            notifyPlayer(Component.translatable("message.dgutweak.auto_filter.no_profession_targets"));
            return;
        }
        boolean impossibleTarget = targets.stream()
                .filter(target -> target.profession().equals(activeProfession))
                .anyMatch(target -> target.enchantments().stream()
                        .anyMatch(requirement -> !ItemEnchantmentsScreen.isPossibleTradeEnchantment(
                                target.resultItem(), requirement)));
        if (impossibleTarget) {
            notifyPlayer(Component.translatable("message.dgutweak.auto_filter.impossible_trade_enchantment"));
            return;
        }

        active = true;
        awaitingResponse = false;
        cycleCount = 0;
        ticksUntilCycle = 0;
        updateButtonLabel();
        notifyPlayer(Component.translatable("message.dgutweak.auto_filter.enabled"));
    }

    public static void onMerchantOffersUpdated(MerchantOffers offers) {
        if (!active || !awaitingResponse) {
            return;
        }

        awaitingResponse = false;
        Match match = findMatch(offers);
        if (match != null) {
            active = false;
            updateButtonLabel();
            Component foundName = match.enchantment() == null
                    ? match.itemName()
                    : Enchantment.getFullname(match.enchantment(), match.level());
            notifyPlayer(Component.translatable(
                    "message.dgutweak.auto_filter.found",
                    foundName,
                    match.price(),
                    cycleCount
            ));
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F)
            );
            return;
        }

        ticksUntilCycle = CYCLE_DELAY_TICKS;
    }

    private static void tick(Minecraft client) {
        if (!active) {
            return;
        }
        if (!(client.screen instanceof MerchantScreen)) {
            stop("message.dgutweak.auto_filter.closed");
            return;
        }

        if (awaitingResponse) {
            if (--responseTimeout <= 0) {
                awaitingResponse = false;
                ticksUntilCycle = CYCLE_DELAY_TICKS;
            }
            return;
        }

        if (ticksUntilCycle-- <= 0) {
            sendCyclePacket();
        }
    }

    private static void sendCyclePacket() {
        try {
            Class<?> packetClass = Class.forName("de.maxhenkel.tradecycling.net.CycleTradesPacket");
            Object packet = packetClass.getConstructor().newInstance();
            ClientPlayNetworking.send((CustomPacketPayload) packet);
            awaitingResponse = true;
            responseTimeout = RESPONSE_TIMEOUT_TICKS;
            cycleCount++;
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException | ClassCastException exception) {
            active = false;
            notifyPlayer(Component.translatable("message.dgutweak.auto_filter.missing_dependency"));
        }
    }

    private static Match findMatch(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            if (offer == null || !offer.getBaseCostA().is(Items.EMERALD)) {
                continue;
            }

            ItemStack result = offer.getResult();
            int price = offer.getBaseCostA().getCount();
            Identifier resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
            for (Target target : targets) {
                if (!target.profession().equals(activeProfession) || !target.resultItem().equals(resultId)
                        || price >= target.maxEmeraldPrice()) {
                    continue;
                }
                if (target.enchantments().isEmpty()) {
                    return new Match(null, 0, result.getHoverName(), price);
                }
                ItemEnchantments enchantments = result.get(DataComponents.STORED_ENCHANTMENTS);
                if (enchantments == null || enchantments.isEmpty()) {
                    enchantments = result.getEnchantments();
                }
                if (enchantments == null || enchantments.isEmpty()) {
                    continue;
                }
                if (matchesAllEnchantments(enchantments, target.enchantments())) {
                    EnchantmentRequirement first = target.enchantments().get(0);
                    var holder = enchantments.entrySet().stream()
                            .filter(entry -> entry.getKey().unwrapKey().map(key -> key.identifier().equals(first.enchantmentId())).orElse(false))
                            .map(java.util.Map.Entry::getKey)
                            .findFirst()
                            .orElse(null);
                    return new Match(target.enchantments().size() == 1 ? holder : null,
                            first.level(), result.getHoverName(), price);
                }
            }
        }
        return null;
    }

    private static boolean matchesAllEnchantments(ItemEnchantments actual, List<EnchantmentRequirement> required) {
        for (EnchantmentRequirement requirement : required) {
            boolean matched = actual.entrySet().stream().anyMatch(entry ->
                    entry.getIntValue() == requirement.level()
                            && entry.getKey().unwrapKey()
                            .map(key -> key.identifier().equals(requirement.enchantmentId()))
                            .orElse(false));
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static String findNearestProfession(Minecraft client) {
        if (client.player == null || client.level == null) {
            return null;
        }
        return client.level.getEntitiesOfClass(Villager.class, client.player.getBoundingBox().inflate(6.0D)).stream()
                .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(client.player)))
                .flatMap(villager -> villager.getVillagerData().profession().unwrapKey())
                .map(key -> key.identifier().getPath())
                .orElse(null);
    }

    private static void stop(String translationKey) {
        active = false;
        awaitingResponse = false;
        updateButtonLabel();
        notifyPlayer(Component.translatable(translationKey));
    }

    private static void updateButtonLabel() {
        if (toggleButton != null) {
            toggleButton.setMessage(Component.translatable(active
                    ? "gui.dgutweak.auto_filter.stop"
                    : "gui.dgutweak.auto_filter.start"));
        }
    }

    private static void notifyPlayer(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(message, false);
        }
    }

    private static Identifier id(String value) {
        Identifier identifier = Identifier.tryParse(value);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid identifier: " + value);
        }
        return identifier;
    }

    public record Target(String profession, Identifier resultItem, List<EnchantmentRequirement> enchantments, int maxEmeraldPrice) {
        public Target {
            enchantments = List.copyOf(enchantments);
        }
    }

    public record EnchantmentRequirement(Identifier enchantmentId, int level) {
    }

    private record Match(net.minecraft.core.Holder<Enchantment> enchantment, int level, Component itemName, int price) {
    }
}
