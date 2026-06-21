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
import java.util.List;
import java.util.Properties;

final class AutoFilterConfig {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("dgutweak")
            .resolve("auto_filter.properties");

    private AutoFilterConfig() {
    }

    static List<AutoVillagerFilter.Target> load(List<AutoVillagerFilter.Target> defaults) {
        if (!Files.isRegularFile(PATH)) {
            save(defaults);
            return defaults;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(PATH)) {
            properties.load(input);
            int count = Integer.parseInt(properties.getProperty("target.count", "0"));
            List<AutoVillagerFilter.Target> targets = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String profession = properties.getProperty("target." + index + ".profession", "librarian");
                Identifier resultItem = Identifier.tryParse(properties.getProperty("target." + index + ".result_item", "minecraft:enchanted_book"));
                String enchantmentValue = properties.getProperty("target." + index + ".enchantment", "");
                Identifier enchantment = enchantmentValue.isBlank() ? null : Identifier.tryParse(enchantmentValue);
                int level = Integer.parseInt(properties.getProperty("target." + index + ".level", "0"));
                int price = Integer.parseInt(properties.getProperty("target." + index + ".max_price", "0"));
                if (ProfessionTradeCatalog.PROFESSIONS.contains(profession) && resultItem != null && price > 0
                        && (enchantment == null || level > 0)) {
                    targets.add(new AutoVillagerFilter.Target(profession, resultItem, enchantment, level, price));
                }
            }
            return targets.isEmpty() ? defaults : targets;
        } catch (IOException | NumberFormatException exception) {
            DGuTweak.LOGGER.warn("Failed to load auto filter config", exception);
            return defaults;
        }
    }

    static void save(List<AutoVillagerFilter.Target> targets) {
        Properties properties = new Properties();
        properties.setProperty("target.count", Integer.toString(targets.size()));
        for (int index = 0; index < targets.size(); index++) {
            AutoVillagerFilter.Target target = targets.get(index);
            properties.setProperty("target." + index + ".profession", target.profession());
            properties.setProperty("target." + index + ".result_item", target.resultItem().toString());
            properties.setProperty("target." + index + ".enchantment", target.enchantmentId() == null ? "" : target.enchantmentId().toString());
            properties.setProperty("target." + index + ".level", Integer.toString(target.level()));
            properties.setProperty("target." + index + ".max_price", Integer.toString(target.maxEmeraldPrice()));
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
}
