import okio.Buffer
import okio.Options
import okio.ByteString.Companion.encodeUtf8

fun main() {
    val b = Buffer().writeUtf8("\"name\": \"value\"")
    b.skip(1) // skip opening quote
    val opts = Options.of("name".encodeUtf8())
    val index = b.select(opts)
    println("Matched index: $index")
    println("Next char: '${b.readUtf8(1)}'")
}
