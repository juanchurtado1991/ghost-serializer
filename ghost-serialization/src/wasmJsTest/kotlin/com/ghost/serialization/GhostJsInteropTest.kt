package com.ghost.serialization

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.js.JsAny

// In Kotlin/Wasm, js() must be a single expression in a top-level or member function.
private fun hasTestKey(obj: JsAny): Boolean = js("obj.testKey === 'testValue'")
private fun getArrayLength(arr: JsAny): Int = js("arr.length")
private fun getFirstElement(arr: JsAny): Int = js("arr[0]")
private fun isGhostString(str: JsAny): Boolean = js("str === 'Ghost'")
private fun is100(num: JsAny): Boolean = js("num === 100")
private fun is99_9(dbl: JsAny): Boolean = js("dbl === 99.9")
private fun isTrueBool(bool: JsAny): Boolean = js("bool === true")

class GhostJsInteropTest {

    @Test
    fun testCreateJsObject() {
        val obj = createJsObject()
        // If createJsObject returned undefined (like evaluating "{}"), assertNotNull would fail
        // Wait, assertNotNull works on Kotlin types, JsAny is an external interface but it maps cleanly.
        // It's better to assert it's actually an object and not undefined.
        setJsProperty(obj, "testKey", stringToJs("testValue"))
        assertTrue(hasTestKey(obj), "Property should be correctly set on the JS object")
    }

    @Test
    fun testCreateJsArray() {
        val arr = createJsArray()
        pushJsArray(arr, intToJs(42))
        
        assertEquals(1, getArrayLength(arr), "Array should have length 1 after pushing")
        assertEquals(42, getFirstElement(arr), "First element should be 42")
    }

    @Test
    fun testPrimitives() {
        assertTrue(isGhostString(stringToJs("Ghost")), "String conversion failed")
        assertTrue(is100(intToJs(100)), "Int conversion failed")
        assertTrue(is99_9(doubleToJs(99.9)), "Double conversion failed")
        assertTrue(isTrueBool(boolToJs(true)), "Boolean conversion failed")
    }
}

