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
    ) throws Exception {
        if (fromEdition == Edition.JAVA && toEdition == Edition.BEDROCK) {
            // Java NBT -> Chunker -> Bedrock NBT
            Optional<Entity> entity = cache.javaResolvers
                .entityResolver()
                .to(data);

            if (entity.isEmpty()) {
                String identifier = data
                    .getOptionalValue("id", String.class)
                    .orElse("unknown");
                throw new IllegalArgumentException(
                    "Failed to parse Java entity NBT. ID: " + identifier
                );
            }

            Optional<CompoundTag> result = cache.bedrockResolvers
                .entityResolver()
                .from(entity.get());

            if (result.isEmpty() || result.get().size() == 0) {
                throw new IllegalStateException(
                    "Failed to convert entity to Bedrock format"
                );
            }
            return result.get();
        } else if (
            fromEdition == Edition.BEDROCK && toEdition == Edition.JAVA
        ) {
            // Bedrock NBT -> Chunker -> Java NBT
            Optional<Entity> entity = cache.bedrockResolvers
                .entityResolver()
                .to(data);

            if (entity.isEmpty()) {
                String identifier = data
                    .getOptionalValue("identifier", String.class)
                    .orElse("unknown");
                throw new IllegalArgumentException(
                    "Failed to parse Bedrock entity NBT. ID: " + identifier
                );
            }

            Optional<CompoundTag> result = cache.javaResolvers
                .entityResolver()
                .from(entity.get());

            if (result.isEmpty() || result.get().size() == 0) {
                throw new IllegalStateException(
                    "Failed to convert entity to Java format"
                );
            }
            return result.get();
        }

        throw new IllegalArgumentException(
            "Unsupported conversion direction: " +
                fromEdition +
                " to " +
                toEdition
        );
    }
}
