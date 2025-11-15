package games.orium.cache;

import com.hivemc.chunker.conversion.WorldConverter;
import com.hivemc.chunker.conversion.encoding.bedrock.BedrockDataVersion;
import com.hivemc.chunker.conversion.encoding.bedrock.BedrockEncoders;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.BedrockResolvers;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.biome.BedrockBiomeIDResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.itemstack.BedrockItemStackResolver;
import com.hivemc.chunker.conversion.encoding.java.JavaDataVersion;
import com.hivemc.chunker.conversion.encoding.java.JavaEncoders;
import com.hivemc.chunker.conversion.encoding.java.base.resolver.JavaResolvers;
import com.hivemc.chunker.conversion.encoding.java.base.resolver.biome.JavaNamedBiomeResolver;
import com.hivemc.chunker.conversion.encoding.java.base.resolver.itemstack.JavaItemStackResolver;
import com.hivemc.chunker.conversion.intermediate.level.ChunkerLevel;
import com.hivemc.chunker.conversion.intermediate.level.map.ChunkerMap;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;
import games.orium.util.MockConverter;
import games.orium.util.VersionUtil;
import java.io.Closeable;
import java.util.Collections;
import java.util.List;

public class ResolverCache implements Closeable {

    public final WorldConverter converter;
    public final JavaResolvers javaResolvers;
    public final BedrockResolvers bedrockResolvers;
    public final JavaNamedBiomeResolver javaBiomeResolver;
    public final BedrockBiomeIDResolver bedrockBiomeResolver;
    public final JavaItemStackResolver javaItemStackResolver;
    public final BedrockItemStackResolver bedrockItemStackResolver;

    public ResolverCache(String javaVersion, String bedrockVersion) {
        JavaDataVersion javaVer = VersionUtil.parseJavaVersion(javaVersion);
        BedrockDataVersion bedrockVer = VersionUtil.parseBedrockVersion(
            bedrockVersion
        );

        this.converter = new MockConverter(
            new ChunkerLevel(
                null,
                null,
                List.of(
                    new ChunkerMap(
                        1,
                        1,
                        100,
                        100,
                        (byte) 0,
                        Dimension.OVERWORLD,
                        0,
                        0,
                        true,
                        true,
                        null,
                        null
                    )
                ),
                null,
                Collections.emptyList()
            )
        );

        this.javaResolvers = JavaEncoders.getNearestEncoder(javaVer)
            .writerConstructor()
            .construct(null, javaVer.getVersion(), converter)
            .buildResolvers(converter)
            .build();

        this.bedrockResolvers = BedrockEncoders.getNearestEncoder(bedrockVer)
            .writerConstructor()
            .construct(null, bedrockVer.getVersion(), converter)
            .buildResolvers(converter)
            .build();

        this.javaBiomeResolver = new JavaNamedBiomeResolver(
            javaVer.getVersion(),
            false
        );
        this.bedrockBiomeResolver = new BedrockBiomeIDResolver(
            bedrockVer.getVersion()
        );

        this.javaItemStackResolver = new JavaItemStackResolver(javaResolvers);
        this.bedrockItemStackResolver = new BedrockItemStackResolver(
            bedrockResolvers
        );
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
