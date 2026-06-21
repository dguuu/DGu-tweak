package net.dgu.dgutweak.client;

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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TradeDatabaseScreen extends Screen {
    private static final int PANEL = 0xE0202020;
    private static final int PANEL_DARK = 0xE8181818;
    private static final int BORDER = 0xFF555555;
    private static final int SELECTED = 0xFF315E7D;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int ACCENT = 0xFF7DD3FC;
    private static final String ENCHANTED_ITEMS_CATEGORY = "__enchanted_items";
    private static final String ENCHANTED_BOOK_KEY = "item.minecraft.enchanted_book";

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
    private boolean bestButtonVisible;
    private int refreshTicks;

    public TradeDatabaseScreen(List<TradeListS2CPayload.Entry> entries) {
        super(Component.translatable("gui.dgutweak.trades.title"));
        replaceEntries(entries);
    }

    public void replaceEntries(List<TradeListS2CPayload.Entry> entries) {
        String profession = selectedProfession;
        String selected = visibleEntries.isEmpty() ? "" : tradeKey(selectedEntry());
        allEntries.clear();
        allEntries.addAll(DGuTweakClient.applyRememberedMerchantPrices(entries));
        allEntries.sort(Comparator.comparing(TradeListS2CPayload.Entry::profession)
                .thenComparing(TradeDatabaseScreen::resultName)
                .thenComparingInt(TradeDatabaseScreen::effectivePrice)
                .thenComparingDouble(entry -> entry.distance() < 0 ? Double.MAX_VALUE : entry.distance()));
        rebuildProfessions();
        if (!profession.isBlank() && professions.contains(profession)) selectedProfession = profession;
        rebuildVisibleEntries();
        restoreSelectedTrade(selected);
    }

    public List<TradeListS2CPayload.Entry> entriesSnapshot() {
        return List.copyOf(allEntries);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(650, width - 24);
        int left = (width - panelWidth) / 2;
        searchBox = new EditBox(font, left + 10, 34, panelWidth - 190, 18,
                Component.translatable("gui.dgutweak.trades.search"));
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.translatable(bestOnly
                        ? "gui.dgutweak.trades.best" : "gui.dgutweak.trades.all"), button -> {
                    bestOnly = !bestOnly;
                    button.setMessage(Component.translatable(bestOnly
                            ? "gui.dgutweak.trades.best" : "gui.dgutweak.trades.all"));
                    rebuildVisibleEntries();
                }).pos(left + panelWidth - 174, 34).size(48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.refresh"), button -> requestLiveRefresh())
                .pos(left + panelWidth - 122, 34).size(56, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.close"), button -> onClose())
                .pos(left + panelWidth - 62, 34).size(52, 18).build());
        requestLiveRefresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            requestLiveRefresh();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xAA000000);
        int panelWidth = Math.min(650, width - 24);
        int panelHeight = Math.min(285, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int contentTop = top + 58;
        int contentBottom = top + panelHeight - 10;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        graphics.fill(left, top, left + panelWidth, top + 24, PANEL_DARK);
        drawBorder(graphics, left, top, panelWidth, panelHeight);
        graphics.drawCenteredString(font, title, width / 2, top + 8, TEXT);
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
        if (trackedEntry != null) {
            graphics.drawCenteredString(font, Component.translatable("gui.dgutweak.trades.tracking",
                    professionName(trackedEntry.profession()), posText(trackedEntry)), width / 2, height - 14, ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int panelWidth = Math.min(650, width - 24);
        int panelHeight = Math.min(285, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int contentTop = top + 58;
        int contentBottom = top + panelHeight - 10;
        int professionsLeft = left + 10;
        int professionsRight = professionsLeft + 120;
        int tradesLeft = professionsRight + 8;
        int tradesRight = tradesLeft + 275;
        if (inside(event, professionsLeft, contentTop, professionsRight, contentBottom)) {
            int index = professionScroll + ((int) event.y() - contentTop) / 18;
            if (index >= 0 && index < professions.size()) {
                selectedProfession = professions.get(index);
                tradeScroll = 0;
                selectedTradeIndex = 0;
                rebuildVisibleEntries();
                return true;
            }
        }
        if (inside(event, tradesLeft, contentTop, tradesRight, contentBottom)) {
            int index = tradeScroll + ((int) event.y() - contentTop) / 24;
            if (index >= 0 && index < visibleEntries.size()) {
                selectedTradeIndex = index;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!visibleEntries.isEmpty()) {
            TradeListS2CPayload.Entry entry = selectedEntry();
            if (inside(event, actionButtonX, actionButtonY, actionButtonX + 56, actionButtonY + 16)) {
                ClientPlayNetworking.send(new GlowVillagerC2SPayload(entry.uuid(), true));
                return true;
            }
            if (inside(event, actionButtonX + 62, actionButtonY, actionButtonX + 118, actionButtonY + 16)) {
                trackedEntry = entry;
                ClientPlayNetworking.send(new GlowVillagerC2SPayload(entry.uuid(), false));
                return true;
            }
            if (bestButtonVisible && inside(event, actionButtonX, actionButtonY + 20,
                    actionButtonX + 118, actionButtonY + 36)) {
                DGuTweakClientConfig.toggleBestTrade(manualBestKey(entry));
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelHeight = Math.min(285, height - 20);
        int left = (width - Math.min(650, width - 24)) / 2;
        if (mouseX <= left + 130) {
            professionScroll = clampScroll(professionScroll, professions.size() - professionRows(), scrollY);
        } else {
            tradeScroll = clampScroll(tradeScroll, visibleEntries.size() - tradeRows(), scrollY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox.keyPressed(event)) {
            rebuildVisibleEntries();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchBox.charTyped(event)) {
            rebuildVisibleEntries();
            return true;
        }
        return super.charTyped(event);
    }

    private void rebuildProfessions() {
        professions = allEntries.stream().map(TradeDatabaseScreen::categoryKey).distinct().sorted().toList();
        if ((selectedProfession.isBlank() || !professions.contains(selectedProfession)) && !professions.isEmpty()) {
            selectedProfession = professions.get(0);
        }
    }

    private void rebuildVisibleEntries() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<TradeListS2CPayload.Entry> matches = allEntries.stream()
                .filter(entry -> categoryKey(entry).equals(selectedProfession))
                .filter(entry -> query.isBlank() || searchable(entry).contains(query))
                .toList();
        if (bestOnly && selectedProfession.equals(ENCHANTED_ITEMS_CATEGORY)) {
            visibleEntries = matches.stream().filter(entry -> DGuTweakClientConfig.isBestTrade(manualBestKey(entry))).toList();
        } else if (bestOnly) {
            Map<String, TradeListS2CPayload.Entry> best = new LinkedHashMap<>();
            for (TradeListS2CPayload.Entry entry : matches) {
                if (!isBestEligible(entry)) continue;
                String key = bestGroupKey(entry);
                TradeListS2CPayload.Entry current = best.get(key);
                if (current == null || compareValue(entry, current) < 0) best.put(key, entry);
            }
            visibleEntries = new ArrayList<>(best.values());
        } else {
            visibleEntries = new ArrayList<>(matches);
        }
        selectedTradeIndex = Math.min(selectedTradeIndex, Math.max(0, visibleEntries.size() - 1));
        tradeScroll = Math.min(tradeScroll, Math.max(0, visibleEntries.size() - tradeRows()));
    }

    private void restoreSelectedTrade(String key) {
        for (int index = 0; index < visibleEntries.size(); index++) {
            if (tradeKey(visibleEntries.get(index)).equals(key)) {
                selectedTradeIndex = index;
                return;
            }
        }
    }

    private void drawProfessionList(GuiGraphics graphics, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.dgutweak.trades.profession"), left, top - 12, ACCENT);
        if (professions.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.dgutweak.trades.no_records"), left, top, MUTED);
            return;
        }
        int rows = Math.min(professionRows(), professions.size() - professionScroll);
        for (int row = 0; row < rows; row++) {
            String profession = professions.get(professionScroll + row);
            int y = top + row * 18;
            boolean selected = profession.equals(selectedProfession);
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + 17;
            if (selected || hovered) graphics.fill(left - 2, y - 1, right, y + 17, selected ? SELECTED : 0x55444444);
            String text = professionName(profession) + " (" + countProfession(profession) + ")";
            graphics.drawString(font, trim(text, right - left - 4), left, y + 4, selected ? 0xFFFFFFFF : TEXT);
        }
    }

    private void drawTradeList(GuiGraphics graphics, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable(bestOnly
                ? "gui.dgutweak.trades.best_trades" : "gui.dgutweak.trades.all_trades"), left, top - 12, ACCENT);
        if (visibleEntries.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.dgutweak.trades.no_trades"), left, top, MUTED);
            return;
        }
        int rows = Math.min(tradeRows(), visibleEntries.size() - tradeScroll);
        for (int row = 0; row < rows; row++) {
            int index = tradeScroll + row;
            TradeListS2CPayload.Entry entry = visibleEntries.get(index);
            int y = top + row * 24;
            boolean selected = index == selectedTradeIndex;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + 23;
            if (selected || hovered) graphics.fill(left - 2, y - 1, right, y + 22, selected ? SELECTED : 0x55444444);
            String marker = selectedProfession.equals(ENCHANTED_ITEMS_CATEGORY)
                    && DGuTweakClientConfig.isBestTrade(manualBestKey(entry)) ? "★ " : "";
            graphics.drawString(font, trim(marker + entry.resultCount() + "x " + resultName(entry), right - left - 6),
                    left, y, selected ? 0xFFFFFFFF : TEXT);
            graphics.drawString(font, trim(priceText(entry) + " | " + distanceText(entry), right - left - 6),
                    left, y + 10, entry.live() ? ACCENT : MUTED);
        }
    }

    private void drawDetails(GuiGraphics graphics, int left, int top, int right, int bottom) {
        if (visibleEntries.isEmpty()) return;
        TradeListS2CPayload.Entry entry = selectedEntry();
        int y = top;
        graphics.drawString(font, trim(entry.resultCount() + "x " + resultName(entry), right - left), left, y, 0xFFFFFFFF);
        y = drawLine(graphics, "price", priceText(entry), left, right, y + 14);
        y = drawLine(graphics, "villager", professionName(entry.profession()) + " L" + entry.level(), left, right, y);
        y = drawLine(graphics, "position", posText(entry), left, right, y);
        y = drawLine(graphics, "distance", distanceText(entry), left, right, y);
        y = drawLine(graphics, "status", Component.translatable(entry.locked()
                ? "gui.dgutweak.trades.locked_trade"
                : entry.live() ? "gui.dgutweak.trades.loaded" : "gui.dgutweak.trades.not_loaded").getString(), left, right, y);
        bestButtonVisible = selectedProfession.equals(ENCHANTED_ITEMS_CATEGORY) && !bestOnly;
        actionButtonX = left;
        actionButtonY = Math.min(bottom - (bestButtonVisible ? 38 : 18), y + 8);
        drawAction(graphics, actionButtonX, actionButtonY, 56, "gui.dgutweak.trades.glow", 0xFF2D4F66);
        drawAction(graphics, actionButtonX + 62, actionButtonY, 56, "gui.dgutweak.trades.track", 0xFF3F4F2D);
        if (bestButtonVisible) {
            boolean selected = DGuTweakClientConfig.isBestTrade(manualBestKey(entry));
            drawAction(graphics, actionButtonX, actionButtonY + 20, 118,
                    selected ? "gui.dgutweak.trades.remove_best" : "gui.dgutweak.trades.add_best",
                    selected ? 0xFF6A4F20 : 0xFF374151);
        }
    }

    private int drawLine(GuiGraphics graphics, String key, String value, int left, int right, int y) {
        graphics.drawString(font, Component.translatable("gui.dgutweak.trades." + key), left, y, ACCENT);
        y += 10;
        for (FormattedCharSequence line : font.split(Component.literal(value), right - left)) {
            graphics.drawString(font, line, left, y, TEXT);
            y += 10;
        }
        return y + 4;
    }

    private void drawAction(GuiGraphics graphics, int x, int y, int width, String key, int color) {
        graphics.fill(x, y, x + width, y + 16, color);
        graphics.drawCenteredString(font, Component.translatable(key), x + width / 2, y + 4, 0xFFFFFFFF);
    }

    private TradeListS2CPayload.Entry selectedEntry() {
        return visibleEntries.get(Math.min(selectedTradeIndex, visibleEntries.size() - 1));
    }

    private int countProfession(String profession) {
        return (int) allEntries.stream().filter(entry -> categoryKey(entry).equals(profession)).count();
    }

    private int professionRows() {
        return Math.max(1, (Math.min(285, height - 20) - 68) / 18);
    }

    private int tradeRows() {
        return Math.max(1, (Math.min(285, height - 20) - 68) / 24);
    }

    private String trim(String value, int maxWidth) {
        return font.width(value) <= maxWidth ? value
                : font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static boolean isBestEligible(TradeListS2CPayload.Entry entry) {
        return !isEnchantedBook(entry) || entry.resultEnchantments().isEmpty()
                || entry.resultEnchantments().stream().allMatch(data -> data.level() >= maxEnchantLevel(data.id()));
    }

    private static String searchable(TradeListS2CPayload.Entry entry) {
        return (resultName(entry) + " " + costAName(entry) + " " + costBName(entry) + " "
                + professionName(entry.profession()) + " " + posText(entry)).toLowerCase(Locale.ROOT);
    }

    private static String tradeKey(TradeListS2CPayload.Entry entry) {
        return entry.uuid() + "|" + entry.resultTranslationKey() + "|" + enchantmentKey(entry)
                + "|" + entry.resultCount() + "|" + entry.baseCostA() + "|" + entry.costB();
    }

    private static String manualBestKey(TradeListS2CPayload.Entry entry) {
        return entry.uuid() + "|" + entry.resultTranslationKey() + "|" + entry.resultCount() + "|" + enchantmentKey(entry);
    }

    private static String bestGroupKey(TradeListS2CPayload.Entry entry) {
        if (isEnchantedBook(entry)) return ENCHANTED_BOOK_KEY + "|" + enchantmentKey(entry);
        return entry.resultTranslationKey() + "|" + entry.resultCount() + "|"
                + entry.costATranslationKey() + "|" + entry.costBTranslationKey();
    }

    private static int compareValue(TradeListS2CPayload.Entry first, TradeListS2CPayload.Entry second) {
        int price = Integer.compare(effectivePrice(first), effectivePrice(second));
        return price != 0 ? price : Double.compare(distanceValue(first), distanceValue(second));
    }

    private static double distanceValue(TradeListS2CPayload.Entry entry) {
        return entry.distance() < 0 ? Double.MAX_VALUE : entry.distance();
    }

    private static int effectivePrice(TradeListS2CPayload.Entry entry) {
        return entry.currentCostA() + entry.costB();
    }

    private static String priceText(TradeListS2CPayload.Entry entry) {
        String current = costText(entry.currentCostA(), costAName(entry), entry.costB(), costBName(entry));
        String recorded = costText(entry.baseCostA(), costAName(entry), entry.costB(), costBName(entry));
        if (entry.live() && entry.currentCostA() != entry.baseCostA()) {
            return Component.translatable("gui.dgutweak.trades.price_live_recorded", current, recorded).getString();
        }
        return Component.translatable(entry.live()
                ? "gui.dgutweak.trades.price_current" : "gui.dgutweak.trades.price_recorded", current).getString();
    }

    private static String costText(int costA, String costAName, int costB, String costBName) {
        String text = costA + "x " + costAName;
        return costB > 0 ? text + " + " + costB + "x " + costBName : text;
    }

    private static String resultName(TradeListS2CPayload.Entry entry) {
        String base = localizedItemName(entry.resultName(), entry.resultTranslationKey());
        List<String> enchantments = entry.resultEnchantments().stream()
                .map(TradeDatabaseScreen::localizedEnchantmentName).toList();
        return enchantments.isEmpty() ? base : base + ": " + String.join(", ", enchantments);
    }

    private static String costAName(TradeListS2CPayload.Entry entry) {
        return localizedItemName(entry.costAName(), entry.costATranslationKey());
    }

    private static String costBName(TradeListS2CPayload.Entry entry) {
        return localizedItemName(entry.costBName(), entry.costBTranslationKey());
    }

    private static String localizedItemName(String fallback, String key) {
        return key == null || key.isBlank() ? fallback : Component.translatable(key).getString();
    }

    private static String localizedEnchantmentName(TradeListS2CPayload.EnchantmentData data) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return data.id() + " " + data.level();
        return client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .filter(holder -> holder.key().identifier().equals(data.id())).findFirst()
                .map(holder -> Enchantment.getFullname(holder, data.level()).getString())
                .orElse(data.id() + " " + data.level());
    }

    private static int maxEnchantLevel(Identifier id) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return 0;
        return client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .filter(holder -> holder.key().identifier().equals(id))
                .mapToInt(holder -> holder.value().getMaxLevel()).findFirst().orElse(0);
    }

    private static String enchantmentKey(TradeListS2CPayload.Entry entry) {
        return entry.resultEnchantments().stream()
                .sorted(Comparator.comparing(data -> data.id().toString()))
                .map(data -> data.id() + "@" + data.level())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String professionName(String profession) {
        if (profession.equals(ENCHANTED_ITEMS_CATEGORY)) {
            return Component.translatable("gui.dgutweak.trades.category.enchanted_items").getString();
        }
        return Component.translatable(profession.equals("wandering_trader")
                ? "entity.minecraft.wandering_trader" : "entity.minecraft.villager." + profession).getString();
    }

    private static String categoryKey(TradeListS2CPayload.Entry entry) {
        return isEnchantedItem(entry) && !isEnchantedBook(entry) ? ENCHANTED_ITEMS_CATEGORY : entry.profession();
    }

    private static boolean isEnchantedItem(TradeListS2CPayload.Entry entry) {
        return !entry.resultEnchantments().isEmpty();
    }

    private static boolean isEnchantedBook(TradeListS2CPayload.Entry entry) {
        return ENCHANTED_BOOK_KEY.equals(entry.resultTranslationKey());
    }

    private static String distanceText(TradeListS2CPayload.Entry entry) {
        return entry.distance() < 0 ? Component.translatable("gui.dgutweak.trades.other_dimension").getString()
                : String.format(Locale.ROOT, "%.0fm", entry.distance());
    }

    private static String posText(TradeListS2CPayload.Entry entry) {
        return entry.dimension().getPath() + " " + entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ();
    }

    private static int clampScroll(int current, int max, double amount) {
        return Math.max(0, Math.min(Math.max(0, max), current - (int) Math.signum(amount)));
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

    private static void requestLiveRefresh() {
        if (Minecraft.getInstance().player != null) ClientPlayNetworking.send(new RequestTradeListC2SPayload());
    }
}
