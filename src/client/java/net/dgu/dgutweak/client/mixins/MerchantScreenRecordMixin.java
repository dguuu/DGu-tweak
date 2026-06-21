package net.dgu.dgutweak.client.mixins;

import net.dgu.dgutweak.client.DGuTweakClient;
import net.dgu.dgutweak.client.AutoVillagerFilter;
import net.dgu.dgutweak.client.BestTradeEnchantmentsScreen;
import net.dgu.dgutweak.networking.RecordVillagerC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenRecordMixin extends AbstractContainerScreen<MerchantMenu> {

    protected MerchantScreenRecordMixin(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "method_25426", at = @At("TAIL"), remap = false)
    private void addRecordButtons(CallbackInfo ci) {
        int buttonX = this.leftPos + 150;
        int buttonY = this.topPos + 59;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.record"), button -> sendRecordRequest())
                .pos(buttonX, buttonY)
                .size(40, 14)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.query"), button -> sendDetailCommand())
                .pos(buttonX + 41, buttonY)
                .size(40, 14)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.dgutweak.trades"), button -> {
                    findNearestVillagerUuid(uuid -> DGuTweakClient.rememberMerchantOffers(uuid, this.menu.getOffers()));
                    this.onClose();
                    DGuTweakClient.requestTradeList();
                })
                .pos(buttonX + 82, buttonY)
                .size(40, 14)
                .build());

        Button autoFilterButton = Button.builder(
                        Component.translatable("gui.dgutweak.auto_filter.start"),
                        button -> AutoVillagerFilter.toggle())
                .pos(buttonX, buttonY + 15)
                .size(81, 14)
                .build();
        this.addRenderableWidget(autoFilterButton);
        AutoVillagerFilter.bindButton(autoFilterButton);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.dgutweak.auto_filter.settings"),
                        button -> AutoVillagerFilter.openSettings())
                .pos(buttonX + 82, buttonY + 15)
                .size(40, 14)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.dgutweak.best_enchantments"),
                        button -> Minecraft.getInstance().setScreen(
                                new BestTradeEnchantmentsScreen((MerchantScreen) (Object) this)))
                .pos(buttonX, buttonY + 30)
                .size(122, 14)
                .build());
    }

    private static void sendRecordRequest() {
        findNearestVillagerEntityId(entityId -> ClientPlayNetworking.send(new RecordVillagerC2SPayload(entityId)));
    }

    private static void sendDetailCommand() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        findNearestVillagerUuid(uuid -> client.player.connection.sendCommand("vt detail " + uuid));
    }

    private static void findNearestVillagerEntityId(java.util.function.IntConsumer consumer) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        client.level.getEntitiesOfClass(Villager.class, client.player.getBoundingBox().inflate(6.0))
                .stream()
                .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(client.player)))
                .ifPresentOrElse(
                        villager -> consumer.accept(villager.getId()),
                        () -> client.level.getEntitiesOfClass(WanderingTrader.class, client.player.getBoundingBox().inflate(6.0))
                                .stream()
                                .min(Comparator.comparingDouble(trader -> trader.distanceToSqr(client.player)))
                                .ifPresent(trader -> consumer.accept(trader.getId()))
                );
    }

    private static void findNearestVillagerUuid(java.util.function.Consumer<java.util.UUID> consumer) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        client.level.getEntitiesOfClass(Villager.class, client.player.getBoundingBox().inflate(6.0))
                .stream()
                .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(client.player)))
                .ifPresentOrElse(
                        villager -> consumer.accept(villager.getUUID()),
                        () -> client.level.getEntitiesOfClass(WanderingTrader.class, client.player.getBoundingBox().inflate(6.0))
                                .stream()
                                .min(Comparator.comparingDouble(trader -> trader.distanceToSqr(client.player)))
                                .ifPresent(trader -> consumer.accept(trader.getUUID()))
                );
    }
}
