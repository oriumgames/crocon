package games.orium.conversion;

import com.hivemc.chunker.conversion.intermediate.column.blockentity.BlockEntity;
import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.ChunkerBlockIdentifier;
import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.type.block.ChunkerVanillaBlockType;
import com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack.ChunkerItemProperty;
import com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack.ChunkerItemStack;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import games.orium.cache.ResolverCache;
import games.orium.util.Edition;
import java.util.Optional;

public class BlockEntityConverter {

    private BlockEntityConverter() {
        // Private constructor to prevent instantiation
    }

    public static CompoundTag convert(
        ResolverCache cache,
        Edition fromEdition,
        Edition toEdition,
        CompoundTag data
    ) {
        Optional<BlockEntity> blockEntity;
        if (fromEdition == Edition.JAVA) {
            blockEntity = cache.javaResolvers.blockEntityResolver().to(data);
            if (blockEntity.isEmpty()) {
                String identifier = data
                    .getOptionalValue("id", String.class)
                    .orElse("unknown");
                throw new IllegalArgumentException(
                    "Failed to parse Java block entity NBT. ID: " + identifier
                );
            }
        } else if (fromEdition == Edition.BEDROCK) {
            blockEntity = cache.bedrockResolvers.blockEntityResolver().to(data);
            if (blockEntity.isEmpty()) {
                String identifier = data
                    .getOptionalValue("id", String.class)
                    .orElse("unknown");
                throw new IllegalArgumentException(
                    "Failed to parse Bedrock block entity NBT. ID: " +
                        identifier
                );
            }
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'from' edition: " + fromEdition
            );
        }

        ChunkerVanillaBlockType blockType = getBlockTypeFromEntity(
            blockEntity.get()
        );

        ChunkerItemStack chunkerItem = new ChunkerItemStack(
            new ChunkerBlockIdentifier(blockType)
        );
        chunkerItem.put(ChunkerItemProperty.BLOCK_ENTITY, blockEntity.get());

        Optional<CompoundTag> outputNbt;
        if (toEdition == Edition.JAVA) {
            outputNbt = cache.javaItemStackResolver.from(chunkerItem);
        } else if (toEdition == Edition.BEDROCK) {
            outputNbt = cache.bedrockItemStackResolver.from(chunkerItem);
        } else {
            throw new UnsupportedOperationException(
                "Unsupported 'to' edition: " + toEdition
            );
        }

        if (outputNbt.isEmpty()) {
            throw new IllegalStateException(
                "Failed to convert item with block entity to " +
                    toEdition +
                    " format"
            );
        }

        if (toEdition == Edition.JAVA) {
            CompoundTag tag = outputNbt.get().getCompound("tag");
            if (tag != null) {
                CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");
                if (blockEntityTag != null) {
                    return blockEntityTag;
                }
            }
        }
        // Bedrock conversions return the full item NBT
        return outputNbt.get();
    }

    private static ChunkerVanillaBlockType getBlockTypeFromEntity(
        BlockEntity entity
    ) {
        Class<? extends BlockEntity> entityClass = entity.getClass();

        for (ChunkerVanillaBlockType blockType : ChunkerVanillaBlockType.values()) {
            Optional<Class<? extends BlockEntity>> beClass =
                blockType.getBlockEntityClass();
            if (
                beClass.isPresent() &&
                beClass.get().isAssignableFrom(entityClass)
            ) {
                return blockType;
            }
        }

        return ChunkerVanillaBlockType.CHEST;
    }
}
