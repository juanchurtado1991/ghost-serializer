package com.ghost.serialization

import com.ghost.serialization.proto.wkt.ProtoValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.Buffer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Decodes every file listed in the file named by `GHOST_CORPUS_LIST` (one path per line) into a generic tree on
 * the string, bytes and streaming channels and compares acceptance and content with kotlinx.serialization.
 * Skipped when the variable is unset, so it only runs where a corpus is provided.
 */
class GhostRealWorldCorpusTest {

    private val maxFileBytes = 16 * 1024 * 1024
    private val findingsPerCategory = 6

    private fun JsonElement.toProtoValue(): ProtoValue = when (this) {
        is JsonNull -> ProtoValue.Null
        is JsonObject -> ProtoValue.Struct(mapValues { it.value.toProtoValue() })
        is JsonArray -> ProtoValue.List(map { it.toProtoValue() })
        is JsonPrimitive -> when {
            isString -> ProtoValue.Str(content)
            content == "true" -> ProtoValue.Bool(true)
            content == "false" -> ProtoValue.Bool(false)
            else -> ProtoValue.Number(content.toDouble())
        }
    }

    private fun attempt(block: () -> Any?): Any? = try {
        block()
    } catch (e: StackOverflowError) {
        "ERR StackOverflow"
    } catch (e: Throwable) {
        "ERR ${e::class.simpleName}"
    }

    private fun isError(result: Any?) = result is String && result.startsWith("ERR")

    @Test
    fun corpusAgreesWithKotlinx() {
        val listPath = System.getenv("GHOST_CORPUS_LIST") ?: return
        val files = File(listPath).readLines().filter { it.isNotBlank() }.map(::File)
            .filter { it.isFile && it.length() in 1..maxFileBytes }
        val counts = HashMap<String, Int>()
        val findings = LinkedHashMap<String, MutableList<String>>()
        fun note(category: String, file: File, detail: String = "") {
            counts.merge(category, 1, Int::plus)
            val list = findings.getOrPut(category) { mutableListOf() }
            if (list.size < findingsPerCategory) list.add("${file.path} $detail".trim())
        }
        var compared = 0
        var skippedInvalidUtf8 = 0
        for (file in files) {
            val bytes = file.readBytes()
            val text = bytes.decodeToString()
            if (!text.encodeToByteArray().contentEquals(bytes)) {
                skippedInvalidUtf8++
                continue
            }
            // RFC 8259 §8.1 lets a parser ignore a leading BOM; Ghost does, kotlinx does not.
            val oracle = attempt { Json.parseToJsonElement(text.removePrefix("\uFEFF")).toProtoValue() }
            val channels = listOf(
                "string" to attempt { Ghost.deserialize<ProtoValue>(json = text) },
                "bytes" to attempt { Ghost.deserialize<ProtoValue>(bytes = bytes) },
                "stream" to attempt { Ghost.deserializeStreaming<ProtoValue>(source = Buffer().write(bytes)) },
            )
            compared++
            if (channels.map { it.second }.toSet().size > 1) {
                note("channels disagree", file, channels.joinToString { "${it.first}=${if (isError(it.second)) it.second else "tree"}" })
            }
            val (_, ghost) = channels[1]
            when {
                isError(oracle) && isError(ghost) -> Unit
                isError(ghost) && !isError(oracle) -> note("ghost rejects, kotlinx accepts", file, ghost.toString())
                !isError(ghost) && isError(oracle) -> note("ghost accepts, kotlinx rejects", file, oracle.toString())
                ghost != oracle -> note("content differs", file)
            }
        }
        println("CORPUS compared=$compared skippedInvalidUtf8=$skippedInvalidUtf8 files=${files.size}")
        counts.entries.sortedBy { it.key }.forEach { (category, count) ->
            println("CORPUS $count  $category")
            findings[category]!!.forEach { println("CORPUS     $it") }
        }
        assertEquals(expected = emptyMap(), actual = counts, message = "corpus disagreements")
    }
}
