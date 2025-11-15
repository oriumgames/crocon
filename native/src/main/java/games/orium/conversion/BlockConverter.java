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

        if (fromEdition == Edition.JAVA && toEdition == Edition.BEDROCK) {
            Identifier javaIdentifier = new Identifier(blockId, statesMap);
            ChunkerBlockIdentifier chunkerBlock =
                cache.javaResolvers.readBlockIdentifier(javaIdentifier);

            // Chunker returns a default 'air' object if the block is not found.
            // This is a reliable way to check for a failed lookup.
            if (chunkerBlock.isAir() && !blockId.contains("air")) {
                throw new IllegalArgumentException(
                    "Unknown or invalid Java block ID: " + blockId
                );
            }

            Optional<Identifier> bedrockId =
                cache.bedrockResolvers.writeBlockIdentifier(chunkerBlock, true);

            if (bedrockId.isPresent()) {
                CompoundTag result = new CompoundTag();
                result.put("id", bedrockId.get().getIdentifier());
                if (!bedrockId.get().getStates().isEmpty()) {
                    CompoundTag bedrockStates = new CompoundTag();
                    bedrockId
                        .get()
                        .getStates()
                        .forEach((key, stateValue) ->
                            bedrockStates.put(key, stateValue.toNBT())
                        );
                    result.put("states", bedrockStates);
                }
                return result;
            } else {
                throw new IllegalArgumentException(
                    "Failed to convert Java block to Bedrock: " + blockId
                );
            }
        } else if (
            fromEdition == Edition.BEDROCK && toEdition == Edition.JAVA
        ) {
            Identifier bedrockIdentifier = new Identifier(blockId, statesMap);
            ChunkerBlockIdentifier chunkerBlock =
                cache.bedrockResolvers.readBlockIdentifier(bedrockIdentifier);

            if (chunkerBlock.isAir() && !blockId.contains("air")) {
                throw new IllegalArgumentException(
                    "Unknown or invalid Bedrock block ID: " + blockId
                );
            }

            Optional<Identifier> javaId =
                cache.javaResolvers.writeBlockIdentifier(chunkerBlock, true);

            if (javaId.isPresent()) {
                CompoundTag result = new CompoundTag();
                result.put("id", javaId.get().getIdentifier());
                if (!javaId.get().getStates().isEmpty()) {
                    CompoundTag javaStates = new CompoundTag();
                    javaId
                        .get()
                        .getStates()
                        .forEach((key, stateValue) ->
                            javaStates.put(key, stateValue.toNBT())
                        );
                    result.put("states", javaStates);
                }
                return result;
            } else {
                throw new IllegalArgumentException(
                    "Failed to convert Bedrock block to Java: " + blockId
                );
            }
        }

        throw new UnsupportedOperationException(
            "Unsupported conversion direction from " +
                fromEdition +
                " to " +
                toEdition
        );
    }
}
