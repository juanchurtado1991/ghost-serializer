@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.HierarchyWrappedKeysFixture
import com.ghost.serialization.integration.model.OmitIfAbsentWrappedKeysFixture
import com.ghost.serialization.integration.model.OmitIfEmptyWrappedKeysFixture
import com.ghost.serialization.integration.model.RepeatedWrappedKeysFixture
import com.ghost.serialization.integration.model.WireExtras
import com.ghost.serialization.integration.model.WireExtras12
import com.ghost.serialization.integration.model.WireExtras34
import com.ghost.serialization.integration.model.WrappedKeysFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhostWrappedKeysIntegrationTest {

    @Test
    fun deserializeBasicWrappedKeys() {
        val result = Ghost.deserialize<WrappedKeysFixture>(BASIC_JSON)
        assertEquals(BASIC_MODEL, result)
    }

    @Test
    fun serializeBasicWrappedKeys() {
        val json = Ghost.serialize(BASIC_MODEL)
        val roundTrip = Ghost.deserialize<WrappedKeysFixture>(json)
        assertEquals(BASIC_MODEL, roundTrip)
    }

    @Test
    fun deserializeMissingValuesKeepsNullWrapperFields() {
        val result = Ghost.deserialize<WrappedKeysFixture>(MISSING_VALUES_JSON)
        assertEquals(
            WrappedKeysFixture(
                id = "1",
                extras = WireExtras(null, null, null, null),
            ),
            result,
        )
    }

    @Test
    fun deserializeOmitIfEmptyYieldsNullWrapper() {
        val result = Ghost.deserialize<OmitIfEmptyWrappedKeysFixture>(ALL_NULL_JSON)
        assertEquals(
            OmitIfEmptyWrappedKeysFixture(id = "1", extras = null),
            result,
        )
    }

    @Test
    fun deserializeOmitIfAbsentYieldsNullWrapperWhenTriggerKeyNull() {
        val result = Ghost.deserialize<OmitIfAbsentWrappedKeysFixture>(EXTRA_TWO_NULL_JSON)
        assertEquals(
            OmitIfAbsentWrappedKeysFixture(id = "1", extras = null),
            result,
        )
    }

    @Test
    fun deserializeHierarchyWrappedKeys() {
        val result = Ghost.deserialize<HierarchyWrappedKeysFixture>(BASIC_JSON)
        assertEquals(HierarchyWrappedKeysFixture(wrappedKeysTestClass = BASIC_MODEL), result)
    }

    @Test
    fun serializeHierarchyWrappedKeys() {
        val model = HierarchyWrappedKeysFixture(wrappedKeysTestClass = BASIC_MODEL)
        val roundTrip = Ghost.deserialize<HierarchyWrappedKeysFixture>(Ghost.serialize(model))
        assertEquals(model, roundTrip)
    }

    @Test
    fun deserializeRepeatedWrappedKeys() {
        val result = Ghost.deserialize<RepeatedWrappedKeysFixture>(BASIC_JSON)
        assertEquals(
            RepeatedWrappedKeysFixture(
                id = "1",
                extras12 = WireExtras12(extra1 = "1", extra2 = "2"),
                extras34 = WireExtras34(extra3 = "3", extra4 = "4"),
            ),
            result,
        )
    }

    @Test
    fun serializeOmitIfEmptySkipsWrapperKeys() {
        val model = OmitIfEmptyWrappedKeysFixture(id = "1", extras = null)
        val json = Ghost.serialize(model)
        assertEquals(false, json.contains("extra1"))
        assertEquals(false, json.contains("extra2"))
        val roundTrip = Ghost.deserialize<OmitIfEmptyWrappedKeysFixture>(json)
        assertEquals(model, roundTrip)
    }

    @Test
    fun serializeNullableWrapperFieldOmitsAbsentKey() {
        val model = WrappedKeysFixture(
            id = "1",
            extras = WireExtras(extra1 = "1", extra2 = "2", extra3 = "3", extra4 = null),
        )
        val json = Ghost.serialize(model)
        assertEquals(false, json.contains("extra4"))
    }

    private companion object {
        const val BASIC_JSON = """
            {
              "id": "1",
              "extra1": "1",
              "extra2": "2",
              "extra3": "3",
              "extra4": "4"
            }
        """

        const val MISSING_VALUES_JSON = """
            {
              "id": "1"
            }
        """

        const val ALL_NULL_JSON = """
            {
              "id": "1",
              "extra1": null,
              "extra2": null,
              "extra3": null,
              "extra4": null
            }
        """

        const val EXTRA_TWO_NULL_JSON = """
            {
              "id": "1",
              "extra1": "1",
              "extra2": null,
              "extra3": "3",
              "extra4": "4"
            }
        """

        val BASIC_MODEL = WrappedKeysFixture(
            id = "1",
            extras = WireExtras(
                extra1 = "1",
                extra2 = "2",
                extra3 = "3",
                extra4 = "4",
            ),
        )
    }
}
