package net.dgu.dgutweak.client;

import net.dgu.dgutweak.DGuTweak;
import net.dgu.dgutweak.networking.GlowVillagerC2SPayload;
import net.dgu.dgutweak.networking.RequestTradeListC2SPayload;
import net.dgu.dgutweak.networking.TradeListS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TradeDatabaseScreen extends Screen {
    private static final Identifier TRADE_CSV = DGuTweak.id("trade.csv");
    private static final int PANEL = 0xE0202020;
    private static final int PANEL_DARK = 0xE8181818;
    private static final int BORDER = 0xFF555555;
    private static final int SELECTED = 0xFF315E7D;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int ACCENT = 0xFF7DD3FC;
    private static final String ENCHANTED_ITEMS_CATEGORY = "__enchanted_items";
    private static final String ENCHANTED_BOOK_KEY = "item.minecraft.enchanted_book";

    private static final Map<String, Integer> MAX_ENCHANT_LEVELS = loadMaxEnchantLevels();

    private final List<TradeListS2CPayload.Entry> allEntries = new ArrayList<>();
    private List<String> professions = List.of();
    private List<TradeListS2CPayload.Entry> visibleEntries = List.of();
    private EditBox searchBox;
    private boolean bestOnly = true;
    private String selectedProfession = "";
    private int professionScroll;
    private int tradeScroll;
    private int selectedTradeIndex;
    private TradeListS2CPayload.Entry trackedEntry;
    private int actionButtonX;
    private int actionButtonY;
    private int refreshTicks;

    public TradeDatabaseScreen(List<TradeListS2CPayload.Entry> entries) {
        super(Component.literal(t("title")));
        DGuTweakClientConfig.load();
        replaceEntries(entries);
    }

    public void replaceEntries(List<TradeListS2CPayload.Entry> entries) {
        String profession = this.selectedProfession;
        String selectedTradeKey = this.visibleEntries.isEmpty() ? "" : tradeKey(selectedEntry());
        this.allEntries.clear();
        this.allEntries.addAll(DGuTweakClient.applyRememberedMerchantPrices(entries));
        this.allEntries.sort(Comparator
                .comparing(TradeListS2CPayload.Entry::profession)
                .thenComparing(TradeListS2CPayload.Entry::resultName)
                .thenComparingInt(TradeDatabaseScreen::effectivePrice)
                .thenComparingDouble(entry -> entry.distance() < 0 ? Double.MAX_VALUE : entry.distance()));
        rebuildProfessions();
        if (!profession.isBlank() && this.professions.contains(profession)) {
            this.selectedProfession = profession;
        }
        rebuildVisibleEntries();
        restoreSelectedTrade(selectedTradeKey);
    }

    public List<TradeListS2CPayload.Entry> entriesSnapshot() {
        return List.copyOf(this.allEntries);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(650, this.width - 24);
        int left = (this.width - panelWidth) / 2;
        DGuTweakClientConfig.load();
        this.searchBox = new EditBox(this.font, left + 10, 34, panelWidth - 190, 18, Component.literal(t("search")));
        this.addRenderableWidget(this.searchBox);
        this.addRenderableWidget(Button.builder(Component.literal(this.bestOnly ? t("best") : t("all")), button -> {
                    this.bestOnly = !this.bestOnly;
                    button.setMessage(Component.literal(this.bestOnly ? t("best") : t("all")));
                    rebuildVisibleEntries();
                })
                .pos(left + panelWidth - 174, 34)
                .size(48, 18)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(t("refresh")), button -> DGuTweakClient.requestTradeList())
                .pos(left + panelWidth - 122, 34)
                .size(56, 18)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(t("close")), button -> Minecraft.getInstance().setScreen(null))
                .pos(left + panelWidth - 62, 34)
                .size(52, 18)
                .build());
        requestLiveRefresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.refreshTicks >= 20) {
            this.refreshTicks = 0;
            requestLiveRefresh();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);
        int panelWidth = Math.min(650, this.width - 24);
        int panelHeight = Math.min(285, this.height - 20);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int contentTop = top + 58;
        int contentBottom = top + panelHeight - 10;

        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        graphics.fill(left, top, left + panelWidth, top + 24, PANEL_DARK);
        drawBorder(graphics, left, top, panelWidth, panelHeight);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 8, TEXT);
        super.render(graphics, mouseX, mouseY, partialTick);

        int professionsLeft = left + 10;
        int professionsRight = professionsLeft + 120;
        int tradesLeft = professionsRight + 8;
        int tradesRight = tradesLeft + 275;
        int detailsLeft = tradesRight + 12;
        int detailsRight = left + panelWidth - 12;

        drawProfessionList(graphics, professionsLeft, contentTop, professionsRight, contentBottom, mouseX, mouseY);
        drawTradeList(graphics, tradesLeft, contentTop, tradesRight, contentBottom, mouseX, mouseY);
        drawDetails(graphics, detailsLeft, contentTop, detailsRight, contentBottom);

        if (this.trackedEntry != null) {
            String text = t("tracking") + " " + professionName(this.trackedEntry.profession()) + " @ " + posText(this.trackedEntry);
            graphics.drawCenteredString(this.font, text, this.width / 2, this.height - 14, ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        int panelWidth = Math.min(650, this.width - 24);
        int panelHeight = Math.min(285, this.height - 20);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int contentTop = top + 58;
        int contentBottom = top + panelHeight - 10;

        int professionsLeft = left + 10;
        int professionsRight = professionsLeft + 120;
        int tradesLeft = professionsRight + 8;
        int tradesRight = tradesLeft + 275;

        if (inside(event, professionsLeft, contentTop, professionsRight, contentBottom)) {
            int clicked = this.professionScroll + ((int) event.y() - contentTop) / 18;
            if (clicked >= 0 && clicked < this.professions.size()) {
                this.selectedProfession = this.professions.get(clicked);
                this.tradeScroll = 0;
                this.selectedTradeIndex = 0;
                rebuildVisibleEntries();
                return true;
            }
        }

        if (inside(event, tradesLeft, contentTop, tradesRight, contentBottom)) {
            int clicked = this.tradeScroll + ((int) event.y() - contentTop) / 24;
            if (clicked >= 0 && clicked < this.visibleEntries.size()) {
                this.selectedTradeIndex = clicked;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelWidth = Math.min(650, this.width - 24);
        int panelHeight = Math.min(285, this.height - 20);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int contentTop = top + 58;
        int contentBottom = top + panelHeight - 10;
        int professionsRight = left + 130;

        if (mouseX <= professionsRight) {
            int maxScroll = Math.max(0, this.professions.size() - professionRows());
            this.professionScroll = Math.max(0, Math.min(maxScroll, this.professionScroll - (int) Math.signum(scrollY)));
        } else if (mouseY >= contentTop && mouseY <= contentBottom) {
            int maxScroll = Math.max(0, this.visibleEntries.size() - tradeRows());
            this.tradeScroll = Math.max(0, Math.min(maxScroll, this.tradeScroll - (int) Math.signum(scrollY)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.searchBox.keyPressed(event)) {
            rebuildVisibleEntries();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchBox.charTyped(event)) {
            rebuildVisibleEntries();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!this.visibleEntries.isEmpty()) {
            TradeListS2CPayload.Entry entry = selectedEntry();
            if (inside(event, this.actionButtonX, this.actionButtonY, this.actionButtonX + 56, this.actionButtonY + 16)) {
                ClientPlayNetworking.send(new GlowVillagerC2SPayload(entry.uuid(), true));
                return true;
            }
            if (inside(event, this.actionButtonX + 62, this.actionButtonY, this.actionButtonX + 118, this.actionButtonY + 16)) {
                this.trackedEntry = entry;
                ClientPlayNetworking.send(new GlowVillagerC2SPayload(entry.uuid(), false));
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    private void rebuildProfessions() {
        this.professions = this.allEntries.stream()
                .map(TradeDatabaseScreen::categoryKey)
                .distinct()
                .sorted()
                .toList();
        if ((this.selectedProfession.isBlank() || !this.professions.contains(this.selectedProfession)) && !this.professions.isEmpty()) {
            this.selectedProfession = this.professions.get(0);
        }
    }

    private void rebuildVisibleEntries() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<TradeListS2CPayload.Entry> matches = this.allEntries.stream()
                .filter(entry -> categoryKey(entry).equals(this.selectedProfession))
                .filter(entry -> query.isBlank() || searchable(entry).contains(query))
                .toList();

        if (this.bestOnly) {
            Map<String, TradeListS2CPayload.Entry> best = new LinkedHashMap<>();
            for (TradeListS2CPayload.Entry entry : matches) {
                if (!isBestEligible(entry)) {
                    continue;
                }
                String key = bestGroupKey(entry);
                TradeListS2CPayload.Entry existing = best.get(key);
                if (existing == null || compareValue(entry, existing) < 0) {
                    best.put(key, entry);
                }
            }
            this.visibleEntries = new ArrayList<>(best.values());
        } else {
            this.visibleEntries = new ArrayList<>(matches);
        }

        this.selectedTradeIndex = Math.min(this.selectedTradeIndex, Math.max(0, this.visibleEntries.size() - 1));
        this.tradeScroll = Math.min(this.tradeScroll, Math.max(0, this.visibleEntries.size() - tradeRows()));
    }

    private void restoreSelectedTrade(String selectedTradeKey) {
        if (selectedTradeKey.isBlank()) {
            return;
        }
        for (int index = 0; index < this.visibleEntries.size(); index++) {
            if (tradeKey(this.visibleEntries.get(index)).equals(selectedTradeKey)) {
                this.selectedTradeIndex = index;
                return;
            }
        }
    }

    private void drawProfessionList(GuiGraphics graphics, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        graphics.drawString(this.font, t("profession"), left, top - 12, ACCENT);
        if (this.professions.isEmpty()) {
            graphics.drawString(this.font, t("no_records"), left, top, MUTED);
            return;
        }
        int rows = Math.min(professionRows(), this.professions.size() - this.professionScroll);
        for (int row = 0; row < rows; row++) {
            int index = this.professionScroll + row;
            String profession = this.professions.get(index);
            int y = top + row * 18;
            boolean selected = profession.equals(this.selectedProfession);
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + 17;
            if (selected || hovered) {
                graphics.fill(left - 2, y - 1, right, y + 17, selected ? SELECTED : 0x55444444);
            }
            graphics.drawString(this.font, trim(professionName(profession) + " (" + countProfession(profession) + ")", right - left - 4), left, y + 4, selected ? 0xFFFFFFFF : TEXT);
        }
    }

    private void drawTradeList(GuiGraphics graphics, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.bestOnly ? t("best_trades") : t("all_trades"), left, top - 12, ACCENT);
        if (this.visibleEntries.isEmpty()) {
            graphics.drawString(this.font, t("no_trades"), left, top, MUTED);
            return;
        }
        int rows = Math.min(tradeRows(), this.visibleEntries.size() - this.tradeScroll);
        for (int row = 0; row < rows; row++) {
            int index = this.tradeScroll + row;
            TradeListS2CPayload.Entry entry = this.visibleEntries.get(index);
            int y = top + row * 24;
            boolean selected = index == this.selectedTradeIndex;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + 23;
            if (selected || hovered) {
                graphics.fill(left - 2, y - 1, right, y + 22, selected ? SELECTED : 0x55444444);
            }
            graphics.drawString(this.font, trim(entry.resultCount() + "x " + resultName(entry), right - left - 6), left, y, selected ? 0xFFFFFFFF : TEXT);
            graphics.drawString(this.font, trim(priceText(entry) + " | " + distanceText(entry), right - left - 6), left, y + 10, entry.live() ? ACCENT : MUTED);
        }
    }

    private void drawDetails(GuiGraphics graphics, int left, int top, int right, int bottom) {
        if (this.visibleEntries.isEmpty()) {
            return;
        }
        TradeListS2CPayload.Entry entry = selectedEntry();
        int y = top;
        graphics.drawString(this.font, trim(entry.resultCount() + "x " + resultName(entry), right - left), left, y, 0xFFFFFFFF);
        y += 14;
        y = drawLine(graphics, t("price"), priceText(entry), left, right, y);
        y = drawLine(graphics, t("villager"), professionName(entry.profession()) + " L" + entry.level(), left, right, y);
        y = drawLine(graphics, t("position"), posText(entry), left, right, y);
        y = drawLine(graphics, t("distance"), distanceText(entry), left, right, y);
        y = drawLine(graphics, t("status"), entry.locked() ? t("locked_trade") : (entry.live() ? t("loaded") : t("not_loaded")), left, right, y);

        this.actionButtonX = left;
        this.actionButtonY = Math.min(bottom - 18, y + 8);
        graphics.fill(this.actionButtonX, this.actionButtonY, this.actionButtonX + 56, this.actionButtonY + 16, 0xFF2D4F66);
        graphics.drawCenteredString(this.font, t("glow"), this.actionButtonX + 28, this.actionButtonY + 4, 0xFFFFFFFF);
        graphics.fill(this.actionButtonX + 62, this.actionButtonY, this.actionButtonX + 118, this.actionButtonY + 16, 0xFF3F4F2D);
        graphics.drawCenteredString(this.font, t("track"), this.actionButtonX + 90, this.actionButtonY + 4, 0xFFFFFFFF);
    }

    private int drawLine(GuiGraphics graphics, String label, String value, int left, int right, int y) {
        graphics.drawString(this.font, label, left, y, ACCENT);
        y += 10;
        for (FormattedCharSequence line : this.font.split(Component.literal(value), right - left)) {
            graphics.drawString(this.font, line, left, y, TEXT);
            y += 10;
        }
        return y + 4;
    }

    private TradeListS2CPayload.Entry selectedEntry() {
        return this.visibleEntries.get(Math.min(this.selectedTradeIndex, this.visibleEntries.size() - 1));
    }

    private int professionRows() {
        int panelHeight = Math.min(285, this.height - 20);
        return Math.max(1, (panelHeight - 68) / 18);
    }

    private int tradeRows() {
        int panelHeight = Math.min(285, this.height - 20);
        return Math.max(1, (panelHeight - 68) / 24);
    }

    private int countProfession(String profession) {
        int count = 0;
        for (TradeListS2CPayload.Entry entry : this.allEntries) {
            if (categoryKey(entry).equals(profession)) {
                count++;
            }
        }
        return count;
    }

    private String trim(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        return this.font.plainSubstrByWidth(value, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    private static boolean isBestEligible(TradeListS2CPayload.Entry entry) {
        if (!isEnchantedBook(entry)) {
            return true;
        }
        EnchantMatch enchant = findEnchantMatch(entry.resultName());
        if (enchant == null) {
            return true;
        }
        Integer maxLevel = MAX_ENCHANT_LEVELS.get(normalizeEnchantName(enchant.name()));
        return maxLevel == null || enchant.level() >= maxLevel;
    }

    private static EnchantMatch findEnchantMatch(String resultName) {
        int separator = resultName.indexOf(": ");
        if (separator < 0) {
            return null;
        }
        String firstEnchant = resultName.substring(separator + 2).split(",")[0].trim();
        int lastSpace = firstEnchant.lastIndexOf(' ');
        if (lastSpace < 0) {
            return firstEnchant.isBlank() ? null : new EnchantMatch(firstEnchant, 1);
        }
        int level = romanToInt(firstEnchant.substring(lastSpace + 1));
        if (level <= 0) {
            return null;
        }
        return new EnchantMatch(firstEnchant.substring(0, lastSpace).trim(), level);
    }

    private static String searchable(TradeListS2CPayload.Entry entry) {
        return (entry.resultName() + " " + resultName(entry) + " "
                + entry.costAName() + " " + costAName(entry) + " "
                + entry.costBName() + " " + costBName(entry) + " "
                + entry.profession() + " " + professionName(entry.profession()) + " " + posText(entry))
                .toLowerCase(Locale.ROOT);
    }

    private static String tradeKey(TradeListS2CPayload.Entry entry) {
        return entry.uuid() + "|" + entry.resultName() + "|" + entry.resultCount() + "|" + entry.baseCostA() + "|" + entry.costB();
    }

    private static String bestGroupKey(TradeListS2CPayload.Entry entry) {
        if (isEnchantedItem(entry) && !isEnchantedBook(entry)) {
            return entry.uuid() + "|" + entry.resultName() + "|" + entry.resultCount()
                    + "|" + entry.baseCostA() + "|" + entry.costB() + "|" + entry.distance();
        }
        if (isEnchantedBook(entry)) {
            EnchantMatch enchant = findEnchantMatch(entry.resultName());
            if (enchant != null) {
                return ENCHANTED_BOOK_KEY + "|" + normalizeEnchantName(enchant.name()) + "|" + entry.resultCount()
                        + "|" + stackGroupKey(entry.costAName(), entry.costATranslationKey())
                        + "|" + stackGroupKey(entry.costBName(), entry.costBTranslationKey());
            }
        }
        return stackGroupKey(entry.resultName(), entry.resultTranslationKey()) + "|" + entry.resultCount()
                + "|" + stackGroupKey(entry.costAName(), entry.costATranslationKey())
                + "|" + stackGroupKey(entry.costBName(), entry.costBTranslationKey());
    }

    private static int compareValue(TradeListS2CPayload.Entry first, TradeListS2CPayload.Entry second) {
        int price = Integer.compare(effectivePrice(first), effectivePrice(second));
        if (price != 0) {
            return price;
        }
        return Double.compare(first.distance() < 0 ? Double.MAX_VALUE : first.distance(), second.distance() < 0 ? Double.MAX_VALUE : second.distance());
    }

    private static int effectivePrice(TradeListS2CPayload.Entry entry) {
        return entry.currentCostA() + entry.costB();
    }

    private static String costText(TradeListS2CPayload.Entry entry) {
        return costText(entry.currentCostA(), costAName(entry), entry.costB(), costBName(entry));
    }

    private static String recordedCostText(TradeListS2CPayload.Entry entry) {
        return costText(entry.baseCostA(), costAName(entry), entry.costB(), costBName(entry));
    }

    private static String costText(int costA, String costAName, int costB, String costBName) {
        String text = costA + "x " + costAName;
        if (costB > 0) {
            text += " + " + costB + "x " + costBName;
        }
        return text;
    }

    private static String priceText(TradeListS2CPayload.Entry entry) {
        String current = costText(entry);
        String recorded = recordedCostText(entry);
        if (entry.live() && entry.currentCostA() != entry.baseCostA()) {
            return current + " (" + t("recorded_price") + " " + recorded + ")";
        }
        return current + (entry.live() ? " (" + t("current_price") + ")" : " (" + t("recorded_price") + ")");
    }

    private static String resultName(TradeListS2CPayload.Entry entry) {
        return DGuTweakClientConfig.itemName(entry.resultName(), entry.resultTranslationKey());
    }

    private static String costAName(TradeListS2CPayload.Entry entry) {
        return DGuTweakClientConfig.itemName(entry.costAName(), entry.costATranslationKey());
    }

    private static String costBName(TradeListS2CPayload.Entry entry) {
        return DGuTweakClientConfig.itemName(entry.costBName(), entry.costBTranslationKey());
    }

    private static String professionName(String profession) {
        if (profession.equals(ENCHANTED_ITEMS_CATEGORY)) {
            return t("category.enchanted_items");
        }
        return t("profession." + profession);
    }

    private static String categoryKey(TradeListS2CPayload.Entry entry) {
        return isEnchantedItem(entry) && !isEnchantedBook(entry)
                ? ENCHANTED_ITEMS_CATEGORY
                : entry.profession();
    }

    private static boolean isEnchantedItem(TradeListS2CPayload.Entry entry) {
        return findEnchantMatch(entry.resultName()) != null;
    }

    private static boolean isEnchantedBook(TradeListS2CPayload.Entry entry) {
        return ENCHANTED_BOOK_KEY.equals(entry.resultTranslationKey());
    }

    private static String stackGroupKey(String fallbackName, String translationKey) {
        return translationKey == null || translationKey.isBlank() ? fallbackName : translationKey;
    }

    private static String distanceText(TradeListS2CPayload.Entry entry) {
        return entry.distance() < 0 ? t("other_dimension") : String.format(Locale.ROOT, "%.0fm", entry.distance());
    }

    private static String posText(TradeListS2CPayload.Entry entry) {
        return entry.dimension().getPath() + " " + entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ();
    }

    private static boolean inside(MouseButtonEvent event, int left, int top, int right, int bottom) {
        return event.x() >= left && event.x() <= right && event.y() >= top && event.y() <= bottom;
    }

    private static void drawBorder(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + 1, BORDER);
        graphics.fill(left, top + height - 1, left + width, top + height, BORDER);
        graphics.fill(left, top, left + 1, top + height, BORDER);
        graphics.fill(left + width - 1, top, left + width, top + height, BORDER);
    }

    private static String t(String key) {
        return DGuTweakClientConfig.text(key);
    }

    private static void requestLiveRefresh() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            ClientPlayNetworking.send(new RequestTradeListC2SPayload());
        }
    }

    private static Map<String, Integer> loadMaxEnchantLevels() {
        Map<String, Integer> levels = defaultMaxEnchantLevels();
        List<Resource> resources = Minecraft.getInstance().getResourceManager().getResourceStack(TRADE_CSV);
        if (resources.isEmpty()) {
            return levels;
        }
        try (BufferedReader reader = resources.get(0).openAsReader()) {
            List<String> lines = reader.lines().toList();
            for (int index = 1; index < lines.size(); index++) {
                List<String> columns = parseCsvLine(lines.get(index));
                if (columns.size() < 3) {
                    continue;
                }
                int maxLevel = romanToInt(columns.get(2));
                if (maxLevel <= 0) {
                    continue;
                }
                for (String name : columns.get(0).split(",")) {
                    levels.putIfAbsent(normalizeEnchantName(name), maxLevel);
                }
                levels.putIfAbsent(normalizeEnchantName(columns.get(1)), maxLevel);
            }
        } catch (IOException exception) {
            DGuTweak.LOGGER.warn("Failed to load enchantment max levels", exception);
        }
        return levels;
    }

    private static Map<String, Integer> defaultMaxEnchantLevels() {
        Map<String, Integer> levels = new HashMap<>();
        addMaxLevel(levels, 4, "保護", "保护", "Protection");
        addMaxLevel(levels, 4, "火焰保護", "火焰保护", "Fire Protection");
        addMaxLevel(levels, 4, "輕盈", "轻盈", "摔落緩衝", "摔落缓冲", "Feather Falling");
        addMaxLevel(levels, 4, "爆炸保護", "爆炸保护", "Blast Protection");
        addMaxLevel(levels, 4, "投射物保護", "弹射物保护", "Projectile Protection");
        addMaxLevel(levels, 3, "水中呼吸", "Respiration");
        addMaxLevel(levels, 1, "親水性", "水下速掘", "Aqua Affinity");
        addMaxLevel(levels, 3, "尖刺", "荆棘", "Thorns");
        addMaxLevel(levels, 3, "深海漫遊", "深海探索者", "Depth Strider");
        addMaxLevel(levels, 2, "冰霜行者", "Frost Walker");
        addMaxLevel(levels, 1, "綁定詛咒", "绑定诅咒", "Curse of Binding");
        addMaxLevel(levels, 5, "鋒利", "锋利", "Sharpness");
        addMaxLevel(levels, 5, "不死剋星", "不死克星", "不死剋手", "不死克手", "Smite");
        addMaxLevel(levels, 5, "節肢剋星", "节肢杀手", "節肢剋手", "节肢克手", "Bane of Arthropods");
        addMaxLevel(levels, 2, "擊退", "击退", "Knockback");
        addMaxLevel(levels, 2, "燃燒", "火焰附加", "Fire Aspect");
        addMaxLevel(levels, 3, "掠奪", "抢夺", "Looting");
        addMaxLevel(levels, 3, "橫掃之刃", "横扫之刃", "Sweeping Edge");
        addMaxLevel(levels, 5, "強力", "力量", "Power");
        addMaxLevel(levels, 2, "衝擊", "冲击", "Punch");
        addMaxLevel(levels, 1, "火焰", "Flame");
        addMaxLevel(levels, 1, "無限", "无限", "Infinity");
        addMaxLevel(levels, 5, "效率", "Efficiency");
        addMaxLevel(levels, 1, "絲綢之觸", "精准采集", "Silk Touch");
        addMaxLevel(levels, 3, "幸運", "时运", "Fortune");
        addMaxLevel(levels, 3, "海洋的祝福", "海之眷顾", "Luck of the Sea");
        addMaxLevel(levels, 3, "魚餌", "饵钓", "Lure");
        addMaxLevel(levels, 3, "耐久", "Unbreaking");
        addMaxLevel(levels, 1, "修補", "经验修补", "Mending");
        addMaxLevel(levels, 1, "消失詛咒", "消失诅咒", "Curse of Vanishing");
        addMaxLevel(levels, 1, "喚雷", "引雷", "Channeling");
        addMaxLevel(levels, 5, "魚叉", "穿刺", "Impaling");
        addMaxLevel(levels, 3, "忠誠", "忠诚", "Loyalty");
        addMaxLevel(levels, 3, "波濤", "激流", "Riptide");
        addMaxLevel(levels, 3, "快速上弦", "快速裝填", "快速装填", "Quick Charge");
        addMaxLevel(levels, 4, "貫穿", "穿透", "Piercing");
        addMaxLevel(levels, 1, "分裂箭矢", "多重射擊", "多重射击", "Multishot");
        addMaxLevel(levels, 3, "靈魂疾走", "灵魂疾行", "Soul Speed");
        addMaxLevel(levels, 3, "迅捷潛行", "迅捷潜行", "Swift Sneak");
        addMaxLevel(levels, 5, "緻密", "致密", "密度", "Density");
        addMaxLevel(levels, 4, "破甲", "Breach");
        addMaxLevel(levels, 3, "風爆", "风爆", "Wind Burst");
        return levels;
    }

    private static void addMaxLevel(Map<String, Integer> levels, int maxLevel, String... names) {
        for (String name : names) {
            levels.put(normalizeEnchantName(name), maxLevel);
        }
    }

    private static String normalizeEnchantName(String name) {
        return name.trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("　", "");
    }

    private static List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        columns.add(current.toString());
        return columns;
    }

    private static int romanToInt(String roman) {
        String value = roman.trim().toUpperCase(Locale.ROOT);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
        return switch (value) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> -1;
        };
    }

    private record EnchantMatch(String name, int level) {
    }
}
