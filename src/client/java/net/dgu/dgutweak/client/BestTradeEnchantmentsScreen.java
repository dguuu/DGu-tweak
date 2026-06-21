package net.dgu.dgutweak.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class BestTradeEnchantmentsScreen extends Screen {
    private static final int PANEL = 0xE0202020;
    private static final int PANEL_DARK = 0xE8181818;
    private static final int BORDER = 0xFF555555;
    private static final int SELECTED = 0xFF315E7D;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int ACCENT = 0xFF7DD3FC;

    private static final List<Plan> PLANS = List.of(
            armor("diamond_helmet", "respiration", "aqua_affinity", "thorns"),
            armor("diamond_chestplate", "thorns"),
            armor("diamond_leggings"),
            armor("diamond_boots", "feather_falling", "depth_strider"),
            plan("fisherman", "fishing_rod",
                    group("core", "unbreaking", "lure", "luck_of_the_sea")),
            plan("fletcher", "bow",
                    group("core", "power", "unbreaking", "punch", "flame", "infinity")),
            plan("fletcher", "crossbow",
                    group("core", "unbreaking", "quick_charge"),
                    group("attack_choice", "piercing", "multishot")),
            tool("iron_axe", true), tool("iron_shovel", false), tool("iron_pickaxe", false),
            tool("diamond_axe", true), tool("diamond_shovel", false), tool("diamond_pickaxe", false),
            weapon("iron_sword"), weapon("diamond_sword"),
            plan("weaponsmith", "diamond_axe",
                    group("core", "unbreaking", "efficiency"),
                    group("harvest_choice", "fortune", "silk_touch"),
                    group("damage_choice", "sharpness", "smite", "bane_of_arthropods"))
    );

    private final Screen parent;
    private List<String> professions = List.of();
    private List<Plan> visiblePlans = List.of();
    private String selectedProfession;
    private int selectedPlan;

    public BestTradeEnchantmentsScreen(Screen parent) {
        super(Component.translatable("gui.dgutweak.best_enchantments.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        professions = PLANS.stream().map(Plan::profession).distinct().toList();
        if (selectedProfession == null) selectedProfession = professions.get(0);
        rebuildPlans();
        int panelWidth = Math.min(650, width - 24);
        int left = (width - panelWidth) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.close"), button -> onClose())
                .pos(left + panelWidth - 62, 34).size(52, 18).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xAA000000);
        int panelWidth = Math.min(650, width - 24);
        int panelHeight = Math.min(285, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int contentTop = top + 42;
        int contentBottom = top + panelHeight - 10;

        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        graphics.fill(left, top, left + panelWidth, top + 24, PANEL_DARK);
        border(graphics, left, top, panelWidth, panelHeight);
        graphics.drawCenteredString(font, title, width / 2, top + 8, TEXT);
        super.render(graphics, mouseX, mouseY, partialTick);

        int professionsLeft = left + 10;
        int professionsRight = professionsLeft + 120;
        int itemsLeft = professionsRight + 8;
        int itemsRight = itemsLeft + 150;
        int detailsLeft = itemsRight + 12;
        int detailsRight = left + panelWidth - 12;

        drawProfessions(graphics, professionsLeft, professionsRight, contentTop, mouseX, mouseY);
        drawItems(graphics, itemsLeft, itemsRight, contentTop, mouseX, mouseY);
        drawDetails(graphics, detailsLeft, detailsRight, contentTop, contentBottom);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int panelWidth = Math.min(650, width - 24);
        int panelHeight = Math.min(285, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int contentTop = top + 42;
        int professionsLeft = left + 10;
        int professionsRight = professionsLeft + 120;
        int itemsLeft = professionsRight + 8;
        int itemsRight = itemsLeft + 150;

        if (inside(event, professionsLeft, contentTop, professionsRight, top + panelHeight - 10)) {
            int index = ((int) event.y() - contentTop) / 20;
            if (index >= 0 && index < professions.size()) {
                selectedProfession = professions.get(index);
                selectedPlan = 0;
                rebuildPlans();
                return true;
            }
        }
        if (inside(event, itemsLeft, contentTop, itemsRight, top + panelHeight - 10)) {
            int index = ((int) event.y() - contentTop) / 20;
            if (index >= 0 && index < visiblePlans.size()) {
                selectedPlan = index;
                return true;
            }
        }
        return false;
    }

    private void drawProfessions(GuiGraphics graphics, int left, int right, int top, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.dgutweak.best_enchantments.profession"), left, top - 13, ACCENT);
        for (int index = 0; index < professions.size(); index++) {
            String profession = professions.get(index);
            int y = top + index * 20;
            rowBackground(graphics, left, right, y, profession.equals(selectedProfession), mouseX, mouseY);
            graphics.drawString(font, ProfessionTradeCatalog.professionName(profession), left + 3, y + 5, TEXT);
        }
    }

    private void drawItems(GuiGraphics graphics, int left, int right, int top, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.dgutweak.best_enchantments.item"), left, top - 13, ACCENT);
        for (int index = 0; index < visiblePlans.size(); index++) {
            Plan plan = visiblePlans.get(index);
            int y = top + index * 20;
            rowBackground(graphics, left, right, y, index == selectedPlan, mouseX, mouseY);
            graphics.drawString(font, itemName(plan.item()), left + 3, y + 5, TEXT);
        }
    }

    private void drawDetails(GuiGraphics graphics, int left, int right, int top, int bottom) {
        if (visiblePlans.isEmpty()) return;
        Plan plan = visiblePlans.get(Math.min(selectedPlan, visiblePlans.size() - 1));
        Identifier itemId = id("minecraft:" + plan.item());
        int y = top - 13;
        graphics.drawString(font, itemName(plan.item()), left, y, 0xFFFFFFFF);
        y += 16;
        for (Group group : plan.groups()) {
            graphics.drawString(font, Component.translatable("gui.dgutweak.best_enchantments." + group.label()), left, y, ACCENT);
            y += 11;
            for (String enchantment : group.enchantments()) {
                Identifier enchantmentId = id("minecraft:" + enchantment);
                int level = ItemEnchantmentsScreen.highestPossibleTradeLevel(itemId, enchantmentId);
                if (level <= 0) continue;
                Component line = Component.literal(group.label().endsWith("choice") ? "• " : "✓ ")
                        .append(Component.translatable("enchantment.minecraft." + enchantment))
                        .append(" " + roman(level));
                for (FormattedCharSequence wrapped : font.split(line, right - left)) {
                    if (y >= bottom - 10) return;
                    graphics.drawString(font, wrapped, left + 4, y, TEXT);
                    y += 10;
                }
            }
            y += 5;
        }
        if (y < bottom - 20) {
            graphics.drawString(font, Component.translatable("gui.dgutweak.best_enchantments.note"), left, bottom - 10, MUTED);
        }
    }

    private void rowBackground(GuiGraphics graphics, int left, int right, int y,
                               boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + 19;
        graphics.fill(left, y, right, y + 19, selected ? SELECTED : hovered ? 0x55444444 : 0x40282828);
    }

    private void rebuildPlans() {
        visiblePlans = PLANS.stream().filter(plan -> plan.profession().equals(selectedProfession)).toList();
        selectedPlan = Math.min(selectedPlan, Math.max(0, visiblePlans.size() - 1));
    }

    private static Plan armor(String item, String... special) {
        List<String> core = new java.util.ArrayList<>();
        core.add("unbreaking");
        core.addAll(List.of(special));
        return plan("armorer", item,
                new Group("core", List.copyOf(core)),
                group("protection_choice", "protection", "fire_protection", "blast_protection", "projectile_protection"));
    }

    private static Plan tool(String item, boolean axe) {
        return plan("toolsmith", item,
                group("core", "efficiency", "unbreaking"),
                group("harvest_choice", "fortune", "silk_touch"),
                axe ? group("damage_choice", "sharpness", "smite", "bane_of_arthropods") : group("none"));
    }

    private static Plan weapon(String item) {
        return plan("weaponsmith", item,
                group("core", "unbreaking", "sweeping_edge", "looting", "knockback", "fire_aspect"),
                group("damage_choice", "sharpness", "smite", "bane_of_arthropods"));
    }

    private static Plan plan(String profession, String item, Group... groups) {
        return new Plan(profession, item, List.of(groups).stream()
                .filter(group -> !group.enchantments().isEmpty()).toList());
    }

    private static Group group(String label, String... enchantments) {
        return new Group(label, List.of(enchantments));
    }

    private static Component itemName(String item) {
        return Component.translatable("item.minecraft." + item);
    }

    private static Identifier id(String value) {
        return Identifier.parse(value);
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    private static boolean inside(MouseButtonEvent event, int left, int top, int right, int bottom) {
        return event.x() >= left && event.x() < right && event.y() >= top && event.y() < bottom;
    }

    private static void border(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + 1, BORDER);
        graphics.fill(left, top + height - 1, left + width, top + height, BORDER);
        graphics.fill(left, top, left + 1, top + height, BORDER);
        graphics.fill(left + width - 1, top, left + width, top + height, BORDER);
    }

    private record Plan(String profession, String item, List<Group> groups) { }
    private record Group(String label, List<String> enchantments) { }
}
