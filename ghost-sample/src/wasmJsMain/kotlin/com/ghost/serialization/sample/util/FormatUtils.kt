package com.ghost.serialization.sample.util

/**
 * Wasm implementation for the sample's limited `%.Nf` format uses.
 */
actual fun String.format(vararg args: Any?): String {
    if (args.isEmpty()) return this
    val first = args[0] as? Number ?: return this
    val double = first.toDouble()
    val precisionMatch = Regex("""%\.(\d+)f""").find(this)
    val precision = precisionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 2
    val factor = tenPow(precision)
    val rounded = kotlin.math.round(double * factor) / factor
    val text = rounded.toString()
    return if (precisionMatch != null) {
        this.replace(precisionMatch.value, text)
    } else {
        text
    }
}

private fun tenPow(n: Int): Double {
    var result = 1.0
    var i = 0
    while (i < n) {
        result *= 10.0
        i++
    }
    return result
}
