package net.dgu.dgutweak.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ItemEnchantmentsScreen extends Screen {
    private static final int MAX_ENCHANTMENTS = 5;
    private static final int VISIBLE_ENCHANTMENTS = 3;
    private final Screen parent;
    private final List<AutoVillagerFilter.EnchantmentRequirement> initial;
    private final Consumer<List<AutoVillagerFilter.EnchantmentRequirement>> onSave;
    private final List<Row> rows = new ArrayList<>();
    private Dropdown openDropdown;
    private Component error;
    private int scrollOffset;
    private int rowsTop;

    public ItemEnchantmentsScreen(Screen parent, List<AutoVillagerFilter.EnchantmentRequirement> initial,
                                  Consumer<List<AutoVillagerFilter.EnchantmentRequirement>> onSave) {
        super(Component.translatable("gui.dgutweak.auto_filter.enchantments.title"));
        this.parent = parent;
        this.initial = List.copyOf(initial);
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        rows.clear();
        int panelWidth = Math.min(320, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(8, (height - 145) / 2);
        rowsTop = top + 35;
        List<Option> options = enchantmentOptions();
        for (int index = 0; index < MAX_ENCHANTMENTS; index++) {
            AutoVillagerFilter.EnchantmentRequirement saved = index < initial.size() ? initial.get(index) : null;
            Option selected = options.stream().filter(option -> Objects.equals(option.id(), saved == null ? null : saved.enchantmentId()))
                    .findFirst().orElse(options.get(0));
            int y = rowsTop + index * 24;
            Dropdown dropdown = new Dropdown(left + 10, y, panelWidth - 62, 20, options, selected);
            EditBox level = new EditBox(font, left + panelWidth - 46, y, 36, 20, Component.literal("1"));
            level.setMaxLength(2);
            level.setValue(saved == null ? "1" : Integer.toString(saved.level()));
            addRenderableWidget(dropdown.button);
            addRenderableWidget(level);
            rows.add(new Row(dropdown, level));
        }
        updateVisibleRows();
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.auto_filter.save"), button -> save())
                .pos(width / 2 - 82, top + 110).size(78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.auto_filter.cancel"), button -> onClose())
                .pos(width / 2 + 4, top + 110).size(78, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC101010);
        int panelWidth = Math.min(320, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(8, (height - 145) / 2);
        graphics.fill(left, top, left + panelWidth, top + 140, 0xF0202020);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                (scrollOffset + 1) + "-" + Math.min(scrollOffset + VISIBLE_ENCHANTMENTS, MAX_ENCHANTMENTS) + " / " + MAX_ENCHANTMENTS,
                width / 2, top + 100, 0xFF888888);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (openDropdown != null) openDropdown.renderList(graphics, mouseX, mouseY);
        if (error != null) graphics.drawCenteredString(font, error, width / 2, top + 133, 0xFFFF5555);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (openDropdown != null) {
            if (openDropdown.click(event.x(), event.y())) return true;
            openDropdown = null;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (openDropdown != null) return openDropdown.scroll(scrollY);
        int nextOffset = Math.max(0, Math.min(MAX_ENCHANTMENTS - VISIBLE_ENCHANTMENTS,
                scrollOffset - (int) Math.signum(scrollY)));
        if (nextOffset != scrollOffset) {
            scrollOffset = nextOffset;
            updateVisibleRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateVisibleRows() {
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            boolean visible = index >= scrollOffset && index < scrollOffset + VISIBLE_ENCHANTMENTS;
            int y = rowsTop + (index - scrollOffset) * 24;
            row.dropdown.button.visible = visible;
            row.dropdown.button.setY(y);
            row.level.visible = visible;
            row.level.setY(y);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void save() {
        List<AutoVillagerFilter.EnchantmentRequirement> requirements = new ArrayList<>();
        try {
            for (Row row : rows) {
                if (row.dropdown.value().id() == null) continue;
                int level = Integer.parseInt(row.level.getValue().trim());
                if (level <= 0) throw new NumberFormatException();
                requirements.add(new AutoVillagerFilter.EnchantmentRequirement(row.dropdown.value().id(), level));
            }
        } catch (NumberFormatException exception) {
            error = Component.translatable("gui.dgutweak.auto_filter.invalid");
            return;
        }
        onSave.accept(requirements);
        onClose();
    }

    private static List<Option> enchantmentOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(null, Component.translatable("gui.dgutweak.auto_filter.none")));
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                    .filter(holder -> holder.is(EnchantmentTags.TRADEABLE))
                    .sorted(Comparator.comparing(holder -> holder.value().description().getString()))
                    .forEach(holder -> options.add(new Option(holder.key().identifier(), holder.value().description())));
        }
        return options;
    }

    private record Row(Dropdown dropdown, EditBox level) { }
    private record Option(Identifier id, Component label) { }

    private final class Dropdown {
        private static final int MAX_ROWS = 6;
        private final Button button;
        private final List<Option> options;
        private int selected;
        private int offset;

        private Dropdown(int x, int y, int width, int height, List<Option> options, Option selected) {
            this.options = options;
            this.selected = Math.max(0, options.indexOf(selected));
            this.button = Button.builder(options.get(this.selected).label(), ignored -> {
                openDropdown = openDropdown == this ? null : this;
                offset = Math.max(0, Math.min(this.selected, options.size() - visibleRows()));
            }).pos(x, y).size(width, height).build();
        }

        private Option value() { return options.get(selected); }
        private int visibleRows() { return Math.min(MAX_ROWS, options.size()); }
        private int listY() {
            int h = visibleRows() * 18;
            int below = button.getY() + button.getHeight();
            return below + h <= height ? below : button.getY() - h;
        }
        private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
            int y = listY();
            graphics.fill(button.getX(), y, button.getX() + button.getWidth(), y + visibleRows() * 18, 0xFF181818);
            for (int row = 0; row < visibleRows(); row++) {
                int index = offset + row;
                int rowY = y + row * 18;
                boolean hover = mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() && mouseY >= rowY && mouseY < rowY + 18;
                if (hover || index == selected) graphics.fill(button.getX() + 1, rowY + 1, button.getX() + button.getWidth() - 1, rowY + 17, hover ? 0xFF3A6A8A : 0xFF304A5A);
                graphics.drawString(font, options.get(index).label(), button.getX() + 4, rowY + 5, 0xFFFFFFFF);
            }
        }
        private boolean click(double x, double y) {
            if (x >= button.getX() && x < button.getX() + button.getWidth() && y >= button.getY() && y < button.getY() + button.getHeight()) {
                openDropdown = null;
                return true;
            }
            int listY = listY();
            if (x < button.getX() || x >= button.getX() + button.getWidth() || y < listY || y >= listY + visibleRows() * 18) return false;
            int index = offset + ((int) y - listY) / 18;
            if (index < options.size()) {
                selected = index;
                button.setMessage(options.get(index).label());
                openDropdown = null;
            }
            return true;
        }
        private boolean scroll(double amount) {
            offset = Math.max(0, Math.min(options.size() - visibleRows(), offset - (int) Math.signum(amount)));
            return true;
        }
    }
}
