package net.dgu.dgutweak.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dgu.dgutweak.DGuTweak;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Base64;

public final class DGuTweakClientConfig {
    private static final String CONFIG_FILE = "dgutweak-client.properties";
    private static final String DEFAULT_LANGUAGE = "zh_tw";
    private static final Map<String, Map<String, String>> TRANSLATIONS = createTranslations();
    private static final Map<String, Map<String, String>> VANILLA_TRANSLATIONS = new HashMap<>();

    private static boolean loaded;
    private static String language = DEFAULT_LANGUAGE;
    private static final Set<String> bestTrades = new HashSet<>();

    private DGuTweakClientConfig() {
    }

    public static void load() {
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CONFIG_FILE);
        Properties properties = new Properties();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                properties.setProperty("language", DEFAULT_LANGUAGE);
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    writer.write("# DGu Tweak client config\n");
                    writer.write("# language: zh_tw or en_us\n");
                    properties.store(writer, null);
                }
            } else {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
            language = normalizeLanguage(properties.getProperty("language", DEFAULT_LANGUAGE));
            bestTrades.clear();
            String encodedTrades = properties.getProperty("best_trades", "");
            for (String encoded : encodedTrades.split(",")) {
                if (encoded.isBlank()) continue;
                try {
                    bestTrades.add(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException exception) {
                    DGuTweak.LOGGER.warn("Ignoring invalid best trade config entry");
                }
            }
        } catch (IOException exception) {
            DGuTweak.LOGGER.warn("Failed to load DGu Tweak client config", exception);
            language = DEFAULT_LANGUAGE;
        }
        loaded = true;
    }

    public static String text(String key) {
        if (!loaded) {
            load();
        }
        Map<String, String> selected = TRANSLATIONS.getOrDefault(language, TRANSLATIONS.get(DEFAULT_LANGUAGE));
        String value = selected.get(key);
        if (value != null) {
            return value;
        }
        return TRANSLATIONS.get("en_us").getOrDefault(key, key);
    }

    public static String itemName(String fallback, String translationKey) {
        if (!loaded) {
            load();
        }
        if (translationKey == null || translationKey.isBlank()) {
            return fallback;
        }

        String suffix = "";
        int suffixStart = fallback.indexOf(": ");
        if (suffixStart >= 0) {
            suffix = fallback.substring(suffixStart);
        }

        String translated = vanillaTranslations(language).get(translationKey);
        if (translated == null) {
            translated = vanillaTranslations("en_us").get(translationKey);
        }
        return translated == null ? fallback : translated + suffix;
    }

    public static String language() {
        if (!loaded) {
            load();
        }
        return language;
    }

    public static boolean setLanguage(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!TRANSLATIONS.containsKey(normalized)) {
            return false;
        }

        language = normalized;
        loaded = true;
        save();
        return true;
    }

    public static boolean isBestTrade(String key) {
        if (!loaded) load();
        return bestTrades.contains(key);
    }

    public static boolean toggleBestTrade(String key) {
        if (!loaded) load();
        boolean selected = bestTrades.add(key);
        if (!selected) bestTrades.remove(key);
        save();
        return selected;
    }

    private static String normalizeLanguage(String value) {
        String normalized = value == null ? DEFAULT_LANGUAGE : value.trim().toLowerCase(Locale.ROOT);
        return TRANSLATIONS.containsKey(normalized) ? normalized : DEFAULT_LANGUAGE;
    }

    private static void save() {
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CONFIG_FILE);
        Properties properties = new Properties();
        properties.setProperty("language", language);
        properties.setProperty("best_trades", bestTrades.stream()
                .map(value -> Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                .sorted()
                .collect(java.util.stream.Collectors.joining(",")));
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("# DGu Tweak client config\n");
                writer.write("# language: zh_tw or en_us\n");
                properties.store(writer, null);
            }
        } catch (IOException exception) {
            DGuTweak.LOGGER.warn("Failed to save DGu Tweak client config", exception);
        }
    }

    private static Map<String, String> vanillaTranslations(String language) {
        return VANILLA_TRANSLATIONS.computeIfAbsent(language, DGuTweakClientConfig::loadVanillaTranslations);
    }

    private static Map<String, String> loadVanillaTranslations(String language) {
        Map<String, String> translations = new HashMap<>();
        Identifier id = Identifier.tryParse("minecraft:lang/" + language + ".json");
        if (id == null) {
            return Map.of();
        }
        List<Resource> resources = Minecraft.getInstance().getResourceManager().getResourceStack(id);
        for (Resource resource : resources) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        translations.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            } catch (RuntimeException | IOException exception) {
                DGuTweak.LOGGER.warn("Failed to load language resource {}", id, exception);
            }
        }
        return translations;
    }

    private static Map<String, Map<String, String>> createTranslations() {
        Map<String, Map<String, String>> translations = new HashMap<>();

        Map<String, String> zhTw = new HashMap<>();
        zhTw.put("title", "\u4ea4\u6613\u8cc7\u6599\u5eab");
        zhTw.put("search", "\u641c\u5c0b\u4ea4\u6613");
        zhTw.put("best", "\u6700\u4f73");
        zhTw.put("all", "\u5168\u90e8");
        zhTw.put("refresh", "\u5237\u65b0");
        zhTw.put("close", "\u95dc\u9589");
        zhTw.put("profession", "\u8077\u696d");
        zhTw.put("no_records", "\u6c92\u6709\u8a18\u9304");
        zhTw.put("best_trades", "\u6700\u4f73\u4ea4\u6613");
        zhTw.put("all_trades", "\u5168\u90e8\u4ea4\u6613");
        zhTw.put("no_trades", "\u9019\u500b\u7be9\u9078\u6c92\u6709\u4ea4\u6613");
        zhTw.put("price", "\u50f9\u683c");
        zhTw.put("villager", "\u6751\u6c11");
        zhTw.put("position", "\u4f4d\u7f6e");
        zhTw.put("distance", "\u8ddd\u96e2");
        zhTw.put("status", "\u72c0\u614b");
        zhTw.put("locked_trade", "\u672a\u89e3\u9396\u4ea4\u6613");
        zhTw.put("loaded", "\u5df2\u8f09\u5165");
        zhTw.put("not_loaded", "\u672a\u8f09\u5165");
        zhTw.put("glow", "\u767c\u5149");
        zhTw.put("track", "\u8ffd\u8e64");
        zhTw.put("add_best", "\u52a0\u5165\u6700\u4f73");
        zhTw.put("remove_best", "\u79fb\u51fa\u6700\u4f73");
        zhTw.put("tracking", "\u8ffd\u8e64");
        zhTw.put("other_dimension", "\u5176\u4ed6\u7dad\u5ea6");
        zhTw.put("live", "\u76ee\u524d");
        zhTw.put("recorded", "\u8a18\u9304");
        zhTw.put("current_price", "\u76ee\u524d");
        zhTw.put("recorded_price", "\u8a18\u9304");
        zhTw.put("category.enchanted_items", "\u9644\u9b54\u7269\u54c1");
        zhTw.put("profession.armorer", "\u88fd\u7532\u5e2b");
        zhTw.put("profession.butcher", "\u5c60\u592b");
        zhTw.put("profession.cartographer", "\u88fd\u5716\u5e2b");
        zhTw.put("profession.cleric", "\u7267\u5e2b");
        zhTw.put("profession.farmer", "\u8fb2\u6c11");
        zhTw.put("profession.fisherman", "\u6f01\u592b");
        zhTw.put("profession.fletcher", "\u88fd\u7bad\u5e2b");
        zhTw.put("profession.leatherworker", "\u76ae\u9769\u5de5\u5320");
        zhTw.put("profession.librarian", "\u5716\u66f8\u7ba1\u7406\u54e1");
        zhTw.put("profession.mason", "\u77f3\u5320");
        zhTw.put("profession.nitwit", "\u50bb\u5b50");
        zhTw.put("profession.none", "\u7121\u8077\u696d");
        zhTw.put("profession.shepherd", "\u7267\u7f8a\u4eba");
        zhTw.put("profession.toolsmith", "\u5de5\u5177\u5320");
        zhTw.put("profession.weaponsmith", "\u6b66\u5668\u5320");
        zhTw.put("profession.wandering_trader", "\u6d41\u6d6a\u5546\u4eba");
        translations.put("zh_tw", zhTw);

        Map<String, String> enUs = new HashMap<>();
        enUs.put("title", "Recorded Trades");
        enUs.put("search", "Search trades");
        enUs.put("best", "Best");
        enUs.put("all", "All");
        enUs.put("refresh", "Refresh");
        enUs.put("close", "Close");
        enUs.put("profession", "Profession");
        enUs.put("no_records", "No records");
        enUs.put("best_trades", "Best trades");
        enUs.put("all_trades", "All trades");
        enUs.put("no_trades", "No trades for this filter");
        enUs.put("price", "Price");
        enUs.put("villager", "Villager");
        enUs.put("position", "Position");
        enUs.put("distance", "Distance");
        enUs.put("status", "Status");
        enUs.put("locked_trade", "Locked trade");
        enUs.put("loaded", "Loaded");
        enUs.put("not_loaded", "Not loaded");
        enUs.put("glow", "Glow");
        enUs.put("track", "Track");
        enUs.put("add_best", "Add to best");
        enUs.put("remove_best", "Remove best");
        enUs.put("tracking", "Tracking");
        enUs.put("other_dimension", "other dimension");
        enUs.put("live", "live");
        enUs.put("recorded", "recorded");
        enUs.put("current_price", "current");
        enUs.put("recorded_price", "recorded");
        enUs.put("category.enchanted_items", "enchanted items");
        enUs.put("profession.armorer", "armorer");
        enUs.put("profession.butcher", "butcher");
        enUs.put("profession.cartographer", "cartographer");
        enUs.put("profession.cleric", "cleric");
        enUs.put("profession.farmer", "farmer");
        enUs.put("profession.fisherman", "fisherman");
        enUs.put("profession.fletcher", "fletcher");
        enUs.put("profession.leatherworker", "leatherworker");
        enUs.put("profession.librarian", "librarian");
        enUs.put("profession.mason", "mason");
        enUs.put("profession.nitwit", "nitwit");
        enUs.put("profession.none", "none");
        enUs.put("profession.shepherd", "shepherd");
        enUs.put("profession.toolsmith", "toolsmith");
        enUs.put("profession.weaponsmith", "weaponsmith");
        enUs.put("profession.wandering_trader", "wandering trader");
        translations.put("en_us", enUs);

        return translations;
    }
}
