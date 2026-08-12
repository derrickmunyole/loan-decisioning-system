package io.github.derrickmunyole.loandecisioning.infrastructure.idempotency;

import io.github.derrickmunyole.loandecisioning.common.Sha256;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Fingerprints the parts of a request that must match for an Idempotency-Key to be replayed. */
public final class RequestHash {

    private RequestHash() {}

    public static String of(String... parts) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (String part : parts) {
            buffer.writeBytes((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
            buffer.write(0);
        }
        return Sha256.hex(buffer.toByteArray());
    }
}
