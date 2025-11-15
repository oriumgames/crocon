package games.orium;

import games.orium.conversion.BiomeConverter;
import games.orium.conversion.BlockConverter;
import games.orium.conversion.BlockEntityConverter;
import games.orium.conversion.ConversionService;
import games.orium.conversion.EntityConverter;
import games.orium.conversion.ItemConverter;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;

public class Crocon {

    /**
     * Convert block identifiers and states between editions.
     */
    @CEntryPoint(name = "convert_block")
    public static CCharPointer convertBlock(
        IsolateThread thread,
        CCharPointer base64Input
    ) {
        return ConversionService.processConversion(
            base64Input,
            BlockConverter::convert
        );
    }

    /**
     * Convert item stacks between editions.
     */
    @CEntryPoint(name = "convert_item")
    public static CCharPointer convertItem(
        IsolateThread thread,
        CCharPointer base64Input
    ) {
        return ConversionService.processConversion(
            base64Input,
            ItemConverter::convert
        );
    }

    /**
     * Convert biome identifiers between editions.
     */
    @CEntryPoint(name = "convert_biome")
    public static CCharPointer convertBiome(
        IsolateThread thread,
        CCharPointer base64Input
    ) {
        return ConversionService.processConversion(
            base64Input,
            BiomeConverter::convert
        );
    }

    /**
     * Convert entities between editions (limited support).
     */
    @CEntryPoint(name = "convert_entity")
    public static CCharPointer convertEntity(
        IsolateThread thread,
        CCharPointer base64Input
    ) {
        return ConversionService.processConversion(
            base64Input,
            EntityConverter::convert
        );
    }

    /**
     * Convert block entities between editions.
     */
    @CEntryPoint(name = "convert_block_entity")
    public static CCharPointer convertBlockEntity(
        IsolateThread thread,
        CCharPointer base64Input
    ) {
        return ConversionService.processConversion(
            base64Input,
            BlockEntityConverter::convert
        );
    }

    /**
     * Free memory allocated by the conversion functions.
     * MUST be called by the caller to free memory returned by convert_* functions.
     */
    @CEntryPoint(name = "free_result")
    public static void freeResult(IsolateThread thread, CCharPointer pointer) {
        if (pointer.isNonNull()) {
            UnmanagedMemory.free(pointer);
        }
    }
}
