package games.orium.util;

import com.hivemc.chunker.conversion.WorldConverter;
import com.hivemc.chunker.conversion.intermediate.level.ChunkerLevel;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class MockConverter extends WorldConverter {

    public MockConverter(@Nullable ChunkerLevel mockLevel) {
        super(UUID.randomUUID());
        level = mockLevel;
    }
}
