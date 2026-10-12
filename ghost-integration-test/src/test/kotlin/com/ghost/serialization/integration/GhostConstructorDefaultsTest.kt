package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostConstructorDefaultsTest {

    @Test
    fun bodyPropertiesAreWrittenWhenStoredAndNeverRequired() {
        assertEquals(
            expected = "{\"base\":1,\"stored\":\"stored\"}",
            actual = Ghost.encodeToString(value = BodyPropertiesModel(base = 1))
        )
        assertEquals(
            expected = BodyPropertiesModel(base = 2),
            actual = Ghost.deserialize<BodyPropertiesModel>(json = "{\"base\":2}")
        )
    }

    @Test
    fun dependentDefaultsAreComputedFromDecodedValues() {
        val json = "{\"base\":5,\"third\":9}"
        val expected = DependentDefaultsModel(base = 5, third = 9)
        listOf(
            Ghost.deserialize<DependentDefaultsModel>(json = json),
            Ghost.deserialize<DependentDefaultsModel>(bytes = json.encodeToByteArray()),
            Ghost.deserializeStreaming<DependentDefaultsModel>(source = Buffer().writeUtf8(string = json))
        ).forEach { decoded ->
            assertEquals(
                expected = expected,
                actual = decoded
            )
        }
    }

    @Test
    fun dependentDefaultsAreComputedFromDecodedValuesInFragmentedModels() {
        val json = "{\"base\":5,\"f42\":9}"
        val expected = DependentDefaultsFragmentedModel(base = 5, f42 = 9)
        listOf(
            Ghost.deserialize<DependentDefaultsFragmentedModel>(json = json),
            Ghost.deserialize<DependentDefaultsFragmentedModel>(bytes = json.encodeToByteArray()),
            Ghost.deserializeStreaming<DependentDefaultsFragmentedModel>(source = Buffer().writeUtf8(string = json))
        ).forEach { decoded ->
            assertEquals(
                expected = expected,
                actual = decoded
            )
        }
    }
}
