package net.dgu.dgutweak.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dgu.dgutweak.DGuTweak;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RecordedVillagersData extends SavedData {

    public enum RecordResult {
        ADDED,
        UPDATED
    }

    public record VillagerRecord(
            UUID uuid,
            BlockPos pos,
            ResourceKey<Level> dimension,
            MerchantOffers offers,
            MerchantOffers lockedOffers,
            String profession,
            int level,
            long recordedAt,
            String recordedBy
    ) {
        public static final Codec<VillagerRecord> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        UUIDUtil.CODEC.fieldOf("uuid").forGetter(VillagerRecord::uuid),
                        BlockPos.CODEC.fieldOf("pos").forGetter(VillagerRecord::pos),
                        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(VillagerRecord::dimension),
                        MerchantOffers.CODEC.fieldOf("offers").forGetter(VillagerRecord::offers),
                        MerchantOffers.CODEC.optionalFieldOf("lockedOffers", new MerchantOffers()).forGetter(VillagerRecord::lockedOffers),
                        Codec.STRING.optionalFieldOf("profession", "none").forGetter(VillagerRecord::profession),
                        Codec.INT.optionalFieldOf("level", 1).forGetter(VillagerRecord::level),
                        Codec.LONG.optionalFieldOf("recordedAt", 0L).forGetter(VillagerRecord::recordedAt),
                        Codec.STRING.optionalFieldOf("recordedBy", "unknown").forGetter(VillagerRecord::recordedBy)
                ).apply(instance, VillagerRecord::new)
        );
    }

    private static final Codec<RecordedVillagersData> CODEC = VillagerRecord.CODEC.listOf()
            .xmap(
                    list -> {
                        RecordedVillagersData data = new RecordedVillagersData();
                        list.forEach(record -> data.records.put(record.uuid(), record));
                        return data;
                    },
                    data -> new ArrayList<>(data.records.values())
            );

    public static final SavedDataType<RecordedVillagersData> TYPE = new SavedDataType<>(
            DGuTweak.MOD_ID + "_recorded_villagers",
            RecordedVillagersData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_SCOREBOARD
    );

    private final Map<UUID, VillagerRecord> records = new LinkedHashMap<>();

    public static RecordedVillagersData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public RecordResult addOrUpdate(VillagerRecord record) {
        boolean existed = records.containsKey(record.uuid());
        records.put(record.uuid(), record);
        setDirty();
        return existed ? RecordResult.UPDATED : RecordResult.ADDED;
    }

    public Map<UUID, VillagerRecord> getRecords() {
        return Collections.unmodifiableMap(records);
    }
}
