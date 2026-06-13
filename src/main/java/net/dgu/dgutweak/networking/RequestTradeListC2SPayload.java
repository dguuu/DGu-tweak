package net.dgu.dgutweak.networking;

import net.dgu.dgutweak.DGuTweak;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

public record RequestTradeListC2SPayload() implements CustomPacketPayload {
    public static final Type<RequestTradeListC2SPayload> PACKET_ID = new Type<>(DGuTweak.id("request_trade_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTradeListC2SPayload> PACKET_CODEC =
            StreamCodec.unit(new RequestTradeListC2SPayload());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}
