package lib

interface MutableMultiset<T>: MutableSet<T>, Multiset<T> {
    override val counts: MutableMap<T, Int>

    fun add(m: T, n: Int): Boolean
    fun remove(m: T, n: Int): Boolean

    fun addAll(multiset: Multiset<T>): Boolean {
        multiset.counts.forEach { counts.merge(it.key, it.value, Int::plus) }
        return true
    }

    override fun addAll(elements: Collection<T>): Boolean {
        var success = true
        elements.forEach {
            success = success && add(it)

        }
        return success
    }

    override fun clear() {
        counts.clear()
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var success = true
        elements.forEach {
            success = success && remove(it)

        }
        return success
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        return retainAll(elements.toMutableMultiset())
    }

    fun retainAll(elements: Multiset<T>): Boolean {
        return counts.entries.removeIf { entry ->
            val toRetain = elements.count(entry.key)
            if (toRetain < entry.value) {
                if (toRetain > 0) {
                    entry.setValue(toRetain)
                    false
                } else {
                    true
                }
            }
            false
        }
    }
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

fun <T>Collection<T>.toMultiset(): Multiset<T> {
    return this.toMutableMultiset()
}

fun <T>Iterable<T>.toMultiset(): Multiset<T> {
    return this.toMutableMultiset()
}

fun <T>Array<out T>.toMultiset(): Multiset<T> {
    return this.toMutableMultiset()
}
