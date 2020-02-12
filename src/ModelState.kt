import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPVariable
import lib.Multiset
import lib.MutableMultiset

interface ModelState<AGENT> {
    val consequencesFootprint: Set<AGENT>
    val sources: MutableMultiset<AGENT>
//    val eventIndicators: Map<Event<AGENT>,MPVariable>
//    val footprintIndicators: Map<AGENT,MPVariable>
//    fun addConsequencesToConstraint(constraint: MPConstraint, agent: AGENT, multiplier: Int)

    fun addConsequencesToConstraints(constraints: Map<AGENT, MPConstraint>, multiplier: Double)

    fun addSourcesToConstraints(constraints: Map<AGENT, MPConstraint>, multiplier: Double, equality: Boolean) {
        constraints.forEach { (agent, constraint) ->
            constraint.setBounds(
                multiplier * sources.count(agent),
                if(equality) multiplier * sources.count(agent) else Double.POSITIVE_INFINITY
            )
        }
    }

}
