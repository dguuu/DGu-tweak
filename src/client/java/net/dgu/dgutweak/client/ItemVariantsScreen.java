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
    private final Screen parent;
    private final Identifier group;
    private final List<Identifier> initial;
    private final Consumer<List<Identifier>> onSave;
    private Dropdown dropdown;

    public ItemVariantsScreen(Screen parent, Identifier group, List<Identifier> initial,
                              Consumer<List<Identifier>> onSave) {
        super(Component.translatable("gui.dgutweak.auto_filter.colors.title"));
        this.parent = parent;
        this.group = group;
        this.initial = List.copyOf(initial);
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(260, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(8, (height - 105) / 2);
        List<Option> options = variantOptions();
        Identifier saved = initial.isEmpty() ? null : initial.get(0);
        Option selected = options.stream()
                .filter(option -> Objects.equals(option.id(), saved))
                .findFirst()
                .orElse(options.get(0));
        dropdown = new Dropdown(left + 10, top + 36, panelWidth - 20, 20, options, selected);
        addRenderableWidget(dropdown.button);
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.auto_filter.save"), button -> save())
                .pos(width / 2 - 82, top + 72).size(78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.auto_filter.cancel"), button -> onClose())
                .pos(width / 2 + 4, top + 72).size(78, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC101010);
        int panelWidth = Math.min(260, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(8, (height - 105) / 2);
        graphics.fill(left, top, left + panelWidth, top + 100, 0xF0202020);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (dropdown != null && dropdown.open) {
            dropdown.renderList(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (dropdown != null && dropdown.open) {
            if (dropdown.click(event.x(), event.y())) {
                return true;
            }
            dropdown.open = false;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dropdown != null && dropdown.open) {
            return dropdown.scroll(scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void save() {
        List<Identifier> variants = new ArrayList<>();
        Identifier id = dropdown.value().id();
        if (id != null) {
            variants.add(id);
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
        private boolean open;

        private Dropdown(int x, int y, int width, int height, List<Option> options, Option selected) {
            this.options = options;
            this.selected = Math.max(0, options.indexOf(selected));
            this.button = Button.builder(options.get(this.selected).label(), ignored -> {
                open = !open;
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
                open = false;
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
                open = false;
            }
            return true;
        }

        private boolean scroll(double amount) {
            offset = Math.max(0, Math.min(options.size() - visibleRows(), offset - (int) Math.signum(amount)));
            return true;
        }
    }
}
