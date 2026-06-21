package net.dgu.dgutweak.networking;

import net.dgu.dgutweak.DGuTweak;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TradeListS2CPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<TradeListS2CPayload> PACKET_ID = new Type<>(DGuTweak.id("trade_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeListS2CPayload> PACKET_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.entries().size());
                        for (Entry entry : payload.entries()) {
                            entry.write(buffer);
                        }
                    },
                    buffer -> {
                        int size = buffer.readVarInt();
                        List<Entry> entries = new ArrayList<>(size);
                        for (int index = 0; index < size; index++) {
                            entries.add(Entry.read(buffer));
                        }
                        return new TradeListS2CPayload(entries);
                    }
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }

    public record Entry(
            UUID uuid,
            BlockPos pos,
            Identifier dimension,
            String profession,
            int level,
            String resultName,
            String resultTranslationKey,
            List<EnchantmentData> resultEnchantments,
            int resultCount,
            String costAName,
            String costATranslationKey,
            int baseCostA,
            int currentCostA,
            String costBName,
            String costBTranslationKey,
            int costB,
            boolean locked,
            boolean live,
            double distance
    ) {
        private static Entry read(RegistryFriendlyByteBuf buffer) {
            return new Entry(
                    buffer.readUUID(),
                    buffer.readBlockPos(),
                    Identifier.STREAM_CODEC.decode(buffer),
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    readEnchantments(buffer),
                    buffer.readVarInt(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readDouble()
            );
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(uuid);
            buffer.writeBlockPos(pos);
            Identifier.STREAM_CODEC.encode(buffer, dimension);
            buffer.writeUtf(profession);
            buffer.writeVarInt(level);
            buffer.writeUtf(resultName);
            buffer.writeUtf(resultTranslationKey);
            writeEnchantments(buffer, resultEnchantments);
            buffer.writeVarInt(resultCount);
            buffer.writeUtf(costAName);
            buffer.writeUtf(costATranslationKey);
            buffer.writeVarInt(baseCostA);
            buffer.writeVarInt(currentCostA);
            buffer.writeUtf(costBName);
            buffer.writeUtf(costBTranslationKey);
            buffer.writeVarInt(costB);
            buffer.writeBoolean(locked);
            buffer.writeBoolean(live);
            buffer.writeDouble(distance);
        }

        private static List<EnchantmentData> readEnchantments(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<EnchantmentData> enchantments = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                enchantments.add(new EnchantmentData(Identifier.STREAM_CODEC.decode(buffer), buffer.readVarInt()));
            }
            return List.copyOf(enchantments);
        }

        private static void writeEnchantments(RegistryFriendlyByteBuf buffer, List<EnchantmentData> enchantments) {
            buffer.writeVarInt(enchantments.size());
            for (EnchantmentData enchantment : enchantments) {
                Identifier.STREAM_CODEC.encode(buffer, enchantment.id());
                buffer.writeVarInt(enchantment.level());
            }
        }
    }

    public record EnchantmentData(Identifier id, int level) { }
}
