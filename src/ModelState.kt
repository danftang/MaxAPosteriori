import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPVariable
import lib.Multiset
import lib.MutableMultiset

interface ModelState<AGENT> {
    val consequencesFootprint: Set<AGENT>
    val sources: MutableMultiset<AGENT>
    val indicators: Map<Event<AGENT>,MPVariable>

    fun addConsequencesToConstraint(constraint: MPConstraint, agent: AGENT, multiplier: Int)
}
