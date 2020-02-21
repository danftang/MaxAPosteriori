import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class PredPreyProblem(val observedTrajectory: List<PredPreyModel.ObservedState>, val solver: MAPOrbitSolver<Agent>): Serializable {
    fun saveToFile(filename: String) {
        val outfile = FileOutputStream(filename)
        val objOutStream = ObjectOutputStream(outfile)
        objOutStream.writeObject(this)
    }

    companion object {
        fun loadFromFile(filename: String) {

        }
    }
}