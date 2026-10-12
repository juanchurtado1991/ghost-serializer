package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import okio.Buffer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Every wire key lands in its own field, in every spelling, and near-miss keys land nowhere. */
class KeyDispatchHuntTest {

    private val keys: List<Pair<String, (KeyDispatchModel) -> Int?>> = listOf(
        "a" to { m -> m.p0 },
        "ab" to { m -> m.p1 },
        "abc" to { m -> m.p2 },
        "abcd" to { m -> m.p3 },
        "abcdefg" to { m -> m.p4 },
        "abcdefgh" to { m -> m.p5 },
        "abcdefghi" to { m -> m.p6 },
        "abcdefghij" to { m -> m.p7 },
        "abcdefghijklmnopq" to { m -> m.p8 },
        "id" to { m -> m.p9 },
        "Id" to { m -> m.p10 },
        "ID" to { m -> m.p11 },
        "idx" to { m -> m.p12 },
        "name" to { m -> m.p13 },
        "Name" to { m -> m.p14 },
        "na" to { m -> m.p15 },
        "ne" to { m -> m.p16 },
        "x1" to { m -> m.p17 },
        "x2" to { m -> m.p18 },
        "x10" to { m -> m.p19 },
        "x11" to { m -> m.p20 },
        "keyOne" to { m -> m.p21 },
        "keyTwo" to { m -> m.p22 },
        "A" to { m -> m.p23 },
        "B" to { m -> m.p24 },
        "key-1" to { m -> m.p25 },
        "key_1" to { m -> m.p26 },
        "a.b" to { m -> m.p27 },
        "kéy" to { m -> m.p28 },
        "日本" to { m -> m.p29 },
        "😀" to { m -> m.p30 },
        "with space" to { m -> m.p31 },
        "q\"q" to { m -> m.p32 },
        "a/b" to { m -> m.p33 },
        "long_key_name_that_is_over_thirty_two_chars_long" to { m -> m.p34 },
    )

    private fun decodeAll(json: String): List<KeyDispatchModel> = listOf(
        Ghost.deserialize<KeyDispatchModel>(json = json),
        Ghost.deserialize<KeyDispatchModel>(bytes = json.encodeToByteArray()),
        Ghost.deserializeStreaming<KeyDispatchModel>(source = Buffer().writeUtf8(string = json)),
        Ghost.deserialize<KeyDispatchModel>(bytes = json.encodeToByteArray()) { it.strictMode = false },
    )

    private fun jsonEscaped(key: String, escapeAll: Boolean): String {
        val sb = StringBuilder()
        for (c in key) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                escapeAll || c.code > 0x7e -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun rawKey(key: String) = key.replace("\\\"", "\"")

    @Test
    fun everyKeyHitsOnlyItsOwnField() {
        keys.forEachIndexed { index, (key, _) ->
            val wire = rawKey(key)
            listOf(false, true).forEach { escapeAll ->
                val json = "{\"" + jsonEscaped(wire, escapeAll) + "\":" + (index + 1) + "}"
                decodeAll(json).forEachIndexed { channel, model ->
                    keys.forEachIndexed { other, (_, getter) ->
                        assertEquals(
                            expected = if (other == index) index + 1 else null,
                            actual = getter(model),
                            message = "key '$wire' (escapeAll=$escapeAll, channel=$channel) field #$other json=$json"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun nearMissKeysAreIgnored() {
        val known = keys.map { rawKey(it.first) }.toSet()
        keys.forEach { (key, _) ->
            val wire = rawKey(key)
            val misses = listOf(wire + "x", wire.dropLast(1), "x" + wire, wire.uppercase(), wire.lowercase(), wire + wire)
                .filter { it !in known && it.isNotEmpty() && !it.last().isHighSurrogate() && !it.first().isLowSurrogate() }
            misses.forEach { miss ->
                val json = "{\"" + jsonEscaped(miss, false) + "\":7}"
                decodeAll(json).forEachIndexed { channel, model ->
                    keys.forEachIndexed { other, (_, getter) ->
                        assertEquals(
                            expected = null,
                            actual = getter(model),
                            message = "near-miss '$miss' (channel=$channel) leaked into field #$other"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun allKeysTogetherInShuffledOrderWithDuplicates() {
        val order = keys.indices.shuffled(kotlin.random.Random(11))
        val json = "{" + (order + order.take(5)).joinToString(",") { i ->
            "\"" + jsonEscaped(rawKey(keys[i].first), false) + "\":" + (i + 100)
        } + "}"
        decodeAll(json).forEachIndexed { channel, model ->
            keys.forEachIndexed { i, (key, getter) ->
                assertEquals(expected = i + 100, actual = getter(model), message = "key '$key' channel=$channel")
            }
        }
    }
}
