
class Prey : Agent {
    constructor(pos: Int) : super(pos)

    override fun copyAt(pos: Int) = Prey(pos)

    fun hamiltonian(h: Hamiltonian<Agent>, params: Params) {
        reproduce(h, params)
        diffuse(h, params.GRIDSIZE, params.preyDiffuse)
        die(h, params.preyDie)
        beEaten(h, params)
        beEatenAndReplaced(h, params)
    }

    fun reproduce(h: Hamiltonian<Agent>, params: Params) {
        if(params.preyReproduce > 0.0) {
            h += action(params.preyReproduce / 4.0, Prey(left(params.GRIDSIZE)), this)
            h += action(params.preyReproduce / 4.0, Prey(right(params.GRIDSIZE)), this)
            h += action(params.preyReproduce / 4.0, Prey(up(params.GRIDSIZE)), this)
            h += action(params.preyReproduce / 4.0, Prey(down(params.GRIDSIZE)), this)
        }
    }

    fun beEaten(h: Hamiltonian<Agent>, params: Params) {
        if(params.predCaptureOnly > 0.0) {
            h += interaction(params.predCaptureOnly, Predator(pos))
            h += interaction(params.predCaptureOnly, Predator(right(params.GRIDSIZE)))
            h += interaction(params.predCaptureOnly, Predator(left(params.GRIDSIZE)))
            h += interaction(params.predCaptureOnly, Predator(up(params.GRIDSIZE)))
            h += interaction(params.predCaptureOnly, Predator(down(params.GRIDSIZE)))
        }
    }

    fun beEatenAndReplaced(h: Hamiltonian<Agent>, params: Params) {
        if(params.predCaptureAndReproduce > 0.0) {
            h += interaction(params.predCaptureAndReproduce, Predator(right(params.GRIDSIZE)), Predator(pos))
            h += interaction(params.predCaptureAndReproduce, Predator(left(params.GRIDSIZE)), Predator(pos))
            h += interaction(params.predCaptureAndReproduce, Predator(up(params.GRIDSIZE)), Predator(pos))
            h += interaction(params.predCaptureAndReproduce, Predator(down(params.GRIDSIZE)), Predator(pos))
        }
    }

    override fun toString() = "r($pos)"

    override fun hashCode() = pos*2

    override fun equals(other: Any?) = ((other is Prey) && (pos == other.pos))
}