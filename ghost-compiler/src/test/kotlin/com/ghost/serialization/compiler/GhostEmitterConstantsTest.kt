package com.ghost.serialization.compiler

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostEmitterConstantsTest {

    @Test
    fun registryNaming_isStable() {
        assertEquals("GhostModuleRegistry", GhostEmitterConstants.STR_REGISTRY_PREFIX)
        assertEquals("Default", GhostEmitterConstants.STR_DEFAULT_NAME)
        assertEquals("_Test", GhostEmitterConstants.STR_TEST_SUFFIX)
    }

    @Test
    fun annotationFqn_matchesRuntime() {
        assertTrue(GhostEmitterConstants.STR_ANNOTATION_SERIALIZATION.contains("GhostSerialization"))
        assertTrue(GhostEmitterConstants.STR_GENERATED_PKG.startsWith("com.ghost.serialization"))
    }

    @Test
    fun envelopeEmitterTemplates_areCentralized() {
        assertEquals("TargetSerializer", GhostEmitterConstants.STR_ENVELOPE_TARGET_SERIALIZER_SUFFIX)
        assertTrue(GhostEmitterConstants.STR_ENVELOPE_PARSE_BYTES_ROUTE.contains("deserialize"))
        assertTrue(GhostEmitterConstants.TEMPLATE_ENVELOPE_FIELD_ACCESS.contains("envelope"))
    }

    @Test
    fun customDecoderTemplates_useParserPackageConstants() {
        val constants = GhostEmitterConstants
        assertTrue(constants.STR_CUSTOM_DECODER_TEMP_READER.contains(constants.STR_GHOST_JSON_READER_QUALIFIED))
        assertTrue(constants.STR_CUSTOM_DECODER_TEMP_READER_STRING.contains(constants.STR_ENSURE_UTF8_BYTES))
        assertTrue(constants.STR_RESET_TOKEN_BYTE_CALL.contains(constants.STR_GHOST_JSON_FLAT_READER_QUALIFIED))
        assertTrue(constants.STR_CUSTOM_DECODER_UPDATE_POS_STRING.contains(constants.STR_BYTE_POSITION_TO_CHAR_POSITION))
    }
}
