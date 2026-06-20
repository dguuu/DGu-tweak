package net.dgu.dgutweak.networking;

import net.dgu.dgutweak.DGuTweak;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

public record RecordVillagerC2SPayload(int entityId) implements CustomPacketPayload {

    public static final Type<RecordVillagerC2SPayload> PACKET_ID = new Type<>(DGuTweak.id("record_villager"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordVillagerC2SPayload> PACKET_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    RecordVillagerC2SPayload::entityId,
                    RecordVillagerC2SPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}
