package games.orium.conversion;

import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import games.orium.cache.CacheManager;
import games.orium.cache.ResolverCache;
import games.orium.util.Edition;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;

public class ConversionService {

    @FunctionalInterface
    public interface ConversionFunction {
        CompoundTag convert(
            ResolverCache cache,
            Edition fromEdition,
            Edition toEdition,
            CompoundTag data
        ) throws Exception;
    }

    public static CCharPointer processConversion(
        CCharPointer base64Input,
        ConversionFunction conversionLogic
    ) {
        String inputStr = CTypeConversion.toJavaString(base64Input);
        CompoundTag resultNbt = new CompoundTag();

        try {
            byte[] nbtBytes = Base64.getDecoder().decode(inputStr);
            CompoundTag inputNbt = Tag.readBedrockNBT(nbtBytes);

            String fromVersion = inputNbt.getString("fromVersion", "1.20.4");
            String toVersion = inputNbt.getString("toVersion", "1.20.80");
            String fromEditionStr = inputNbt.getString("fromEdition", "java");
            String toEditionStr = inputNbt.getString("toEdition", "bedrock");
            CompoundTag dataToConvert = inputNbt.getCompound("data");

            if (dataToConvert == null) {
                throw new IllegalArgumentException(
                    "Missing 'data' field in input NBT"
                );
            }

            Edition fromEdition = Edition.fromString(fromEditionStr);
            Edition toEdition = Edition.fromString(toEditionStr);

            ResolverCache cache = CacheManager.getOrCreateCache(
                fromVersion,
                toVersion
            );
            CompoundTag convertedData = conversionLogic.convert(
                cache,
                fromEdition,
                toEdition,
                dataToConvert
            );

            resultNbt.put("success", (byte) 1);
            resultNbt.put("data", convertedData);
        } catch (IOException e) {
            resultNbt.put("success", (byte) 0);
            resultNbt.put("error", "IO Error: " + e.getMessage());
            resultNbt.put("stackTrace", getStackTrace(e));
        } catch (Exception e) {
            resultNbt.put("success", (byte) 0);
            resultNbt.put(
                "error",
                e.getMessage()
            );
            resultNbt.put("stackTrace", getStackTrace(e));
        }

        try {
            byte[] outputBytes = Tag.writeBedrockNBT(resultNbt);
            String base64Output = Base64.getEncoder().encodeToString(
                outputBytes
            );
            return toCCharPointer(base64Output);
        } catch (IOException e) {
            try {
                CompoundTag errorNbt = new CompoundTag();
                errorNbt.put("success", (byte) 0);
                errorNbt.put(
                    "error",
                    "Serialization failed: " + e.getMessage()
                );
                errorNbt.put("stackTrace", getStackTrace(e));

                byte[] errorBytes = Tag.writeBedrockNBT(errorNbt);
                String errorBase64 = Base64.getEncoder().encodeToString(
                    errorBytes
                );
                return toCCharPointer(errorBase64);
            } catch (Exception fallbackError) {
                String fallbackMsg = "FATAL: Double serialization failure";
                String fallbackBase64 = Base64.getEncoder().encodeToString(
                    fallbackMsg.getBytes()
                );
                return toCCharPointer(fallbackBase64);
            }
        }
    }

    private static String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private static CCharPointer toCCharPointer(String javaString) {
        byte[] stringBytes = javaString.getBytes(
            java.nio.charset.StandardCharsets.UTF_8
        );
        UnsignedWord length = WordFactory.unsigned(stringBytes.length + 1); // +1 for null terminator
        CCharPointer result = UnmanagedMemory.malloc(length);

        for (int i = 0; i < stringBytes.length; i++) {
            result.write(i, stringBytes[i]);
        }
        result.write(stringBytes.length, (byte) 0); // Null terminator
        return result;
    }
}
