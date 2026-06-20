package net.dgu.dgutweak.client.mixins;

import net.dgu.dgutweak.client.AutoVillagerFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleMerchantOffers", at = @At("TAIL"))
    private void afterMerchantOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof MerchantScreen screen) {
            AutoVillagerFilter.onMerchantOffersUpdated(screen.getMenu().getOffers());
        }
    }
}
