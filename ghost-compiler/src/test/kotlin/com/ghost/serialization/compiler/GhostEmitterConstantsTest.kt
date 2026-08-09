package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.internal.GhostEmitterConstants
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
        assertEquals(
            "TargetSerializer",
            GhostEmitterConstants.STR_ENVELOPE_TARGET_SERIALIZER_SUFFIX
        )
        assertTrue(GhostEmitterConstants.STR_ENVELOPE_PARSE_BYTES_ROUTE.contains("deserialize"))
        assertTrue(GhostEmitterConstants.TEMPLATE_ENVELOPE_FIELD_ACCESS.contains("envelope"))
    }

    @Test
    fun yamlPackageConstants_matchRuntimeLayout() {
        assertEquals(
            "com.ghost.serialization.parser.yaml",
            GhostEmitterConstants.PKG_YAML_PARSER
        )
        assertEquals(
            "com.ghost.serialization.writer.yaml",
            GhostEmitterConstants.PKG_YAML_WRITER
        )
        assertTrue(GhostEmitterConstants.STR_YAML_SERIALIZER_FQN.endsWith("GhostYamlSerializer"))
    }

    @Test
    fun customDecoderTemplates_useParserPackageConstants() {
        val constants = GhostEmitterConstants
        assertTrue(constants.STR_CUSTOM_DECODER_TEMP_READER.contains(constants.STR_GHOST_JSON_READER_QUALIFIED))
        assertTrue(constants.STR_CUSTOM_DECODER_TEMP_READER_STRING.contains(constants.STR_ENSURE_UTF8_BYTES))
        assertTrue(constants.STR_RESET_TOKEN_BYTE_CALL.contains("${constants.PKG_PARSER_COMMON}.GhostJsonConstants"))
        assertTrue(constants.STR_CUSTOM_DECODER_UPDATE_POS_STRING.contains(constants.STR_BYTE_POSITION_TO_CHAR_POSITION))
    }

    @Test
    fun chunkSize_sharesSingleMagicWithPropertyMax() {
        assertEquals(GhostEmitterConstants.PROPERTY_MAX_SIZE, GhostEmitterConstants.DEFAULT_CHUNK_SIZE)
        assertEquals(40, GhostEmitterConstants.PROPERTY_MAX_SIZE)
    }

    @Test
    fun canonicalEmitterTemplates_haveExpectedLiterals() {
        val c = GhostEmitterConstants
        assertEquals("writer.value(%L)", c.TEMPLATE_WRITER_VALUE)
        assertEquals("writer.nullValue()", c.STR_WRITER_NULL_VAL)
        assertEquals("writer.writeNameRaw(%L)", c.STR_WRITE_NAME_RAW)
        assertEquals("%L", c.TEMPLATE_L)
        assertEquals("name", c.NAME)
        assertEquals("GhostSerialization", c.ANNOTATION_GHOST_SERIALIZATION)
        assertEquals("Ghost", c.STR_GHOST)
        assertEquals("com.ghost.serialization.contract", c.PKG_CONTRACT)
        assertEquals("return result", c.STR_RETURN_RESULT)
        assertEquals("reader", c.STR_READER)
        assertEquals("%S", c.STR_FORMAT_S)
    }
}
