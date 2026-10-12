package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.proto.wkt.ProtoValue
import okio.Buffer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs the original json.org JSON_checker suite (`pass1`-`pass3`, `fail1`-`fail33`) through the string, bytes and
 * streaming channels, in lenient and in strict mode, decoding into [ProtoValue] (a generic JSON tree).
 *
 * Every `pass*` file must be accepted and every `fail*` file rejected, except two that the suite itself ties to
 * the era of its writing and that RFC 8259 and the suite's own notes leave to the implementation:
 * - `fail1` is a bare string at the root; RFC 8259 allows any JSON value there, so Ghost accepts it.
 * - `fail18` nests arrays 20 deep; the depth limit is the implementation's, and Ghost's `maxDepth` is far higher.
 */
class GhostJsonOrgTestSuiteTest {

    private enum class Outcome { ACCEPTED, REJECTED, CRASHED }

    private val directory = File(javaClass.getResource("/json-test-suite/json-org")!!.toURI())

    private val acceptedDespiteFailName = setOf("fail1.json", "fail18.json")

    private fun outcome(block: () -> Any?): Outcome = try {
        block()
        Outcome.ACCEPTED
    } catch (e: GhostJsonException) {
        Outcome.REJECTED
    } catch (e: Throwable) {
        Outcome.CRASHED
    }

    private fun outcomes(file: File): Map<String, Outcome> {
        val bytes = file.readBytes()
        val text = bytes.decodeToString()
        return linkedMapOf(
            "string" to outcome { Ghost.deserialize<ProtoValue>(json = text) },
            "string-strict" to outcome { Ghost.deserialize<ProtoValue>(json = text) { it.strictMode = true } },
            "bytes" to outcome { Ghost.deserialize<ProtoValue>(bytes = bytes) },
            "bytes-strict" to outcome { Ghost.deserialize<ProtoValue>(bytes = bytes) { it.strictMode = true } },
            "stream" to outcome { Ghost.deserializeStreaming<ProtoValue>(source = Buffer().write(bytes)) },
            "stream-strict" to outcome {
                Ghost.deserialize<ProtoValue>(source = Buffer().write(bytes)) { it.strictMode = true }
            },
        )
    }

    private fun expected(file: File): Outcome = when {
        file.name.startsWith("pass") -> Outcome.ACCEPTED
        file.name in acceptedDespiteFailName -> Outcome.ACCEPTED
        else -> Outcome.REJECTED
    }

    @Test
    fun everyFileBehavesAsExpectedOnEveryChannel() {
        val wrong = directory.listFiles()!!.sortedBy { it.name }.mapNotNull { file ->
            val bad = outcomes(file).filterValues { it != expected(file) }
            if (bad.isEmpty()) null else "${file.name} expected=${expected(file)} got=$bad"
        }
        assertEquals(expected = emptyList(), actual = wrong)
    }

    @Test
    fun suiteIsComplete() {
        val names = directory.listFiles()!!.map { it.name }.toSet()
        val expectedNames = (1..3).map { "pass$it.json" } + (1..33).map { "fail$it.json" }
        assertEquals(expected = expectedNames.toSet(), actual = names)
    }
}
