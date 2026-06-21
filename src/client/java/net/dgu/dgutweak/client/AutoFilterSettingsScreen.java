package net.dgu.dgutweak.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class AutoFilterSettingsScreen extends Screen {
    private static final int MAX_TARGETS = 6;
    private static final int VISIBLE_TARGETS = 3;

    private final Screen parent;
    private final String profession;
    private final List<TargetRow> rows = new ArrayList<>();
    private Component error;
    private Dropdown<?> openDropdown;
    private int scrollOffset;
    private int rowsTop;
    private List<AutoVillagerFilter.Target> stagedTargets;

    public AutoFilterSettingsScreen(Screen parent) {
        this(parent, currentProfession());
    }

    private AutoFilterSettingsScreen(Screen parent, String profession) {
        super(Component.translatable("gui.dgutweak.auto_filter.settings.title"));
        this.parent = parent;
        this.profession = profession;
    }

    @Override
    protected void init() {
        rows.clear();
        int panelWidth = Math.min(340, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(5, (this.height - 174) / 2);
        rowsTop = top + 68;

        List<Option<String>> professionOptions = ProfessionTradeCatalog.PROFESSIONS.stream()
                .map(value -> new Option<>(value, ProfessionTradeCatalog.professionName(value)))
                .toList();
        addDropdown(new Dropdown<>(left + 10, top + 25, panelWidth - 20, 20,
                professionOptions, profession,
                value -> Minecraft.getInstance().setScreen(new AutoFilterSettingsScreen(parent, value))));

        if (stagedTargets == null) {
            List<AutoVillagerFilter.Target> saved = AutoVillagerFilter.targets().stream()
                    .filter(target -> target.profession().equals(profession))
                    .limit(MAX_TARGETS)
                    .toList();
            stagedTargets = new ArrayList<>(MAX_TARGETS);
            for (int index = 0; index < MAX_TARGETS; index++) {
                stagedTargets.add(index < saved.size() ? saved.get(index) : null);
            }
        }
        List<Choice> choices = itemChoices();

        for (int index = 0; index < MAX_TARGETS; index++) {
            AutoVillagerFilter.Target target = stagedTargets.get(index);
            Choice selected = findChoice(choices, target);
            int y = rowsTop + index * 23;
            Dropdown<Choice> targetDropdown = new Dropdown<>(left + 10, y, panelWidth - 112, 19,
                    choices.stream().map(choice -> new Option<>(choice, choice.name())).toList(), selected, value -> { });
            EditBox price = new EditBox(this.font, left + panelWidth - 54, y, 44, 19, Component.literal("20"));
            price.setMaxLength(3);
            price.setValue(target == null ? "20" : Integer.toString(target.maxEmeraldPrice()));
            addDropdown(targetDropdown);
            addRenderableWidget(price);
            TargetRow row = new TargetRow(targetDropdown, price,
                    target == null ? List.of() : target.enchantments());
            rows.add(row);
            int slot = index;
            Button enchantmentButton = Button.builder(Component.translatable("gui.dgutweak.auto_filter.enchantments"),
                            button -> openEnchantments(slot))
                    .pos(left + panelWidth - 96, y).size(36, 19).build();
            addRenderableWidget(enchantmentButton);
            row.enchantmentButton = enchantmentButton;
        }
        updateVisibleRows();

        int buttonY = top + 140;
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.auto_filter.save"), button -> save())
                .pos(this.width / 2 - 82, buttonY).size(78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.auto_filter.cancel"), button -> onClose())
                .pos(this.width / 2 + 4, buttonY).size(78, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xCC101010);
        int panelWidth = Math.min(340, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(5, (this.height - 174) / 2);
        graphics.fill(left, top, left + panelWidth, top + 168, 0xF0202020);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 8, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.dgutweak.auto_filter.item"), left + 10, top + 55, 0xFFAAAAAA);
        graphics.drawString(this.font, Component.translatable("gui.dgutweak.auto_filter.enchantments"), left + panelWidth - 96, top + 55, 0xFFAAAAAA);
        graphics.drawString(this.font, Component.translatable("gui.dgutweak.auto_filter.price"), left + panelWidth - 54, top + 55, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font,
                (scrollOffset + 1) + "-" + Math.min(scrollOffset + VISIBLE_TARGETS, MAX_TARGETS) + " / " + MAX_TARGETS,
                this.width / 2, top + 130, 0xFF888888);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (openDropdown != null) {
            openDropdown.renderList(graphics, mouseX, mouseY);
        }
        if (error != null) {
            graphics.drawCenteredString(this.font, error, this.width / 2, top + 162, 0xFFFF5555);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
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
        if (openDropdown != null && openDropdown.scroll(scrollY)) {
            return true;
        }
        int maxOffset = MAX_TARGETS - VISIBLE_TARGETS;
        int nextOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(scrollY)));
        if (nextOffset != scrollOffset) {
            scrollOffset = nextOffset;
            updateVisibleRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateVisibleRows() {
        for (int index = 0; index < rows.size(); index++) {
            TargetRow row = rows.get(index);
            boolean visible = index >= scrollOffset && index < scrollOffset + VISIBLE_TARGETS;
            int y = rowsTop + (index - scrollOffset) * 23;
            row.target.button.visible = visible;
            row.target.button.setY(y);
            row.price.visible = visible;
            row.price.setY(y);
            if (row.enchantmentButton != null) {
                row.enchantmentButton.visible = visible;
                row.enchantmentButton.setY(y);
            }
        }
    }

    private void save() {
        if (!syncRowsToStage()) {
            return;
        }
        List<AutoVillagerFilter.Target> targets = new ArrayList<>(AutoVillagerFilter.targets());
        targets.removeIf(target -> target.profession().equals(profession));
        for (AutoVillagerFilter.Target target : stagedTargets) {
            if (target != null) {
                targets.add(target);
            }
        }
        AutoVillagerFilter.setTargets(targets);
        onClose();
    }

    private void openEnchantments(int slot) {
        if (!syncRowsToStage()) {
            return;
        }
        AutoVillagerFilter.Target target = stagedTargets.get(slot);
        if (target == null) {
            error = Component.translatable("gui.dgutweak.auto_filter.select_item_first");
            return;
        }
        Minecraft.getInstance().setScreen(new ItemEnchantmentsScreen(this, target.resultItem(), target.enchantments(), enchantments -> {
            AutoVillagerFilter.Target current = stagedTargets.get(slot);
            stagedTargets.set(slot, new AutoVillagerFilter.Target(
                    current.profession(), current.resultItem(), enchantments, current.maxEmeraldPrice()));
        }));
    }

    private boolean syncRowsToStage() {
        try {
            for (int index = 0; index < rows.size(); index++) {
                TargetRow row = rows.get(index);
                Choice choice = row.target().value();
                if (choice.id() == null) {
                    stagedTargets.set(index, null);
                    continue;
                }
                int price = Integer.parseInt(row.price().getValue().trim());
                if (price <= 0) {
                    throw new NumberFormatException();
                }
                AutoVillagerFilter.Target previous = stagedTargets.get(index);
                if (previous != null && !previous.resultItem().equals(choice.id())) {
                    row.enchantments = List.of();
                }
                stagedTargets.set(index, new AutoVillagerFilter.Target(
                        profession,
                        choice.id(),
                        row.enchantments,
                        price));
            }
            error = null;
            return true;
        } catch (NumberFormatException exception) {
            error = Component.translatable("gui.dgutweak.auto_filter.invalid");
            return false;
        }
    }

    private List<Choice> itemChoices() {
        List<Choice> choices = new ArrayList<>();
        choices.add(Choice.empty());
        for (ProfessionTradeCatalog.Choice choice : ProfessionTradeCatalog.choices(profession)) {
            choices.add(new Choice(choice.id(), choice.name()));
        }
        return choices;
    }

    private Choice findChoice(List<Choice> choices, AutoVillagerFilter.Target target) {
        Identifier id = target == null ? null : target.resultItem();
        return choices.stream().filter(choice -> Objects.equals(choice.id(), id)).findFirst().orElse(choices.get(0));
    }

    private static String currentProfession() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return "librarian";
        }
        return client.level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
                        client.player.getBoundingBox().inflate(6.0D)).stream()
                .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(client.player)))
                .flatMap(villager -> villager.getVillagerData().profession().unwrapKey())
                .map(key -> key.identifier().getPath())
                .filter(ProfessionTradeCatalog.PROFESSIONS::contains)
                .orElse("librarian");
    }

    private <T> void addDropdown(Dropdown<T> dropdown) {
        addRenderableWidget(dropdown.button);
    }

    private static final class TargetRow {
        private final Dropdown<Choice> target;
        private final EditBox price;
        private Button enchantmentButton;
        private List<AutoVillagerFilter.EnchantmentRequirement> enchantments;

        private TargetRow(Dropdown<Choice> target, EditBox price,
                          List<AutoVillagerFilter.EnchantmentRequirement> enchantments) {
            this.target = target;
            this.price = price;
            this.enchantments = List.copyOf(enchantments);
        }

        private Dropdown<Choice> target() { return target; }
        private EditBox price() { return price; }
    }

    private record Choice(Identifier id, Component name) {
        private static Choice empty() {
            return new Choice(null, Component.translatable("gui.dgutweak.auto_filter.none"));
        }
    }

    private record Option<T>(T value, Component label) {
    }

    private final class Dropdown<T> {
        private static final int MAX_VISIBLE_ROWS = 6;

        private final Button button;
        private final List<Option<T>> options;
        private final Consumer<T> onSelect;
        private int selectedIndex;
        private int scrollOffset;

        private Dropdown(int x, int y, int width, int height, List<Option<T>> options, T selected, Consumer<T> onSelect) {
            this.options = options;
            this.onSelect = onSelect;
            this.selectedIndex = Math.max(0, findIndex(selected));
            this.button = Button.builder(options.get(this.selectedIndex).label(), ignored -> {
                        openDropdown = openDropdown == this ? null : this;
                        ensureSelectedVisible();
                    })
                    .pos(x, y)
                    .size(width, height)
                    .build();
        }

        private T value() {
            return options.get(selectedIndex).value();
        }

        private int findIndex(T selected) {
            for (int index = 0; index < options.size(); index++) {
                if (Objects.equals(options.get(index).value(), selected)) {
                    return index;
                }
            }
            return 0;
        }

        private void ensureSelectedVisible() {
            int rows = visibleRows();
            scrollOffset = Math.max(0, Math.min(selectedIndex, options.size() - rows));
        }

        private int visibleRows() {
            return Math.min(MAX_VISIBLE_ROWS, options.size());
        }

        private int listY() {
            int height = visibleRows() * 18;
            int below = button.getY() + button.getHeight();
            return below + height <= AutoFilterSettingsScreen.this.height ? below : button.getY() - height;
        }

        private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
            int rows = visibleRows();
            int y = listY();
            graphics.fill(button.getX(), y, button.getX() + button.getWidth(), y + rows * 18, 0xFF181818);
            for (int row = 0; row < rows; row++) {
                int index = scrollOffset + row;
                int rowY = y + row * 18;
                boolean hovered = mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                        && mouseY >= rowY && mouseY < rowY + 18;
                if (hovered || index == selectedIndex) {
                    graphics.fill(button.getX() + 1, rowY + 1, button.getX() + button.getWidth() - 1, rowY + 17,
                            hovered ? 0xFF3A6A8A : 0xFF304A5A);
                }
                graphics.drawString(font, options.get(index).label(), button.getX() + 4, rowY + 5, 0xFFFFFFFF);
            }
        }

        private boolean click(double mouseX, double mouseY) {
            if (mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                    && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight()) {
                openDropdown = null;
                return true;
            }
            int y = listY();
            if (mouseX < button.getX() || mouseX >= button.getX() + button.getWidth()
                    || mouseY < y || mouseY >= y + visibleRows() * 18) {
                return false;
            }
            int index = scrollOffset + ((int) mouseY - y) / 18;
            if (index >= 0 && index < options.size()) {
                selectedIndex = index;
                button.setMessage(options.get(index).label());
                openDropdown = null;
                onSelect.accept(options.get(index).value());
            }
            return true;
        }

        private boolean scroll(double amount) {
            int max = Math.max(0, options.size() - visibleRows());
            if (max == 0) {
                return true;
            }
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(amount)));
            return true;
        }
    }
}
