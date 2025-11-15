package games.orium.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {

    private static final Map<String, ResolverCache> VERSION_CACHE =
        new ConcurrentHashMap<>();

    // Private constructor to prevent instantiation
    private CacheManager() {}

    // Pre-initialize common version pairs to avoid stack issues during JNI calls
    static {
        try {
            // Suppress Caffeine warnings about lambda overrides
            java.util.logging.Logger caffeineLogger =
                java.util.logging.Logger.getLogger(
                    "com.github.benmanes.caffeine"
                );
            caffeineLogger.setLevel(java.util.logging.Level.SEVERE);

            getOrCreateCache("1.21.10", "1.21.120");
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    /**
     * Get or create a resolver cache for the given version pair.
     *
     * @param javaVersion   The Java edition version string.
     * @param bedrockVersion The Bedrock edition version string.
     * @return The cached or newly created ResolverCache.
     */
    public static ResolverCache getOrCreateCache(
        String javaVersion,
        String bedrockVersion
    ) {
        String cacheKey = javaVersion + ":" + bedrockVersion;
        return VERSION_CACHE.computeIfAbsent(cacheKey, k ->
            new ResolverCache(javaVersion, bedrockVersion)
        );
    }
}
