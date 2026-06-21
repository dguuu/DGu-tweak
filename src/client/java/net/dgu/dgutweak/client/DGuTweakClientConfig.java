package net.dgu.dgutweak.client;

import net.dgu.dgutweak.DGuTweak;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public final class DGuTweakClientConfig {
    private static final String CONFIG_FILE = "dgutweak-client.properties";
    private static final Set<String> BEST_TRADES = new HashSet<>();
    private static boolean loaded;

    private DGuTweakClientConfig() { }

    public static void load() {
        Properties properties = new Properties();
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
            BEST_TRADES.clear();
            for (String encoded : properties.getProperty("best_trades", "").split(",")) {
                if (encoded.isBlank()) continue;
                try {
                    BEST_TRADES.add(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException exception) {
                    DGuTweak.LOGGER.warn("Ignoring invalid best trade config entry");
                }
            }
        } catch (IOException exception) {
            DGuTweak.LOGGER.warn("Failed to load DGu Tweak client config", exception);
        }
        loaded = true;
    }

    public static boolean isBestTrade(String key) {
        if (!loaded) load();
        return BEST_TRADES.contains(key);
    }

    public static void toggleBestTrade(String key) {
        if (!loaded) load();
        if (!BEST_TRADES.add(key)) BEST_TRADES.remove(key);
        save();
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("best_trades", BEST_TRADES.stream()
                .map(value -> Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                .sorted()
                .collect(java.util.stream.Collectors.joining(",")));
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("# DGu Tweak client config\n");
                properties.store(writer, null);
            }
        } catch (IOException exception) {
            DGuTweak.LOGGER.warn("Failed to save DGu Tweak client config", exception);
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CONFIG_FILE);
    }
}
