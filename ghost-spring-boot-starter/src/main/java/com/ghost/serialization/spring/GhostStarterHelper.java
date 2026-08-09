package com.ghost.serialization.spring;

import com.ghost.serialization.Ghost_jvmKt;
import com.ghost.serialization.contract.GhostSerializer;
import com.ghost.serialization.writer.bytes.WriterSinkPair;

import java.io.IOException;
import java.io.OutputStream;

public final class GhostStarterHelper {
    public static void writeToStream(Object value, GhostSerializer<Object> serializer, OutputStream out) throws IOException {
        WriterSinkPair pair = Ghost_jvmKt.acquireFlatWriterPair();
        try {
            serializer.serialize(pair.getWriter(), value);
            out.write(pair.getByteWriter().getArray(), 0, pair.getByteWriter().getSize());
        } finally {
            pair.getByteWriter().reset();
        }
    }
}
