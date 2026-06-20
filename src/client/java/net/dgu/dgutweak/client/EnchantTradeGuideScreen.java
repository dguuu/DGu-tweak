package net.dgu.dgutweak.client;

import net.dgu.dgutweak.DGuTweak;
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
import java.util.List;
import java.util.Locale;

public class EnchantTradeGuideScreen extends Screen {
    private static final Identifier TRADE_CSV = DGuTweak.id("trade.csv");
    private static final int PANEL = 0xD8202020;
    private static final int PANEL_DARK = 0xE0181818;
    private static final int BORDER = 0xFF555555;
    private static final int SELECTED = 0xFF315E7D;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int ACCENT = 0xFF7DD3FC;

    private final Screen parent;
    private List<TradeEntry> entries = List.of();
    private List<TradeEntry> filteredEntries = List.of();
    private EditBox searchBox;
    private int selectedIndex;
    private int scrollOffset;

    public EnchantTradeGuideScreen(Screen parent) {
        super(Component.translatable("gui.dgutweak.guide.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.entries = loadEntries();
        this.filteredEntries = this.entries;
        this.selectedIndex = 0;
        this.scrollOffset = 0;

        int panelWidth = Math.min(360, this.width - 32);
        int left = (this.width - panelWidth) / 2;
        this.searchBox = new EditBox(this.font, left + 10, 34, panelWidth - 76, 18, Component.translatable("gui.dgutweak.guide.search"));
        this.searchBox.setValue("");
        this.addRenderableWidget(this.searchBox);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.close"), button -> Minecraft.getInstance().setScreen(this.parent))
                .pos(left + panelWidth - 58, 34)
                .size(48, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);
        int panelWidth = Math.min(360, this.width - 32);
        int panelHeight = Math.min(220, this.height - 24);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int listWidth = 132;

        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        graphics.fill(left, top, left + panelWidth, top + 24, PANEL_DARK);
        drawBorder(graphics, left, top, panelWidth, panelHeight);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 8, TEXT);

        super.render(graphics, mouseX, mouseY, partialTick);

        int contentTop = top + 58;
        int contentBottom = top + panelHeight - 10;
        graphics.fill(left + 8, contentTop - 4, left + listWidth, contentBottom, 0x80303030);
        drawEntryList(graphics, left + 12, contentTop, left + listWidth - 4, contentBottom, mouseX, mouseY);
        drawDetails(graphics, left + listWidth + 10, contentTop - 2, left + panelWidth - 12, contentBottom);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        int panelWidth = Math.min(360, this.width - 32);
        int panelHeight = Math.min(220, this.height - 24);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int listX = left + 12;
        int listY = top + 58;
        int listRight = left + 128;
        int listBottom = top + panelHeight - 10;

        if (event.x() >= listX && event.x() <= listRight && event.y() >= listY && event.y() <= listBottom) {
            int clicked = this.scrollOffset + ((int) event.y() - listY) / 18;
            if (clicked >= 0 && clicked < this.filteredEntries.size()) {
                this.selectedIndex = clicked;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visibleRows = visibleRows();
        int maxScroll = Math.max(0, this.filteredEntries.size() - visibleRows);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.searchBox.keyPressed(event)) {
            refreshFilter();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchBox.charTyped(event)) {
            refreshFilter();
            return true;
        }
        return super.charTyped(event);
    }

    private void refreshFilter() {
        String query = this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            this.filteredEntries = this.entries;
        } else {
            this.filteredEntries = this.entries.stream()
                    .filter(entry -> entry.matches(query))
                    .toList();
        }
        this.selectedIndex = Math.min(this.selectedIndex, Math.max(0, this.filteredEntries.size() - 1));
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, this.filteredEntries.size() - visibleRows()));
    }

    private void drawEntryList(GuiGraphics graphics, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        if (this.filteredEntries.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.dgutweak.guide.no_results"), left, top, MUTED);
            return;
        }

        int rows = Math.min(visibleRows(), this.filteredEntries.size() - this.scrollOffset);
        for (int row = 0; row < rows; row++) {
            int index = this.scrollOffset + row;
            TradeEntry entry = this.filteredEntries.get(index);
            int y = top + row * 18;
            boolean selected = index == this.selectedIndex;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + 17;
            if (selected || hovered) {
                graphics.fill(left - 2, y - 1, right, y + 17, selected ? SELECTED : 0x55444444);
            }
            graphics.drawString(this.font, trim(entry.zhName(), right - left - 24), left, y, selected ? 0xFFFFFFFF : TEXT);
            graphics.drawString(this.font, entry.maxLevel(), right - this.font.width(entry.maxLevel()) - 3, y, ACCENT);
            graphics.drawString(this.font, trim(entry.enName(), right - left - 4), left, y + 9, MUTED);
        }
    }

    private void drawDetails(GuiGraphics graphics, int left, int top, int right, int bottom) {
        if (this.filteredEntries.isEmpty()) {
            return;
        }

        TradeEntry entry = this.filteredEntries.get(Math.min(this.selectedIndex, this.filteredEntries.size() - 1));
        int y = top;
        graphics.drawString(this.font, entry.zhName() + " " + entry.maxLevel(), left, y, 0xFFFFFFFF);
        y += 12;
        graphics.drawString(this.font, entry.enName(), left, y, ACCENT);
        y += 18;

        y = drawSection(graphics, "適用", entry.equipment(), left, right, y);
        y = drawSection(graphics, "衝突", entry.conflicts(), left, right, y);
        y = drawSection(graphics, "備註", entry.note(), left, right, y);
        y = drawSection(graphics, "獲取", entry.source(), left, right, y);
        drawSection(graphics, "錢", entry.price(), left, right, y);
    }

    private int drawSection(GuiGraphics graphics, String label, String value, int left, int right, int y) {
        if (value.isBlank()) {
            return y;
        }

        graphics.drawString(this.font, label, left, y, ACCENT);
        y += 10;
        for (FormattedCharSequence line : this.font.split(Component.literal(value), right - left)) {
            graphics.drawString(this.font, line, left, y, TEXT);
            y += 10;
        }
        return y + 4;
    }

    private int visibleRows() {
        int panelHeight = Math.min(220, this.height - 24);
        return Math.max(1, (panelHeight - 68) / 18);
    }

    private String trim(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        return this.font.plainSubstrByWidth(value, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    private static void drawBorder(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + 1, BORDER);
        graphics.fill(left, top + height - 1, left + width, top + height, BORDER);
        graphics.fill(left, top, left + 1, top + height, BORDER);
        graphics.fill(left + width - 1, top, left + width, top + height, BORDER);
    }

    private static List<TradeEntry> loadEntries() {
        List<Resource> resources = Minecraft.getInstance().getResourceManager().getResourceStack(TRADE_CSV);
        if (resources.isEmpty()) {
            return List.of();
        }

        try (BufferedReader reader = resources.get(0).openAsReader()) {
            List<String> lines = reader.lines().toList();
            List<TradeEntry> loaded = new ArrayList<>();
            for (int index = 1; index < lines.size(); index++) {
                List<String> columns = parseCsvLine(lines.get(index));
                if (columns.size() < 8) {
                    continue;
                }
                loaded.add(new TradeEntry(
                        columns.get(0),
                        columns.get(1),
                        columns.get(2),
                        columns.get(3),
                        columns.get(4),
                        columns.get(5),
                        columns.get(6),
                        columns.get(7)
                ));
            }
            return loaded;
        } catch (IOException exception) {
            DGuTweak.LOGGER.warn("Failed to load enchant trade guide CSV", exception);
            return List.of();
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
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

    private record TradeEntry(
            String zhName,
            String enName,
            String maxLevel,
            String equipment,
            String conflicts,
            String note,
            String source,
            String price
    ) {
        private boolean matches(String query) {
            return zhName.toLowerCase(Locale.ROOT).contains(query)
                    || enName.toLowerCase(Locale.ROOT).contains(query)
                    || maxLevel.toLowerCase(Locale.ROOT).contains(query)
                    || equipment.toLowerCase(Locale.ROOT).contains(query)
                    || conflicts.toLowerCase(Locale.ROOT).contains(query)
                    || note.toLowerCase(Locale.ROOT).contains(query)
                    || source.toLowerCase(Locale.ROOT).contains(query)
                    || price.toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
