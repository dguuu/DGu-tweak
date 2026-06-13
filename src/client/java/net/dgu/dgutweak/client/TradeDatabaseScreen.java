package net.dgu.dgutweak.client;

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
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TradeDatabaseScreen extends Screen {
    private static final int PANEL = 0xE0202020;
    private static final int PANEL_DARK = 0xE8181818;
    private static final int BORDER = 0xFF555555;
    private static final int SELECTED = 0xFF315E7D;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int ACCENT = 0xFF7DD3FC;

    private final List<TradeListS2CPayload.Entry> allEntries;
    private List<TradeListS2CPayload.Entry> visibleEntries = List.of();
    private EditBox searchBox;
    private boolean bestOnly = true;
    private int selectedIndex;
    private int scrollOffset;
    private TradeListS2CPayload.Entry trackedEntry;

    public TradeDatabaseScreen(List<TradeListS2CPayload.Entry> entries) {
        super(Component.translatable("gui.dgutweak.trades.title"));
        this.allEntries = new ArrayList<>(entries);
        this.allEntries.sort(Comparator
                .comparing(TradeListS2CPayload.Entry::resultName)
                .thenComparingInt(TradeDatabaseScreen::effectivePrice)
                .thenComparingDouble(entry -> entry.distance() < 0 ? Double.MAX_VALUE : entry.distance()));
        rebuildVisibleEntries();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(520, this.width - 24);
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
        int panelWidth = Math.min(520, this.width - 24);
        int panelHeight = Math.min(260, this.height - 20);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int listWidth = 270;

        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        graphics.fill(left, top, left + panelWidth, top + 24, PANEL_DARK);
        drawBorder(graphics, left, top, panelWidth, panelHeight);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 8, TEXT);
        super.render(graphics, mouseX, mouseY, partialTick);

        int contentTop = top + 58;
        int contentBottom = top + panelHeight - 10;
        drawEntryList(graphics, left + 10, contentTop, left + listWidth, contentBottom, mouseX, mouseY);
        drawDetails(graphics, left + listWidth + 12, contentTop, left + panelWidth - 12, contentBottom);

        if (this.trackedEntry != null) {
            String text = "Tracking " + this.trackedEntry.resultName() + " @ " + posText(this.trackedEntry);
            graphics.drawCenteredString(this.font, text, this.width / 2, this.height - 14, ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int panelWidth = Math.min(520, this.width - 24);
        int panelHeight = Math.min(260, this.height - 20);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int listX = left + 10;
        int listY = top + 58;
        int listRight = left + 270;
        int listBottom = top + panelHeight - 10;
        if (event.x() >= listX && event.x() <= listRight && event.y() >= listY && event.y() <= listBottom) {
            int clicked = this.scrollOffset + ((int) event.y() - listY) / 24;
            if (clicked >= 0 && clicked < this.visibleEntries.size()) {
                this.selectedIndex = clicked;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, this.visibleEntries.size() - visibleRows());
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (int) Math.signum(scrollY)));
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

    private void rebuildVisibleEntries() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<TradeListS2CPayload.Entry> matches = this.allEntries.stream()
                .filter(entry -> query.isBlank() || searchable(entry).contains(query))
                .toList();
        if (this.bestOnly) {
            Map<String, TradeListS2CPayload.Entry> best = new LinkedHashMap<>();
            for (TradeListS2CPayload.Entry entry : matches) {
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
        this.selectedIndex = Math.min(this.selectedIndex, Math.max(0, this.visibleEntries.size() - 1));
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, this.visibleEntries.size() - visibleRows()));
    }

    private void drawEntryList(GuiGraphics graphics, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        if (this.allEntries.isEmpty()) {
            graphics.drawString(this.font, Component.literal("No recorded trades. Record villagers first."), left, top, MUTED);
            return;
        }
        if (this.visibleEntries.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.dgutweak.guide.no_results"), left, top, MUTED);
            return;
        }

        int rows = Math.min(visibleRows(), this.visibleEntries.size() - this.scrollOffset);
        for (int row = 0; row < rows; row++) {
            int index = this.scrollOffset + row;
            TradeListS2CPayload.Entry entry = this.visibleEntries.get(index);
            int y = top + row * 24;
            boolean selected = index == this.selectedIndex;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + 23;
            if (selected || hovered) {
                graphics.fill(left - 2, y - 1, right, y + 22, selected ? SELECTED : 0x55444444);
            }
            graphics.drawString(this.font, trim(entry.resultCount() + "x " + entry.resultName(), right - left - 6), left, y, selected ? 0xFFFFFFFF : TEXT);
            graphics.drawString(this.font, trim(costText(entry) + " | " + entry.profession() + " L" + entry.level(), right - left - 6), left, y + 10, entry.live() ? ACCENT : MUTED);
        }
    }

    private void drawDetails(GuiGraphics graphics, int left, int top, int right, int bottom) {
        if (this.visibleEntries.isEmpty()) {
            return;
        }
        TradeListS2CPayload.Entry entry = this.visibleEntries.get(Math.min(this.selectedIndex, this.visibleEntries.size() - 1));
        int y = top;
        graphics.drawString(this.font, entry.resultCount() + "x " + entry.resultName(), left, y, 0xFFFFFFFF);
        y += 14;
        y = drawLine(graphics, "價格", costText(entry) + (entry.live() ? " (目前)" : " (記錄)"), left, right, y);
        y = drawLine(graphics, "村民", entry.profession() + " L" + entry.level(), left, right, y);
        y = drawLine(graphics, "位置", posText(entry), left, right, y);
        y = drawLine(graphics, "距離", entry.distance() < 0 ? "其他維度" : String.format(Locale.ROOT, "%.0fm", entry.distance()), left, right, y);
        y = drawLine(graphics, "狀態", entry.locked() ? "Locked trade" : (entry.live() ? "已載入，可發光" : "未載入，請靠近座標"), left, right, y);

        int buttonY = Math.min(bottom - 18, y + 8);
        graphics.fill(left, buttonY, left + 56, buttonY + 16, 0xFF2D4F66);
        graphics.drawCenteredString(this.font, "Glow", left + 28, buttonY + 4, 0xFFFFFFFF);
        graphics.fill(left + 62, buttonY, left + 118, buttonY + 16, 0xFF3F4F2D);
        graphics.drawCenteredString(this.font, "Track", left + 90, buttonY + 4, 0xFFFFFFFF);
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

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int panelWidth = Math.min(520, this.width - 24);
        int panelHeight = Math.min(260, this.height - 20);
        int left = (this.width - panelWidth) / 2 + 282;
        int top = (this.height - panelHeight) / 2 + 58;
        int buttonY = Math.min((this.height - panelHeight) / 2 + panelHeight - 28, top + 122);
        if (!this.visibleEntries.isEmpty()) {
            TradeListS2CPayload.Entry entry = this.visibleEntries.get(Math.min(this.selectedIndex, this.visibleEntries.size() - 1));
            if (event.x() >= left && event.x() <= left + 56 && event.y() >= buttonY && event.y() <= buttonY + 16) {
                ClientPlayNetworking.send(new GlowVillagerC2SPayload(entry.uuid()));
                return true;
            }
            if (event.x() >= left + 62 && event.x() <= left + 118 && event.y() >= buttonY && event.y() <= buttonY + 16) {
                this.trackedEntry = entry;
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    private int visibleRows() {
        int panelHeight = Math.min(260, this.height - 20);
        return Math.max(1, (panelHeight - 68) / 24);
    }

    private String trim(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        return this.font.plainSubstrByWidth(value, Math.max(0, maxWidth - this.font.width("..."))) + "...";
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

    private static String posText(TradeListS2CPayload.Entry entry) {
        return entry.dimension().getPath() + " " + entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ();
    }

    private static void drawBorder(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + 1, BORDER);
        graphics.fill(left, top + height - 1, left + width, top + height, BORDER);
        graphics.fill(left, top, left + 1, top + height, BORDER);
        graphics.fill(left + width - 1, top, left + width, top + height, BORDER);
    }
}
