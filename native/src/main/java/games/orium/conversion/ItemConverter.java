package games.orium.conversion;

import com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack.ChunkerItemStack;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import games.orium.cache.ResolverCache;
import games.orium.util.Edition;
import java.util.Optional;

public class ItemConverter {

    private ItemConverter() {
        // Private constructor to prevent instantiation
    }

    public static CompoundTag convert(
        ResolverCache cache,
        Edition fromEdition,
        Edition toEdition,
        CompoundTag data
    ) throws IllegalArgumentException {
        if (fromEdition == Edition.JAVA && toEdition == Edition.BEDROCK) {
            Optional<ChunkerItemStack> chunkerItem =
                cache.javaItemStackResolver.to(data);

            if (chunkerItem.isEmpty()) {
                throw new IllegalArgumentException(
                    "Failed to parse Java item NBT for ID: " +
                        data.getString("id", "unknown")
                );
            }

            return cache.bedrockItemStackResolver
                .from(chunkerItem.get())
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Failed to convert Java item to Bedrock format for ID: " +
                            data.getString("id", "unknown")
                    )
                );
        } else if (
            fromEdition == Edition.BEDROCK && toEdition == Edition.JAVA
        ) {
            Optional<ChunkerItemStack> chunkerItem =
                cache.bedrockItemStackResolver.to(data);

            if (chunkerItem.isEmpty()) {
                throw new IllegalArgumentException(
                    "Failed to parse Bedrock item NBT for ID: " +
                        data.getString("id", "unknown")
                );
            }

            return cache.javaItemStackResolver
                .from(chunkerItem.get())
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Failed to convert Bedrock item to Java format for ID: " +
                            data.getString("id", "unknown")
                    )
                );
        }

        throw new UnsupportedOperationException(
            "Unsupported conversion direction from " +
                fromEdition +
                " to " +
                toEdition
        );
    }
}
