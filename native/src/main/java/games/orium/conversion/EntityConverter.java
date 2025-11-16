package games.orium.conversion;

import com.hivemc.chunker.conversion.intermediate.column.entity.Entity;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import games.orium.cache.ResolverCache;
import games.orium.util.Edition;
import java.util.Optional;

public class EntityConverter {

    private EntityConverter() {
        // Private constructor to prevent instantiation
    }

    public static CompoundTag convert(
        ResolverCache cache,
        Edition fromEdition,
        Edition toEdition,
        CompoundTag data
    ) {
        Optional<Entity> entity;
        if (fromEdition == Edition.JAVA) {
            entity = cache.javaResolvers.entityResolver().to(data);
            if (entity.isEmpty()) {
                String identifier = data
                    .getOptionalValue("id", String.class)
                    .orElse("unknown");
                throw new IllegalArgumentException(
                    "Failed to parse Java entity NBT. ID: " + identifier
                );
            }
        } else if (fromEdition == Edition.BEDROCK) {
            entity = cache.bedrockResolvers.entityResolver().to(data);
            if (entity.isEmpty()) {
                String identifier = data
                    .getOptionalValue("identifier", String.class)
                    .orElse("unknown");
                throw new IllegalArgumentException(
                    "Failed to parse Bedrock entity NBT. ID: " + identifier
                );
            }
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'from' edition: " + fromEdition
            );
        }

        Optional<CompoundTag> result;
        if (toEdition == Edition.JAVA) {
            result = cache.javaResolvers.entityResolver().from(entity.get());
        } else if (toEdition == Edition.BEDROCK) {
            result = cache.bedrockResolvers.entityResolver().from(entity.get());
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'to' edition: " + toEdition
            );
        }

        if (result.isEmpty() || result.get().size() == 0) {
            throw new IllegalStateException(
                "Failed to convert entity to " + toEdition + " format"
            );
        }
        return result.get();
    }
}
