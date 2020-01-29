import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPVariable
import lib.HashMultiset
import lib.Multiset
import lib.MutableMultiset

class StartState<AGENT>: ModelState<AGENT> {
    override val sources: MutableMultiset<AGENT>
    override val consequencesFootprint: Set<AGENT>
        get() = sources.distinctSet
    override val indicators: Map<Event<AGENT>, MPVariable>
        get() = emptyMap()

    constructor(state: Multiset<AGENT>) {
        sources = HashMultiset()
        sources.addAll(state)
    }

    override fun addConsequencesToConstraint(constraint: MPConstraint, agent: AGENT, multiplier: Int) {
        constraint.setBounds(-sources.count(agent).toDouble(), Double.POSITIVE_INFINITY)
    }

}