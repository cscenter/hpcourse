import java.util.concurrent.atomic.AtomicMarkableReference


private val n1 = LockFreeHashSet<Int>().Node(4.hashCode(), 4, AtomicMarkableReference(null, false))
private val n10 = LockFreeHashSet<Int>().Node(10.hashCode(), 10, AtomicMarkableReference(null, false))
private val n20 = LockFreeHashSet<Int>().Node(20.hashCode(), 20, AtomicMarkableReference(null, false))
private val n30 = LockFreeHashSet<Int>().Node(30.hashCode(), 30, AtomicMarkableReference(null, false))
private val set = LockFreeHashSet<Int>()

fun main(args: Array<String>) {
    n1.nextAndIsDeletePair.set(n10, false)
    n10.nextAndIsDeletePair.set(n20, false)
    n20.nextAndIsDeletePair.set(n30, true)

    println("empty? ${set.isEmpty}")
    set.head.set(n1)
    println("empty? ${set.isEmpty}")

    print(5)
    print(25)
    print(20)
    print(30)
    print(31)
    print(2)

    var curr = set.head.get()
    while (curr != null) {
        print("${curr.value}->")
        curr = curr.nextAndIsDeletePair.reference
    }
    println("null")

    println(set.contains(3))
    println(set.contains(4))
    println(set.contains(5))
    println(set.contains(29))
    println(set.contains(30))
    println(set.contains(31))
}


private fun print(key: Int) {
    val p = set.findNodes(key)
    println("searchedHash: ${key}  key: ${p.first?.key} value: ${p.first?.value}    to    key: ${p.second?.key} value: ${p.second?.value}")
}
