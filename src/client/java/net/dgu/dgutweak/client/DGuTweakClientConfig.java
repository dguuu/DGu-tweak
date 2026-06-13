package net.dgu.dgutweak.client;

import net.dgu.dgutweak.DGuTweak;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class DGuTweakClientConfig {
    private static final String CONFIG_FILE = "dgutweak-client.properties";
    private static final String DEFAULT_LANGUAGE = "zh_tw";
    private static final Map<String, Map<String, String>> TRANSLATIONS = createTranslations();

    private static boolean loaded;
    private static String language = DEFAULT_LANGUAGE;

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

    private static String normalizeLanguage(String value) {
        String normalized = value == null ? DEFAULT_LANGUAGE : value.trim().toLowerCase(Locale.ROOT);
        return TRANSLATIONS.containsKey(normalized) ? normalized : DEFAULT_LANGUAGE;
    }

    private static void save() {
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CONFIG_FILE);
        Properties properties = new Properties();
        properties.setProperty("language", language);
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

    private static Map<String, Map<String, String>> createTranslations() {
        Map<String, Map<String, String>> translations = new HashMap<>();

        Map<String, String> zhTw = new HashMap<>();
        zhTw.put("title", "交易資料庫");
        zhTw.put("search", "搜尋交易");
        zhTw.put("best", "最佳");
        zhTw.put("all", "全部");
        zhTw.put("refresh", "刷新");
        zhTw.put("close", "關閉");
        zhTw.put("profession", "職業");
        zhTw.put("no_records", "沒有記錄");
        zhTw.put("best_trades", "最佳交易");
        zhTw.put("all_trades", "全部交易");
        zhTw.put("no_trades", "這個篩選沒有交易");
        zhTw.put("price", "價格");
        zhTw.put("villager", "村民");
        zhTw.put("position", "位置");
        zhTw.put("distance", "距離");
        zhTw.put("status", "狀態");
        zhTw.put("locked_trade", "未解鎖交易");
        zhTw.put("loaded", "已載入");
        zhTw.put("not_loaded", "未載入");
        zhTw.put("glow", "發光");
        zhTw.put("track", "追蹤");
        zhTw.put("tracking", "追蹤");
        zhTw.put("other_dimension", "其他維度");
        zhTw.put("live", "目前");
        zhTw.put("recorded", "記錄");
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
        enUs.put("tracking", "Tracking");
        enUs.put("other_dimension", "other dimension");
        enUs.put("live", "live");
        enUs.put("recorded", "recorded");
        translations.put("en_us", enUs);

        return translations;
    }
}
