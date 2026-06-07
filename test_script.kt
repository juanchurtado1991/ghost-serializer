class TestReader(var rawData: String) {
    var rawChars = CharArray(0)
    init {
        if (rawData.isNotEmpty()) reset(rawData)
    }
    fun reset(newData: String) {
        val needed = newData.length
        if (rawChars.size >= needed) {
            println("reused")
        } else {
            val newChars = CharArray((needed * 5) ushr 2)
            println("allocated ${newChars.size}")
            this.rawChars = newChars
        }
    }
}
fun main() {
    val r = TestReader("\"false\"")
    println(r.rawChars.size)
}
