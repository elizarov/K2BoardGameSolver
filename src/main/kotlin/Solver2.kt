import kotlin.random.Random

class State2(
    val c1: Climber,
    val c2: Climber,
    override val hand: PackedCards,
    override val deck: PackedCards
) : ClimbersState {
    val compositeScore: Int
        get() = (c1.score + c2.score) * 100 + (c1.acc + c2.acc)
    override val climbers: List<Climber>
        get() = listOf(c1, c2)

    override fun equals(other: Any?): Boolean = other is State2 &&
            (c1 == other.c1 && c2 == other.c2 || c1 == other.c2 && c2 == other.c1) &&
            hand == other.hand && deck == other.deck

    override fun hashCode(): Int = ((c1.hashCode() + c2.hashCode()) * 31 + hand.hashCode()) * 31 + deck.hashCode()
    override fun toString(): String = "$c1 $c2 hand=${hand.packedCardsToString()} deck=${deck.packedCardsToString()}"

}

private const val DAYS = 4

private val stateValues2 = Array(DAYS) { HashMap<State2, Int>() }

data class MoveTrace2(
    val cc1: PackedCards,
    val cc2: PackedCards,
    val state: State2
)

fun solveStateValue2(day: Int, state: State2, trace: MutableList<MoveTrace2>?, paths: BoardPaths): Int {
    if (day == DAYS) return state.compositeScore
    if (trace == null) stateValues2[day][state]?.let { return it }
    val nHand = state.hand.countCards()
    if (nHand < HAND_SIZE && state.deck != 0L || nHand == 0) {
        val deck = if (state.deck == 0L) allCards else state.deck
        val sel = Random(day).sampleCardsShuffle(HAND_SIZE - nHand, deck)
        return solveStateValue2(day, State2(state.c1, state.c2, state.hand + sel, deck - sel), trace, paths)
    }
    var best = if (trace == null) 0 else stateValues2[day][state]!!
    var done = false
    chooseCards(USE_CARDS, state.hand) loop1@{ useCards ->
        if (done) return@loop1
        for (nc1 in 0..USE_CARDS) chooseCards(nc1, useCards) loop2@{ cc1 ->
            if (done) return@loop2
            val cc2 = useCards - cc1
            val ms1 = findClimberMoves(state.c1, cc1, paths)
            val ms2 = findClimberMoves(state.c2, cc2, paths)
            for ((s1, t1n, sc1, a1wt) in ms1) for ((s2, t2n, sc2, a2wt) in ms2) {
                val t1 = state.c1.tent ?: t1n
                val t2 = state.c2.tent ?: t2n
                var a1 = a1wt
                var a2 = a2wt
                if (s1 == t1 || s1 == t2) a1++
                if (s2 == t1 || s2 == t2) a2++
                if (a1 >= 1 && a2 >= 1) {
                    val c1 = Climber(s1, t1, maxOf(state.c1.score, sc1), a1.coerceAtMost(MAX_ACC))
                    val c2 = Climber(s2, t2, maxOf(state.c2.score, sc2), a2.coerceAtMost(MAX_ACC))
                    val next = State2(c1, c2, state.hand - useCards, state.deck)
                    val win = solveStateValue2(day + 1, next, null, paths)
                    if (trace == null) {
                        best = maxOf(best, win)
                    } else {
                        if (win == best) {
                            trace.add(MoveTrace2(cc1, cc2, next))
                            solveStateValue2(day + 1, next, trace, paths)
                            done = true
                            return@loop2
                        }
                    }
                }
            }
        }
    }
    stateValues2[day][state] = best
    return best
}

fun main() {
    val board = easyBoard
    val paths = BoardPaths(board)
    println("--- Board ---")
    val c0 = Climber(board.start, null, 1, 1)
    val s0 = State2(c0, c0, 0L, 0L)
    board.printState(s0)
    val score = solveStateValue2(0, s0, null, paths)
    println("Found solution with score $score")
    println("=============")
    val trace = ArrayList<MoveTrace2>()
    solveStateValue2(0, s0, trace, paths)
    for (t in trace) {
        board.printState(t.state, t.cc1, t.cc2)
        println("-------------")
    }
}