package net.dgu.dgutweak.client;

import net.dgu.dgutweak.DGuTweak;
import net.dgu.dgutweak.networking.GlowVillagerC2SPayload;
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

    private static final Map<String, Integer> MAX_ENCHANT_LEVELS = loadMaxEnchantLevels();

    private final List<TradeListS2CPayload.Entry> allEntries;
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

    public TradeDatabaseScreen(List<TradeListS2CPayload.Entry> entries) {
        super(Component.translatable("gui.dgutweak.trades.title"));
        this.allEntries = new ArrayList<>(entries);
        this.allEntries.sort(Comparator
                .comparing(TradeListS2CPayload.Entry::profession)
                .thenComparing(TradeListS2CPayload.Entry::resultName)
                .thenComparingInt(TradeDatabaseScreen::effectivePrice)
                .thenComparingDouble(entry -> entry.distance() < 0 ? Double.MAX_VALUE : entry.distance()));
        rebuildProfessions();
        rebuildVisibleEntries();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(650, this.width - 24);
        int left = (this.width - panelWidth) / 2;
        this.searchBox = new EditBox(this.font, left + 10, 34, panelWidth - 190, 18, Component.translatable("gui.dgutweak.trades.search"));
        this.addRenderableWidget(this.searchBox);
        this.addRenderableWidget(Button.builder(Component.literal(this.bestOnly ? "Best" : "All"), button -> {
                    this.bestOnly = !this.bestOnly;
                    button.setMessage(Component.literal(this.bestOnly ? "Best" : "All"));
                    rebuildVisibleEntries();
                })
                .pos(left + panelWidth - 174, 34)
                .size(48, 18)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.refresh"), button -> DGuTweakClient.requestTradeList())
                .pos(left + panelWidth - 122, 34)
                .size(56, 18)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.close"), button -> Minecraft.getInstance().setScreen(null))
                .pos(left + panelWidth - 62, 34)
                .size(52, 18)
                .build());
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
            String text = "Tracking " + this.trackedEntry.profession() + " @ " + posText(this.trackedEntry);
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
                .map(TradeListS2CPayload.Entry::profession)
                .distinct()
                .sorted()
                .toList();
        if (this.selectedProfession.isBlank() && !this.professions.isEmpty()) {
            this.selectedProfession = this.professions.get(0);
        }
    }

    private void rebuildVisibleEntries() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<TradeListS2CPayload.Entry> matches = this.allEntries.stream()
                .filter(entry -> entry.profession().equals(this.selectedProfession))
                .filter(entry -> query.isBlank() || searchable(entry).contains(query))
                .toList();

        if (this.bestOnly) {
            Map<String, TradeListS2CPayload.Entry> best = new LinkedHashMap<>();
            for (TradeListS2CPayload.Entry entry : matches) {
                if (!isBestEligible(entry)) {
                    continue;
                }
                String key = entry.resultName() + " x" + entry.resultCount();
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

    private void drawProfessionList(GuiGraphics graphics, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        graphics.drawString(this.font, "Profession", left, top - 12, ACCENT);
        if (this.professions.isEmpty()) {
            graphics.drawString(this.font, "No records", left, top, MUTED);
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
            graphics.drawString(this.font, trim(profession + " (" + countProfession(profession) + ")", right - left - 4), left, y + 4, selected ? 0xFFFFFFFF : TEXT);
        }
    }

    private void drawTradeList(GuiGraphics graphics, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.bestOnly ? "Best trades" : "All trades", left, top - 12, ACCENT);
        if (this.visibleEntries.isEmpty()) {
            graphics.drawString(this.font, "No trades for this filter", left, top, MUTED);
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
            graphics.drawString(this.font, trim(entry.resultCount() + "x " + entry.resultName(), right - left - 6), left, y, selected ? 0xFFFFFFFF : TEXT);
            graphics.drawString(this.font, trim(costText(entry) + " | " + distanceText(entry), right - left - 6), left, y + 10, entry.live() ? ACCENT : MUTED);
        }
    }

    private void drawDetails(GuiGraphics graphics, int left, int top, int right, int bottom) {
        if (this.visibleEntries.isEmpty()) {
            return;
        }
        TradeListS2CPayload.Entry entry = selectedEntry();
        int y = top;
        graphics.drawString(this.font, trim(entry.resultCount() + "x " + entry.resultName(), right - left), left, y, 0xFFFFFFFF);
        y += 14;
        y = drawLine(graphics, "Price", costText(entry) + (entry.live() ? " (live)" : " (recorded)"), left, right, y);
        y = drawLine(graphics, "Villager", entry.profession() + " L" + entry.level(), left, right, y);
        y = drawLine(graphics, "Position", posText(entry), left, right, y);
        y = drawLine(graphics, "Distance", distanceText(entry), left, right, y);
        y = drawLine(graphics, "Status", entry.locked() ? "Locked trade" : (entry.live() ? "Loaded" : "Not loaded"), left, right, y);

        this.actionButtonX = left;
        this.actionButtonY = Math.min(bottom - 18, y + 8);
        graphics.fill(this.actionButtonX, this.actionButtonY, this.actionButtonX + 56, this.actionButtonY + 16, 0xFF2D4F66);
        graphics.drawCenteredString(this.font, "Glow", this.actionButtonX + 28, this.actionButtonY + 4, 0xFFFFFFFF);
        graphics.fill(this.actionButtonX + 62, this.actionButtonY, this.actionButtonX + 118, this.actionButtonY + 16, 0xFF3F4F2D);
        graphics.drawCenteredString(this.font, "Track", this.actionButtonX + 90, this.actionButtonY + 4, 0xFFFFFFFF);
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
            if (entry.profession().equals(profession)) {
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
        EnchantMatch enchant = findEnchantMatch(entry.resultName());
        if (enchant == null) {
            return true;
        }
        Integer maxLevel = MAX_ENCHANT_LEVELS.get(enchant.name().toLowerCase(Locale.ROOT));
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
            return null;
        }
        int level = romanToInt(firstEnchant.substring(lastSpace + 1));
        if (level <= 0) {
            return null;
        }
        return new EnchantMatch(firstEnchant.substring(0, lastSpace).trim(), level);
    }

    private static String searchable(TradeListS2CPayload.Entry entry) {
        return (entry.resultName() + " " + entry.costAName() + " " + entry.costBName() + " " + entry.profession() + " " + posText(entry))
                .toLowerCase(Locale.ROOT);
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
        String text = entry.currentCostA() + "x " + entry.costAName();
        if (entry.costB() > 0) {
            text += " + " + entry.costB() + "x " + entry.costBName();
        }
        return text;
    }

    private static String distanceText(TradeListS2CPayload.Entry entry) {
        return entry.distance() < 0 ? "other dimension" : String.format(Locale.ROOT, "%.0fm", entry.distance());
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

    private static Map<String, Integer> loadMaxEnchantLevels() {
        Map<String, Integer> levels = new HashMap<>();
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
                    levels.put(name.trim().toLowerCase(Locale.ROOT), maxLevel);
                }
                levels.put(columns.get(1).trim().toLowerCase(Locale.ROOT), maxLevel);
            }
        } catch (IOException exception) {
            DGuTweak.LOGGER.warn("Failed to load enchantment max levels", exception);
        }
        return levels;
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
        return switch (roman.trim().toUpperCase(Locale.ROOT)) {
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
