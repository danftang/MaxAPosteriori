package lib

interface MutableMultiset<T>: MutableSet<T>, Multiset<T> {
    override val counts: MutableMap<T,Int>

    fun add(m : T, n : Int) : Boolean
    fun remove(m: T, n: Int): Boolean
}

fun <T>Collection<T>.toMutableMultiset(): MutableMultiset<T> {
    return HashMultiset(this)
}

fun <T>Iterable<T>.toMutableMultiset(): MutableMultiset<T> {
    return HashMultiset(this)
}

fun <T>Array<out T>.toMutableMultiset(): MutableMultiset<T> {
    return HashMultiset(this)
}
