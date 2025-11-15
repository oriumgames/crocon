package games.orium.util;


import com.hivemc.chunker.conversion.encoding.bedrock.BedrockDataVersion;
import com.hivemc.chunker.conversion.encoding.java.JavaDataVersion;

public class VersionUtil {

    private VersionUtil() {
        // Private constructor to prevent instantiation
    }

    public static JavaDataVersion parseJavaVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            // Find the latest version that is less than or equal to requested
            JavaDataVersion bestMatch = null;
            for (JavaDataVersion dataVersion : JavaDataVersion.getVersions()) {
                if (
                    dataVersion
                        .getVersion()
                        .isLessThanOrEqual(major, minor, patch)
                ) {
                    if (
                        bestMatch == null ||
                        dataVersion
                            .getVersion()
                            .isGreaterThan(bestMatch.getVersion())
                    ) {
                        bestMatch = dataVersion;
                    }
                }
            }

            if (bestMatch != null) {
                return bestMatch;
            }
        } catch (Exception e) {
            // Fall back to latest
        }
        return JavaDataVersion.latest();
    }

    public static BedrockDataVersion parseBedrockVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            // Find the latest version that is less than or equal to requested
            BedrockDataVersion bestMatch = null;
            for (BedrockDataVersion dataVersion : BedrockDataVersion.getVersions()) {
                if (
                    dataVersion
                        .getVersion()
                        .isLessThanOrEqual(major, minor, patch)
                ) {
                    if (
                        bestMatch == null ||
                        dataVersion
                            .getVersion()
                            .isGreaterThan(bestMatch.getVersion())
                    ) {
                        bestMatch = dataVersion;
                    }
                }
            }

            if (bestMatch != null) {
                return bestMatch;
            }
        } catch (Exception e) {
            // Fall back to latest
        }
        return BedrockDataVersion.latest();
    }
}
