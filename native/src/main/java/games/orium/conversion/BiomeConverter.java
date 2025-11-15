package games.orium.conversion;

import com.hivemc.chunker.conversion.intermediate.column.biome.ChunkerBiome;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import games.orium.cache.ResolverCache;
import games.orium.util.Edition;
import java.util.Optional;

public class BiomeConverter {

    private BiomeConverter() {
        // Private constructor to prevent instantiation
    }

    public static CompoundTag convert(
        ResolverCache cache,
        Edition fromEdition,
        Edition toEdition,
        CompoundTag data
    ) throws IllegalArgumentException {
        if (fromEdition == Edition.JAVA && toEdition == Edition.BEDROCK) {
            String biomeName = data.getString("name");
            if (biomeName == null || biomeName.isEmpty()) {
                throw new IllegalArgumentException(
                    "Input data for Java biome conversion must contain a 'name' field."
                );
            }

            Optional<ChunkerBiome> chunkerBiome = cache.javaBiomeResolver.to(
                biomeName
            );
            if (chunkerBiome.isEmpty()) {
                throw new IllegalArgumentException(
                    "Unknown or invalid Java biome name: " + biomeName
                );
            }

            return cache.bedrockBiomeResolver
                .from(chunkerBiome.get())
                .map(id -> {
                    CompoundTag result = new CompoundTag();
                    result.put("id", id);
                    return result;
                })
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Failed to convert Java biome to Bedrock ID: " +
                            biomeName
                    )
                );
        } else if (
            fromEdition == Edition.BEDROCK && toEdition == Edition.JAVA
        ) {
            if (!data.contains("id")) {
                throw new IllegalArgumentException(
                    "Input data for Bedrock biome conversion must contain an 'id' field."
                );
            }
            int biomeId = data.getInt("id");

            Optional<ChunkerBiome> chunkerBiome = cache.bedrockBiomeResolver.to(
                biomeId
            );
            if (chunkerBiome.isEmpty()) {
                throw new IllegalArgumentException(
                    "Unknown or invalid Bedrock biome ID: " + biomeId
                );
            }

            return cache.javaBiomeResolver
                .from(chunkerBiome.get())
                .map(name -> {
                    CompoundTag result = new CompoundTag();
                    result.put("name", name);
                    return result;
                })
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Failed to convert Bedrock biome to Java name: " +
                            biomeId
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
