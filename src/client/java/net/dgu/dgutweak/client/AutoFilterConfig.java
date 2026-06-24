package net.dgu.dgutweak.client;

import net.dgu.dgutweak.DGuTweak;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class AutoFilterConfig {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("dgutweak")
            .resolve("auto_filter.properties");

    private AutoFilterConfig() {
    }

    static Config load(List<AutoVillagerFilter.Target> defaults) {
        if (!Files.isRegularFile(PATH)) {
            save(defaults, Map.of());
            return new Config(defaults, Map.of());
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(PATH)) {
            properties.load(input);
            int count = Integer.parseInt(properties.getProperty("target.count", "0"));
            List<AutoVillagerFilter.Target> targets = new ArrayList<>();
            Map<String, Integer> requiredMatches = readRequiredMatches(properties);
            for (int index = 0; index < count; index++) {
                String profession = properties.getProperty("target." + index + ".profession", "librarian");
                Identifier resultItem = Identifier.tryParse(properties.getProperty("target." + index + ".result_item", "minecraft:enchanted_book"));
                List<AutoVillagerFilter.EnchantmentRequirement> enchantments = readEnchantments(properties, index);
                List<Identifier> variants = readVariants(properties, index);
                int price = Integer.parseInt(properties.getProperty("target." + index + ".max_price", "0"));
                if (ProfessionTradeCatalog.PROFESSIONS.contains(profession) && resultItem != null && price > 0
                        && enchantments.stream().allMatch(enchantment -> enchantment.level() > 0)) {
                    targets.add(new AutoVillagerFilter.Target(profession, resultItem, enchantments, variants, price));
                }
            }
            return new Config(targets.isEmpty() ? defaults : targets, requiredMatches);
        } catch (IOException | NumberFormatException exception) {
            DGuTweak.LOGGER.warn("Failed to load auto filter config", exception);
            return new Config(defaults, Map.of());
        }
    }

    static void save(List<AutoVillagerFilter.Target> targets, Map<String, Integer> requiredMatches) {
        Properties properties = new Properties();
        properties.setProperty("target.count", Integer.toString(targets.size()));
        for (int index = 0; index < targets.size(); index++) {
            AutoVillagerFilter.Target target = targets.get(index);
            properties.setProperty("target." + index + ".profession", target.profession());
            properties.setProperty("target." + index + ".result_item", target.resultItem().toString());
            properties.setProperty("target." + index + ".enchantment.count", Integer.toString(target.enchantments().size()));
            for (int enchantmentIndex = 0; enchantmentIndex < target.enchantments().size(); enchantmentIndex++) {
                AutoVillagerFilter.EnchantmentRequirement enchantment = target.enchantments().get(enchantmentIndex);
                properties.setProperty("target." + index + ".enchantment." + enchantmentIndex + ".id", enchantment.enchantmentId().toString());
                properties.setProperty("target." + index + ".enchantment." + enchantmentIndex + ".level", Integer.toString(enchantment.level()));
            }
            properties.setProperty("target." + index + ".variant.count", Integer.toString(target.variants().size()));
            for (int variantIndex = 0; variantIndex < target.variants().size(); variantIndex++) {
                properties.setProperty("target." + index + ".variant." + variantIndex, target.variants().get(variantIndex).toString());
            }
            properties.setProperty("target." + index + ".max_price", Integer.toString(target.maxEmeraldPrice()));
        }
        for (Map.Entry<String, Integer> entry : requiredMatches.entrySet()) {
            if (ProfessionTradeCatalog.PROFESSIONS.contains(entry.getKey()) && entry.getValue() > 0) {
                properties.setProperty("profession." + entry.getKey() + ".required_matches", Integer.toString(entry.getValue()));
            }
        }

        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream output = Files.newOutputStream(PATH)) {
                properties.store(output, "DGu-tweak auto villager filter");
            }
        } catch (IOException exception) {
            DGuTweak.LOGGER.warn("Failed to save auto filter config", exception);
        }
    }

    private static Map<String, Integer> readRequiredMatches(Properties properties) {
        Map<String, Integer> requiredMatches = new HashMap<>();
        for (String profession : ProfessionTradeCatalog.PROFESSIONS) {
            int required = Integer.parseInt(properties.getProperty("profession." + profession + ".required_matches", "0"));
            if (required > 0) {
                requiredMatches.put(profession, required);
            }
        }
        return requiredMatches;
    }

    private static List<AutoVillagerFilter.EnchantmentRequirement> readEnchantments(Properties properties, int targetIndex) {
        List<AutoVillagerFilter.EnchantmentRequirement> enchantments = new ArrayList<>();
        int count = Integer.parseInt(properties.getProperty("target." + targetIndex + ".enchantment.count", "-1"));
        if (count < 0) {
            String legacyId = properties.getProperty("target." + targetIndex + ".enchantment", "");
            int legacyLevel = Integer.parseInt(properties.getProperty("target." + targetIndex + ".level", "0"));
            Identifier id = legacyId.isBlank() ? null : Identifier.tryParse(legacyId);
            if (id != null && legacyLevel > 0) {
                enchantments.add(new AutoVillagerFilter.EnchantmentRequirement(id, legacyLevel));
            }
            return enchantments;
        }
        for (int index = 0; index < count; index++) {
            Identifier id = Identifier.tryParse(properties.getProperty("target." + targetIndex + ".enchantment." + index + ".id", ""));
            int level = Integer.parseInt(properties.getProperty("target." + targetIndex + ".enchantment." + index + ".level", "0"));
            if (id != null && level > 0) {
                enchantments.add(new AutoVillagerFilter.EnchantmentRequirement(id, level));
            }
        }
        return enchantments;
    }

    private static List<Identifier> readVariants(Properties properties, int targetIndex) {
        List<Identifier> variants = new ArrayList<>();
        int count = Integer.parseInt(properties.getProperty("target." + targetIndex + ".variant.count", "0"));
        for (int index = 0; index < count; index++) {
            Identifier id = Identifier.tryParse(properties.getProperty("target." + targetIndex + ".variant." + index, ""));
            if (id != null) {
                variants.add(id);
            }
        }
        return variants;
    }

    record Config(List<AutoVillagerFilter.Target> targets, Map<String, Integer> requiredMatches) {
    }
}
