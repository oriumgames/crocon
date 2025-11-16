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
        Optional<ChunkerBiome> chunkerBiome;
        if (fromEdition == Edition.JAVA) {
            String biomeName = data.getString("name");
            if (biomeName == null || biomeName.isEmpty()) {
                throw new IllegalArgumentException(
                    "Input data for Java biome conversion must contain a 'name' field."
                );
            }
            chunkerBiome = cache.javaBiomeResolver.to(biomeName);
            if (chunkerBiome.isEmpty()) {
                throw new IllegalArgumentException(
                    "Unknown or invalid Java biome name: " + biomeName
                );
            }
        } else if (fromEdition == Edition.BEDROCK) {
            if (!data.contains("id")) {
                throw new IllegalArgumentException(
                    "Input data for Bedrock biome conversion must contain an 'id' field."
                );
            }
            int biomeId = data.getInt("id");
            chunkerBiome = cache.bedrockBiomeResolver.to(biomeId);
            if (chunkerBiome.isEmpty()) {
                throw new IllegalArgumentException(
                    "Unknown or invalid Bedrock biome ID: " + biomeId
                );
            }
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'from' edition: " + fromEdition
            );
        }

        ChunkerBiome biome = chunkerBiome.get();
        if (toEdition == Edition.JAVA) {
            return cache.javaBiomeResolver
                .from(biome)
                .map(name -> {
                    CompoundTag result = new CompoundTag();
                    result.put("name", name);
                    return result;
                })
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Failed to convert biome to Java name: " + biome
                    )
                );
        } else if (toEdition == Edition.BEDROCK) {
            return cache.bedrockBiomeResolver
                .from(biome)
                .map(id -> {
                    CompoundTag result = new CompoundTag();
                    result.put("id", id);
                    return result;
                })
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Failed to convert biome to Bedrock ID: " + biome
                    )
                );
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'to' edition: " + toEdition
            );
        }
    }
}
