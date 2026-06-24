package net.dgu.dgutweak.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ItemVariantsScreen extends Screen {
    private static final int MAX_VARIANTS = 6;
    private static final int VISIBLE_VARIANTS = 3;

    private final Screen parent;
    private final Identifier group;
    private final List<Identifier> initial;
    private final Consumer<List<Identifier>> onSave;
    private final List<Dropdown> rows = new ArrayList<>();
    private Dropdown openDropdown;
    private int scrollOffset;
    private int rowsTop;

    public ItemVariantsScreen(Screen parent, Identifier group, List<Identifier> initial,
                              Consumer<List<Identifier>> onSave) {
        super(Component.literal("Colors"));
        this.parent = parent;
        this.group = group;
        this.initial = List.copyOf(initial);
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        rows.clear();
        int panelWidth = Math.min(260, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(8, (height - 125) / 2);
        rowsTop = top + 35;
        List<Option> options = variantOptions();
        for (int index = 0; index < MAX_VARIANTS; index++) {
            Identifier saved = index < initial.size() ? initial.get(index) : null;
            Option selected = options.stream()
                    .filter(option -> Objects.equals(option.id(), saved))
                    .findFirst()
                    .orElse(options.get(0));
            Dropdown dropdown = new Dropdown(left + 10, rowsTop + index * 24, panelWidth - 20, 20, options, selected);
            addRenderableWidget(dropdown.button);
            rows.add(dropdown);
        }
        updateVisibleRows();
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.auto_filter.save"), button -> save())
                .pos(width / 2 - 82, top + 90).size(78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.auto_filter.cancel"), button -> onClose())
                .pos(width / 2 + 4, top + 90).size(78, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC101010);
        int panelWidth = Math.min(260, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(8, (height - 125) / 2);
        graphics.fill(left, top, left + panelWidth, top + 120, 0xF0202020);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                (scrollOffset + 1) + "-" + Math.min(scrollOffset + VISIBLE_VARIANTS, MAX_VARIANTS) + " / " + MAX_VARIANTS,
                width / 2, top + 80, 0xFF888888);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (openDropdown != null) {
            openDropdown.renderList(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (openDropdown != null) {
            if (openDropdown.click(event.x(), event.y())) {
                return true;
            }
            openDropdown = null;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (openDropdown != null) {
            return openDropdown.scroll(scrollY);
        }
        int nextOffset = Math.max(0, Math.min(MAX_VARIANTS - VISIBLE_VARIANTS,
                scrollOffset - (int) Math.signum(scrollY)));
        if (nextOffset != scrollOffset) {
            scrollOffset = nextOffset;
            updateVisibleRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void updateVisibleRows() {
        for (int index = 0; index < rows.size(); index++) {
            Dropdown row = rows.get(index);
            boolean visible = index >= scrollOffset && index < scrollOffset + VISIBLE_VARIANTS;
            row.button.visible = visible;
            row.button.setY(rowsTop + (index - scrollOffset) * 24);
        }
    }

    private void save() {
        List<Identifier> variants = new ArrayList<>();
        for (Dropdown row : rows) {
            Identifier id = row.value().id();
            if (id != null && !variants.contains(id)) {
                variants.add(id);
            }
        }
        onSave.accept(variants);
        onClose();
    }

    private List<Option> variantOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(null, Component.translatable("gui.dgutweak.auto_filter.none")));
        for (Identifier id : ProfessionTradeCatalog.variants(group)) {
            options.add(new Option(id, ProfessionTradeCatalog.variantName(id)));
        }
        return options;
    }

    private record Option(Identifier id, Component label) {
    }

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

        private Option value() {
            return options.get(selected);
        }

        private int visibleRows() {
            return Math.min(MAX_ROWS, options.size());
        }

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
                boolean hover = mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                        && mouseY >= rowY && mouseY < rowY + 18;
                if (hover || index == selected) {
                    graphics.fill(button.getX() + 1, rowY + 1, button.getX() + button.getWidth() - 1, rowY + 17,
                            hover ? 0xFF3A6A8A : 0xFF304A5A);
                }
                graphics.drawString(font, options.get(index).label(), button.getX() + 4, rowY + 5, 0xFFFFFFFF);
            }
        }

        private boolean click(double x, double y) {
            if (x >= button.getX() && x < button.getX() + button.getWidth()
                    && y >= button.getY() && y < button.getY() + button.getHeight()) {
                openDropdown = null;
                return true;
            }
            int listY = listY();
            if (x < button.getX() || x >= button.getX() + button.getWidth()
                    || y < listY || y >= listY + visibleRows() * 18) {
                return false;
            }
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
