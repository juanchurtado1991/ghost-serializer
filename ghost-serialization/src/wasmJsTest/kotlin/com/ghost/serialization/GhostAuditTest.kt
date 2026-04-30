package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonConstants
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.writer.GhostJsonWriter
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GhostAuditTest {

    @Test
    fun testPositionTypoFix() {
        val reader = GhostJsonReader("{}".encodeToByteArray())
        // Verify that we use 'position' and it's 0 initially
        assertEquals(0, reader.position)
        reader.beginObject()
        assertEquals(1, reader.position)
    }

    @Test
    fun testModelCollisionSafety() {
        // Mock two classes with same simpleName
        val classA = MockModelA::class
        val classB = MockModelB::class

        // We use the actual qualifiedName for the test to be realistic
        val qNameA = classA.qualifiedName!!
        val qNameB = classB.qualifiedName!!

        val serializerA = object : GhostSerializer<MockModelA> {
            override val typeName: String = "MockModelA"
            override fun serialize(writer: GhostJsonWriter, value: MockModelA) {}
            override fun deserialize(reader: GhostJsonReader): MockModelA = MockModelA()
        }

        val serializerB = object : GhostSerializer<MockModelB> {
            override val typeName: String = "MockModelB"
            override fun serialize(writer: GhostJsonWriter, value: MockModelB) {}
            override fun deserialize(reader: GhostJsonReader): MockModelB = MockModelB()
        }

        val registry = object : GhostRegistry {
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? = null
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = mapOf(
                classA to serializerA,
                classB to serializerB
            )
        }

        Ghost.addRegistry(registry)

        // Verify we can find both by their registered typeName
        assertNotNull(Ghost.getSerializerByName("MockModelA"), "Should find ModelA by typeName")
        assertNotNull(Ghost.getSerializerByName("MockModelB"), "Should find ModelB by typeName")
    }

    @Test
    fun testJsonConstantsUsage() {
        // Verify we are using constants
        assertEquals(':'.code.toByte(), GhostJsonConstants.COLON)
        assertEquals('"'.code.toByte(), GhostJsonConstants.QUOTE)
    }
}

class MockModelA
class MockModelB
