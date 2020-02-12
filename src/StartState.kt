import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPVariable
import lib.HashMultiset
import lib.Multiset
import lib.MutableMultiset

class StartState<AGENT>: ModelState<AGENT> {
    override val sources: MutableMultiset<AGENT>
    override val consequencesFootprint: Set<AGENT>
        get() = sources.supportSet

    constructor(state: Multiset<AGENT>) {
        sources = HashMultiset()
        sources.addAll(state)
    }

    override fun addConsequencesToConstraints(constraints: Map<AGENT, MPConstraint>, multiplier: Double) {
    }

}