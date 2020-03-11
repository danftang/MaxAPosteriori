import lib.*
import java.io.Serializable
import kotlin.math.ln
import kotlin.random.Random

class EventTrajectory<AGENT>: Serializable, ArrayList<ModelEvent<AGENT>> {

    constructor(): super()

    constructor(initialCapacity: Int): super(initialCapacity)

    fun toStateTrajectory(): List<Multiset<AGENT>> {
        return this.map { it.consequences() }
    }


//    fun initialPrimaryRequirements(): Multiset<AGENT> {
//        if(size == 0) return emptyMultiset()
//        return first().primaryRequirements()
//    }


    fun generateObservations(pObserve: Double): List<Multiset<AGENT>> {
        return this.map { modelEvent ->
            val observedState = HashMultiset<AGENT>()
            modelEvent.consequences.filterTo(observedState) { Random.nextDouble() < pObserve }
            observedState
        }
    }


    fun logProb() = sumByDouble { it.logProb() }
}