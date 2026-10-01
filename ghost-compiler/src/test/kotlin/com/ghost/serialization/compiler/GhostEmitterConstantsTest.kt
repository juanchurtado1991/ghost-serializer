package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants
import com.ghost.serialization.compiler.internal.GhostCodegenConstants
import com.ghost.serialization.compiler.internal.GhostCommonConstants
import com.ghost.serialization.compiler.internal.GhostEmitterConstants
import com.ghost.serialization.compiler.internal.GhostProcessorConstants
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostEmitterConstantsTest {

    @Test
    fun registryNaming_isStable() {
        assertEquals(expected = "GhostModuleRegistry", actual = GhostProcessorConstants.STR_REGISTRY_PREFIX)
        assertEquals(expected = "Default", actual = GhostProcessorConstants.STR_DEFAULT_NAME)
        assertEquals(expected = "_Test", actual = GhostProcessorConstants.STR_TEST_SUFFIX)
    }

    @Test
    fun annotationFqn_matchesRuntime() {
        assertTrue(actual = GhostCommonConstants.STR_ANNOTATION_SERIALIZATION.contains("GhostSerialization"))
        assertTrue(actual = GhostCommonConstants.STR_GENERATED_PKG.startsWith("com.ghost.serialization"))
    }

    @Test
    fun envelopeEmitterTemplates_areCentralized() {
        assertEquals(
            expected = "TargetSerializer",
            actual = GhostEmitterConstants.STR_ENVELOPE_TARGET_SERIALIZER_SUFFIX
        )
        assertTrue(actual = GhostEmitterConstants.STR_ENVELOPE_PARSE_BYTES_ROUTE.contains("deserialize"))
        assertTrue(actual = GhostEmitterConstants.TEMPLATE_ENVELOPE_FIELD_ACCESS.contains("envelope"))
    }

    @Test
    fun yamlPackageConstants_matchRuntimeLayout() {
        assertEquals(
            expected = "com.ghost.serialization.parser.yaml",
            actual = GhostCommonConstants.PKG_YAML_PARSER
        )
        assertEquals(
            expected = "com.ghost.serialization.writer.yaml",
            actual = GhostCommonConstants.PKG_YAML_WRITER
        )
        assertTrue(actual = GhostProcessorConstants.STR_YAML_SERIALIZER_FQN.endsWith("GhostYamlSerializer"))
    }

    @Test
    fun customDecoderTemplates_useParserPackageConstants() {
        val constants = GhostEmitterConstants
        assertTrue(actual = GhostEmitterConstants.STR_CUSTOM_DECODER_TEMP_READER.contains(GhostAnalyzerConstants.STR_GHOST_JSON_READER_QUALIFIED))
        assertTrue(actual = GhostEmitterConstants.STR_CUSTOM_DECODER_TEMP_READER_STRING.contains(GhostCodegenConstants.STR_ENSURE_UTF8_BYTES))
        assertTrue(actual = GhostEmitterConstants.STR_RESET_TOKEN_BYTE_CALL.contains("${GhostCommonConstants.PKG_PARSER_COMMON_CONSTANTS}.GhostJsonScanConstants"))
        assertTrue(actual = GhostEmitterConstants.STR_CUSTOM_DECODER_UPDATE_POS_STRING.contains(GhostCodegenConstants.STR_BYTE_POSITION_TO_CHAR_POSITION))
    }

    @Test
    fun chunkSize_sharesSingleMagicWithPropertyMax() {
        assertEquals(
            expected = GhostEmitterConstants.PROPERTY_MAX_SIZE,
            actual = GhostEmitterConstants.DEFAULT_CHUNK_SIZE
        )
        assertEquals(expected = 40, actual = GhostEmitterConstants.PROPERTY_MAX_SIZE)
    }

    @Test
    fun canonicalEmitterTemplates_haveExpectedLiterals() {
        val c = GhostEmitterConstants
        assertEquals(expected = "writer.value(%L)", actual = GhostEmitterConstants.TEMPLATE_WRITER_VALUE)
        assertEquals(expected = "writer.nullValue()", actual = GhostEmitterConstants.STR_WRITER_NULL_VAL)
        assertEquals(expected = "writer.writeNameRaw(%L)", actual = GhostEmitterConstants.STR_WRITE_NAME_RAW)
        assertEquals(expected = "%L", actual = GhostEmitterConstants.TEMPLATE_L)
        assertEquals(expected = "name", actual = GhostAnalyzerConstants.NAME)
        assertEquals(expected = "GhostSerialization", actual = GhostCommonConstants.ANNOTATION_GHOST_SERIALIZATION)
        assertEquals(expected = "Ghost", actual = GhostCodegenConstants.STR_GHOST)
        assertEquals(expected = "com.ghost.serialization.contract", actual = GhostCommonConstants.PKG_CONTRACT)
        assertEquals(expected = "return result", actual = GhostEmitterConstants.STR_RETURN_RESULT)
        assertEquals(expected = "reader", actual = GhostEmitterConstants.STR_READER)
        assertEquals(expected = "%S", actual = GhostProcessorConstants.STR_FORMAT_S)
    }
}
