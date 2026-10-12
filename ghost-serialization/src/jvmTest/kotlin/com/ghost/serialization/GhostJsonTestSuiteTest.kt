package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.proto.wkt.ProtoValue
import okio.Buffer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs the vendored JSONTestSuite (https://github.com/nst/JSONTestSuite, MIT) through the string, bytes and
 * streaming channels, in lenient and in strict mode, decoding into [ProtoValue] (a generic JSON tree).
 *
 * - `y_*` must be accepted and `n_*` rejected by every channel in both modes.
 * - `i_*` are implementation-defined; the sets below pin what Ghost does today so a change is a conscious one.
 *   Ghost rejects lone-surrogate escapes and numbers that overflow a `Double`, and accepts out-of-range integers
 *   (as a `Double`), invalid UTF-8 inside strings, UTF-16 input and a leading BOM.
 */
class GhostJsonTestSuiteTest {

    private enum class Outcome { ACCEPTED, REJECTED, CRASHED }

    private val directory = File(javaClass.getResource("/json-test-suite/test_parsing")!!.toURI())

    private val rejectedImplementationDefined = setOf(
        "i_number_huge_exp.json",
        "i_number_neg_int_huge_exp.json",
        "i_number_pos_double_huge_exp.json",
        "i_number_real_neg_overflow.json",
        "i_number_real_pos_overflow.json",
        "i_object_key_lone_2nd_surrogate.json",
        "i_string_1st_surrogate_but_2nd_missing.json",
        "i_string_1st_valid_surrogate_2nd_invalid.json",
        "i_string_incomplete_surrogate_and_escape_valid.json",
        "i_string_incomplete_surrogate_pair.json",
        "i_string_incomplete_surrogates_escape_valid.json",
        "i_string_invalid_lonely_surrogate.json",
        "i_string_invalid_surrogate.json",
        "i_string_inverted_surrogates_U+1D11E.json",
        "i_string_lone_second_surrogate.json",
        "i_structure_500_nested_arrays.json",
    )

    private val acceptedImplementationDefined = setOf(
        "i_number_double_huge_neg_exp.json",
        "i_number_real_underflow.json",
        "i_number_too_big_neg_int.json",
        "i_number_too_big_pos_int.json",
        "i_number_very_big_negative_int.json",
        "i_string_UTF-16LE_with_BOM.json",
        "i_string_UTF-8_invalid_sequence.json",
        "i_string_UTF8_surrogate_U+D800.json",
        "i_string_invalid_utf-8.json",
        "i_string_iso_latin_1.json",
        "i_string_lone_utf8_continuation_byte.json",
        "i_string_not_in_unicode_range.json",
        "i_string_overlong_sequence_2_bytes.json",
        "i_string_overlong_sequence_6_bytes.json",
        "i_string_overlong_sequence_6_bytes_null.json",
        "i_string_truncated-utf-8.json",
        "i_string_utf16BE_no_BOM.json",
        "i_string_utf16LE_no_BOM.json",
        "i_structure_UTF-8_BOM_empty_object.json",
    )

    private fun outcome(block: () -> Any?): Outcome = try {
        block()
        Outcome.ACCEPTED
    } catch (e: GhostJsonException) {
        Outcome.REJECTED
    } catch (e: Throwable) {
        Outcome.CRASHED
    }

    /** Outcome of every channel for [file]; the string channel is skipped when the bytes are not valid UTF-8. */
    private fun outcomes(file: File): Map<String, Outcome> {
        val bytes = file.readBytes()
        val text = bytes.decodeToString()
        val result = LinkedHashMap<String, Outcome>()
        if (text.encodeToByteArray().contentEquals(bytes)) {
            result["string"] = outcome { Ghost.deserialize<ProtoValue>(json = text) }
            result["string-strict"] = outcome { Ghost.deserialize<ProtoValue>(json = text) { it.strictMode = true } }
        }
        result["bytes"] = outcome { Ghost.deserialize<ProtoValue>(bytes = bytes) }
        result["bytes-strict"] = outcome { Ghost.deserialize<ProtoValue>(bytes = bytes) { it.strictMode = true } }
        result["stream"] = outcome { Ghost.deserializeStreaming<ProtoValue>(source = Buffer().write(bytes)) }
        result["stream-strict"] = outcome {
            Ghost.deserialize<ProtoValue>(source = Buffer().write(bytes)) { it.strictMode = true }
        }
        return result
    }

    private fun files(prefix: String): List<File> =
        directory.listFiles()!!.filter { it.name.startsWith(prefix) }.sortedBy { it.name }

    private fun assertEveryChannel(prefix: String, expected: Outcome) {
        val wrong = files(prefix).mapNotNull { file ->
            val bad = outcomes(file).filterValues { it != expected }
            if (bad.isEmpty()) null else "${file.name} $bad"
        }
        assertEquals(expected = emptyList(), actual = wrong, message = "expected $expected on every channel")
    }

    @Test
    fun everyAcceptCaseIsAccepted() {
        assertEveryChannel(prefix = "y_", expected = Outcome.ACCEPTED)
    }

    @Test
    fun everyRejectCaseIsRejected() {
        assertEveryChannel(prefix = "n_", expected = Outcome.REJECTED)
    }

    @Test
    fun implementationDefinedCasesAreStableAndChannelsAgree() {
        val known = rejectedImplementationDefined + acceptedImplementationDefined
        assertEquals(
            expected = emptyList(),
            actual = files("i_").map { it.name }.filter { it !in known },
            message = "new i_ case: classify it in the sets above"
        )
        val problems = files("i_").mapNotNull { file ->
            val expected = if (file.name in rejectedImplementationDefined) Outcome.REJECTED else Outcome.ACCEPTED
            val bad = outcomes(file).filterValues { it != expected }
            if (bad.isEmpty()) null else "${file.name} expected=$expected got=$bad"
        }
        assertEquals(expected = emptyList(), actual = problems)
    }

    @Test
    fun suiteIsComplete() {
        assertEquals(expected = 318, actual = directory.listFiles()!!.size)
    }
}
