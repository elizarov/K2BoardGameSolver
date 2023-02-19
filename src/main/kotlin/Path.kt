import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

data class PathCost(val score: Int, val up: Int = 0, val down: Int = 0, val via: List<Space> = emptyList()) {
    fun dominates(other: PathCost) = up <= other.up && down <= other.down && score >= other.score
    operator fun plus(other: PathCost) =
        PathCost(maxOf(score, other.score), up + other.up, down + other.down, via + other.via)
}

@OptIn(ExperimentalTime::class)
class BoardPaths(board: Board) {
    val paths = HashMap<Space, HashMap<Space, List<PathCost>>>()

    operator fun get(a: Space, b: Space): List<PathCost>? = paths[a]?.get(b)

    private fun put(a: Space, b: Space, costs: List<PathCost>) {
        paths.getOrPut(a) { HashMap() }[b] = costs
    }

    init {
        val time = measureTime {
            for (a in board.spaces) for (b in a.moves) {
                val cost = when {
                    b.row < a.row -> PathCost(b.score, up = b.ent, via = listOf(a))
                    b.row > a.row -> PathCost(a.score, down = b.ent, via = listOf(a))
                    else -> error("Cannot have $a -> $b path on the same board row")
                }
                put(a, b, listOf(cost))
            }
            for (a in board.spaces) {
                put(a, a, listOf(PathCost(a.score)))
            }
            // Floyd's algorithm
            for (b in board.spaces) {
                for (a in board.spaces) {
                    val p1s = get(a, b) ?: continue
                    for (c in board.spaces) {
                        val p2s = get(b, c) ?: continue
                        val res = (get(a, c) ?: emptyList()).toMutableList()
                        for (p1 in p1s) for (p2 in p2s) {
                            val p = p1 + p2
                            if (p.down > maxDownMoveForUp.getOrElse(p.up) { -1 }) continue
                            if (res.any { it.dominates(p) }) continue
                            res.removeAll { p.dominates(it) }
                            res += p
                        }
                        put(a, c, res)
                    }
                }
            }
        }
        println("Found all board paths in ${time.toString(DurationUnit.SECONDS, 3)} sec")
    }
}

fun main() {
    val board = easyBoard
    val paths = BoardPaths(board)
    // find longest
    var maxSize = 0
    for (a in board.spaces) for (b in board.spaces) {
        val p = paths[a, b] ?: continue
        maxSize = maxOf(maxSize, p.size)
    }
    println("Longest path lists have size $maxSize")
    for (a in board.spaces) for (b in board.spaces) {
        val p = paths[a, b] ?: continue
        if (p.size == maxSize) println("$a -> $b: $p")
    }
}