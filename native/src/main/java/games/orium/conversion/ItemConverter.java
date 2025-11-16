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
        Optional<ChunkerItemStack> chunkerItem;
        String idForError = data.getString("id", "unknown");

        if (fromEdition == Edition.JAVA) {
            chunkerItem = cache.javaItemStackResolver.to(data);
            if (chunkerItem.isEmpty()) {
                throw new IllegalArgumentException(
                    "Failed to parse Java item NBT for ID: " + idForError
                );
            }
        } else if (fromEdition == Edition.BEDROCK) {
            chunkerItem = cache.bedrockItemStackResolver.to(data);
            if (chunkerItem.isEmpty()) {
                throw new IllegalArgumentException(
                    "Failed to parse Bedrock item NBT for ID: " + idForError
                );
            }
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'from' edition: " + fromEdition
            );
        }

        Optional<CompoundTag> result;
        if (toEdition == Edition.JAVA) {
            result = cache.javaItemStackResolver.from(chunkerItem.get());
        } else if (toEdition == Edition.BEDROCK) {
            result = cache.bedrockItemStackResolver.from(chunkerItem.get());
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'to' edition: " + toEdition
            );
        }

        return result.orElseThrow(() ->
            new IllegalArgumentException(
                "Failed to convert item to " +
                    toEdition +
                    " format for ID: " +
                    idForError
            )
        );
    }
}
