package lib

class HashMultiset<T>(override val counts : MutableMap<T,Int> = HashMap()) : AbstractMutableSet<T>(),
    MutableMultiset<T> {
    override var size : Int = 0

    override fun add(m : T) = add(m,1)


    override fun add(m : T, n : Int) : Boolean {
        counts.merge(m,n,Int::plus)
        size += n
        return true
    }

    val distinctSet: Set<T>
        get() = counts.keys

    constructor(container : Iterable<T>) : this() {
        container.forEach {
            add(it)
        }
    }

    constructor(container : Collection<T>) : this(HashMap(container.size)) {
        container.forEach {
            add(it)
        }
    }

    constructor(array: Array<out T>): this(array.asList())




    override fun remove(element : T) : Boolean {
        return remove(element,1)
    }


    override fun remove(element : T, n : Int) : Boolean {
        var removed = 0
        counts.compute(element) { _, count ->
            if(count == null) null else {
                if(count <= n) {
                    removed = count
                    null
                } else {
                    removed = n
                    count - n
                }
            }
        }
        size -= removed
        return removed == n
    }


    override operator fun iterator() : MutableIterator<T> {
        return MultiMutableIterator(counts.iterator(), this::decrementSize)
    }




//    override fun equals(other: Any?): Boolean {
//        if(other is lib.HashMultiset<*>) {
//            return this.map == other.map
//        }
//        return false
//    }


    override fun toString() = counts.toString()

    private fun decrementSize() {--size}

    ////////////////////////////////////////////////////////
    // overrides of standard algorithms that are quicker
    // than the default implementations
    ////////////////////////////////////////////////////////
    fun elementAt(index : Int) : T {
        var count = index
        val it = counts.iterator()
        var entry : MutableMap.MutableEntry<T,Int>
        do {
            entry = it.next()
            count -= entry.value
        } while(count>=0)
        return entry.key
    }


    override fun contains(element: T): Boolean {
        return counts.containsKey(element)
    }

    class MultiMutableIterator<A>(val mapIterator : MutableIterator<MutableMap.MutableEntry<A,Int>>, val decrementSize : ()->Unit) : MutableIterator<A> {
        private var currentEntry : MutableMap.MutableEntry<A,Int>? = null
        private var nItems : Int = 0

        override fun remove() {
            val entry = currentEntry
            when {
                entry == null       -> throw(NoSuchElementException())
                entry.value == 1    -> mapIterator.remove()
                else                -> entry.setValue(entry.value - 1)
            }
            decrementSize()
        }

        override fun hasNext(): Boolean = mapIterator.hasNext() || nItems > 0

        override fun next(): A {
            if(nItems > 0) {
                --nItems
            } else {
                val nextEntry = mapIterator.next()
                currentEntry = nextEntry
                nItems = nextEntry.value-1
            }
            return currentEntry!!.key
        }

    }

    override fun hashCode(): Int {
        return counts.hashCode()
    }
}


fun <T>hashMultisetOf(vararg elements : T) : HashMultiset<T> {
    return HashMultiset(elements.asList())
}

