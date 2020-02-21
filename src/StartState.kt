import com.google.ortools.linearsolver.MPConstraint
import lib.HashMultiset
import lib.Multiset
import lib.MutableMultiset
import java.io.Serializable

class StartState<AGENT>: UnknownModelState<AGENT>, Serializable {
    override val sources: MutableMultiset<AGENT>
    override val consequencesFootprint: Set<AGENT>
        get() = sources.supportSet

    constructor(state: Multiset<AGENT>) {
        sources = HashMultiset()
        sources.addAll(state)
    }

    override fun addConsequencesToConstraints(constraints: Map<AGENT, MPConstraint>, multiplier: Double) {
    }

    override fun hasIndicators() = false

}