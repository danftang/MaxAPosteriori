//import lib.KDTree
import lib.KDTree
import lib.partitionInPlace
import org.junit.Test
import kotlin.random.Random

class KDTreee2DTests {

    @Test
    fun testPartitionInPlace() {
        val myArray = ArrayList<Double>()
        myArray.addAll(generateSequence { Random.nextDouble() }.take(10))
        println(myArray)
        val kth = myArray.findKthElement(5)
        println(myArray)
        println(kth)

    }

    fun MutableList<Double>.findKthElement(k: Int): Double {
        if(size == 1) return this[0]
        val pivot = this.random()
        val result = partitionInPlace { it < pivot }
        return when {
            result.first.size >= k -> result.first.findKthElement(k)
            result.first.size == k-1 -> pivot
            else -> result.second.findKthElement(k-result.first.size)
        }
    }

    @Test
    fun KDTreeTest() {
        val myTree = KDTree.Manhattan<Double>(2)
        myTree.addPoint(doubleArrayOf(1.0,1.01), 1.234)
        myTree.addPoint(doubleArrayOf(1.1,1.0), 1.235)
        myTree.addPoint(doubleArrayOf(1.001,1.2), 1.236)

        myTree[5.0,4.0] = 4.567

        println(myTree.size)
        val nearest = myTree.nearestNeighbour(1.02, 1.201)
        println(nearest)

        println(myTree.values.toList())
        println(myTree.keys.toList())
        println(myTree.entries.toList())

    }
}