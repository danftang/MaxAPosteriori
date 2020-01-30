import com.google.ortools.linearsolver.MPSolver
import org.junit.Test

class TestMPSolver {
    @Test
    fun testSolve() {
        System.loadLibrary("jniortools")

        val solver = MPSolver("MySolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
        val x = solver.makeIntVar(0.0, 100.0, "x")
        val y = solver.makeIntVar(0.0, 1000.0, "y")

        val c0 = solver.makeConstraint(-1000.0, 17.5, "c0")
        c0.setCoefficient(x, 1.0)
        c0.setCoefficient(y, 7.0)

        val c1 = solver.makeConstraint(-1000.0, 3.5, "c1")
        c1.setCoefficient(x, 1.0)
        c1.setCoefficient(y, 0.0)

        val obj = solver.objective()
        obj.setCoefficient(x, 1.0)
        obj.setCoefficient(y, 10.0)
        obj.setMaximization()

        println("solving for ${solver.numVariables()} variables and ${solver.numConstraints()} constraints")
        val solveState = solver.solve()

        println("solveState = $solveState")

        println("x = ${x.solutionValue()}")
        println("y = ${y.solutionValue()}")
        println("objective = ${obj.value()}")

//        val solver2 = MPSolver("MySolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
//        println("solving for ${solver2.numVariables()} variables and ${solver2.numConstraints()} constraints")

    }

}