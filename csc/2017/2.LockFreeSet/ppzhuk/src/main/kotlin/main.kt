fun main(args: Array<String>) {
    val set = LockFreeHashSet<Int>()

    println("empty? ${set.isEmpty}")
    println(set.toString())

    set.add(10)
    set.add(4)
    set.add(30)
    set.add(20)

    println("empty? ${set.isEmpty}")
    println(set.toString())

    fun printSetContains(value: Int) = println("${value}: ${set.contains(value)}")

    printSetContains(4)
    printSetContains(5)
    printSetContains(29)
    printSetContains(30)
    printSetContains(31)
}
