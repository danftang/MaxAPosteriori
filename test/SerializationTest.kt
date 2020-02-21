import lib.readObject
import lib.writeObject
import org.junit.Test
import java.io.*

class SerializationTest {
    @Test
    fun basicTest() {
        val myMap = mapOf(1 to 1.0, 2 to 2.0)

        File("test.dat").writeObject(myMap)
//        val outfile = FileOutputStream("test.dat")
//        val objOutStream = ObjectOutputStream(outfile)
//        objOutStream.writeObject(myMap)

        println("original $myMap")

//        val inFile = FileInputStream("test.dat")
//        val objInStream = ObjectInputStream(inFile)
//        val myRead = objInStream.readObject() as Map<Int,Double>

        val myRead = File("test.dat").readObject<Map<Int,Double>>()
        println("restored $myRead")
        assert(myMap == myRead)
    }

    @Test
    fun loadSolver() {
        val mySolver = File("solver.dump").readObject<MAPOrbitSolver<Agent>>()
        mySolver.timesteps.forEach {
            println(it.committedEvents)
        }
    }

    @Test
    fun loadProblem() {
        val myProblem = File("problem1.dump").readObject<PredPreyProblem>()
        println("Real trajectory is")
        myProblem.observedTrajectory.forEach {
            println("${it.realState}")
        }
        println("Observed trajectory is")
        myProblem.observedTrajectory.forEach {
            println("${it.observation}")
        }
        println("Solution is")
        myProblem.solver.timesteps.forEach {
            println(it.committedEvents)
        }
    }
}