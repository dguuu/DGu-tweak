package net.dgu.dgutweak.networking;

import net.dgu.dgutweak.DGuTweak;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record GlowVillagerC2SPayload(UUID uuid) implements CustomPacketPayload {
    public static final Type<GlowVillagerC2SPayload> PACKET_ID = new Type<>(DGuTweak.id("glow_villager"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GlowVillagerC2SPayload> PACKET_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeUUID(payload.uuid()),
                    buffer -> new GlowVillagerC2SPayload(buffer.readUUID())
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}
