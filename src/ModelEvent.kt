import lib.HashMultiset
import lib.Multiset
import java.io.Serializable
import kotlin.math.ln

class ModelEvent<AGENT>: Serializable, HashMultiset<Event<AGENT>>() {
    val consequences: Multiset<AGENT>
        get() {
            val allConsequences = HashMultiset<AGENT>()
            this.forEach {
                allConsequences.addAll(it.consequences)
            }
            return allConsequences
        }

    fun logProb() = this.sumByDouble {  event -> ln(event.rate) }
}