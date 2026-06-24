package net.dgu.dgutweak.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProfessionTradeCatalog {
    static final Identifier TERRACOTTA_GROUP = Identifier.fromNamespaceAndPath("dgutweak", "terracotta");
    static final Identifier GLAZED_TERRACOTTA_GROUP = Identifier.fromNamespaceAndPath("dgutweak", "glazed_terracotta");
    static final List<String> COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );
    static final List<Identifier> TERRACOTTA_VARIANTS = COLORS.stream()
            .map(color -> Identifier.fromNamespaceAndPath("minecraft", color + "_terracotta"))
            .toList();
    static final List<Identifier> GLAZED_TERRACOTTA_VARIANTS = COLORS.stream()
            .map(color -> Identifier.fromNamespaceAndPath("minecraft", color + "_glazed_terracotta"))
            .toList();

    static final List<String> PROFESSIONS = List.of(
            "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
            "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith"
    );
    private static final Map<String, List<String>> ITEMS = new LinkedHashMap<>();

    static {
        put("armorer", "bell", "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots",
                "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots", "shield");
        put("butcher", "rabbit_stew", "cooked_porkchop", "cooked_chicken");
        put("cartographer", "map", "item_frame", "white_banner");
        put("cleric", "redstone", "lapis_lazuli", "glowstone", "ender_pearl", "experience_bottle");
        put("farmer", "bread", "pumpkin_pie", "apple", "cookie", "cake", "suspicious_stew", "golden_carrot", "glistering_melon_slice");
        put("fisherman", "cooked_cod", "cooked_salmon", "campfire", "fishing_rod");
        put("fletcher", "arrow", "flint", "bow", "crossbow", "tipped_arrow");
        put("leatherworker", "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "leather_horse_armor", "saddle");
        put("librarian", "enchanted_book", "bookshelf", "lantern", "glass", "clock", "compass", "name_tag");
        put("mason", "brick", "chiseled_stone_bricks", "dripstone_block", "polished_andesite", "polished_diorite", "polished_granite", "terracotta", "glazed_terracotta", "quartz_block", "quartz_pillar");
        put("shepherd", "shears", "white_wool", "white_carpet", "white_bed", "white_banner", "painting");
        put("toolsmith", "stone_axe", "stone_shovel", "stone_pickaxe", "stone_hoe", "iron_axe", "iron_shovel", "iron_pickaxe", "diamond_axe", "diamond_shovel", "diamond_pickaxe", "diamond_hoe", "bell");
        put("weaponsmith", "iron_axe", "iron_sword", "diamond_axe", "diamond_sword", "bell");
    }

    private ProfessionTradeCatalog() {
    }

    static List<Choice> choices(String profession) {
        return ITEMS.getOrDefault(profession, List.of()).stream()
                .map(ProfessionTradeCatalog::choice)
                .toList();
    }

    static Component professionName(String profession) {
        return Component.translatable("entity.minecraft.villager." + profession);
    }

    static Choice choice(String path) {
        if ("terracotta".equals(path)) {
            return new Choice(TERRACOTTA_GROUP, Component.translatable("block.minecraft.terracotta"));
        }
        if ("glazed_terracotta".equals(path)) {
            return new Choice(GLAZED_TERRACOTTA_GROUP, Component.translatable("gui.dgutweak.auto_filter.glazed_terracotta"));
        }
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", path);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return new Choice(id, item == null ? Component.literal(path) : item.getName());
    }

    static boolean isVariantGroup(Identifier id) {
        return TERRACOTTA_GROUP.equals(id) || GLAZED_TERRACOTTA_GROUP.equals(id);
    }

    static boolean matchesVariantGroup(Identifier group, Identifier item) {
        return variants(group).contains(item);
    }

    static List<Identifier> variants(Identifier group) {
        if (TERRACOTTA_GROUP.equals(group)) {
            return TERRACOTTA_VARIANTS;
        }
        if (GLAZED_TERRACOTTA_GROUP.equals(group)) {
            return GLAZED_TERRACOTTA_VARIANTS;
        }
        return List.of();
    }

    static Component variantName(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? Component.literal(id.getPath()) : item.getName();
    }

    private static void put(String profession, String... items) {
        ITEMS.put(profession, List.of(items));
    }

    record Choice(Identifier id, Component name) {
    }
}
