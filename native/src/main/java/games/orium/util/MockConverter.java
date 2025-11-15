package games.orium.util;

import com.hivemc.chunker.conversion.WorldConverter;
import com.hivemc.chunker.conversion.intermediate.level.ChunkerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class MockConverter extends WorldConverter {
    public MockConverter(@Nullable ChunkerLevel mockLevel) {
        super(UUID.randomUUID());
        level = mockLevel;
    }
}
