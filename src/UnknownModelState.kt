import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import lib.Multiset
import lib.MutableMultiset

interface UnknownModelState<AGENT> {
    val potentialConsequencesFootprint: Set<AGENT>
    val committedConsequences: Multiset<AGENT>

    fun getEndStateVriable(solver: MPSolver, agent: AGENT): MPVariable

    fun getEndStateBinaryVriable(solver: MPSolver, agent: AGENT): MPVariable
//    fun hasIndicators(): Boolean

//    fun addConsequencesToConstraints(constraints: Map<AGENT, MPConstraint>, multiplier: Double)

//    fun addSourcesToConstraints(constraints: Map<AGENT, MPConstraint>, multiplier: Double, equality: Boolean) {
//        constraints.forEach { (agent, constraint) ->
//            constraint.setBounds(
//                multiplier * sources.count(agent),
//                if(equality) multiplier * sources.count(agent) else Double.POSITIVE_INFINITY
//            )
//        }
//    }

}
