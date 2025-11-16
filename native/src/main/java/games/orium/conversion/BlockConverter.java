package games.orium.conversion;

import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.ChunkerBlockIdentifier;
import com.hivemc.chunker.mapping.identifier.Identifier;
import com.hivemc.chunker.mapping.identifier.states.StateValue;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import games.orium.cache.ResolverCache;
import games.orium.util.Edition;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BlockConverter {

    private BlockConverter() {
        // Private constructor to prevent instantiation
    }

    public static CompoundTag convert(
        ResolverCache cache,
        Edition fromEdition,
        Edition toEdition,
        CompoundTag data
    ) throws IllegalArgumentException {
        String blockId = data.getString("id");
        CompoundTag states = data.getCompound("states");

        Map<String, StateValue<?>> statesMap = new HashMap<>();
        if (states != null) {
            for (Map.Entry<String, Tag<?>> entry : states) {
                StateValue<?> stateValue = StateValue.fromBoxed(
                    entry.getValue().getBoxedValue()
                );
                statesMap.put(entry.getKey(), stateValue);
            }
        }

        Identifier inputIdentifier = new Identifier(blockId, statesMap);
        ChunkerBlockIdentifier chunkerBlock;

        if (fromEdition == Edition.JAVA) {
            chunkerBlock = cache.javaResolvers.readBlockIdentifier(
                inputIdentifier
            );
            if (chunkerBlock.isAir() && !blockId.contains("air")) {
                throw new IllegalArgumentException(
                    "Unknown or invalid Java block ID: " + blockId
                );
            }
        } else if (fromEdition == Edition.BEDROCK) {
            chunkerBlock = cache.bedrockResolvers.readBlockIdentifier(
                inputIdentifier
            );
            if (chunkerBlock.isAir() && !blockId.contains("air")) {
                throw new IllegalArgumentException(
                    "Unknown or invalid Bedrock block ID: " + blockId
                );
            }
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'from' edition: " + fromEdition
            );
        }

        Optional<Identifier> outputId;
        if (toEdition == Edition.JAVA) {
            outputId = cache.javaResolvers.writeBlockIdentifier(
                chunkerBlock,
                true
            );
        } else if (toEdition == Edition.BEDROCK) {
            outputId = cache.bedrockResolvers.writeBlockIdentifier(
                chunkerBlock,
                true
            );
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'to' edition: " + toEdition
            );
        }

        if (outputId.isPresent()) {
            CompoundTag result = new CompoundTag();
            Identifier id = outputId.get();
            result.put("id", id.getIdentifier());

            if (!id.getStates().isEmpty()) {
                CompoundTag outputStates = new CompoundTag();
                id
                    .getStates()
                    .forEach((key, stateValue) ->
                        outputStates.put(key, stateValue.toNBT())
                    );
                result.put("states", outputStates);
            }
            return result;
        } else {
            throw new IllegalArgumentException(
                "Failed to convert block from " +
                    fromEdition +
                    " to " +
                    toEdition +
                    ": " +
                    blockId
            );
        }
    }
}
