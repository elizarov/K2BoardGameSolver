import kotlin.random.Random

data class State1(
    val c1: Climber,
    override val hand: PackedCards,
    val deck: PackedCards
) : ClimbersState {
    val compositeScore: Int
        get() = c1.score * 100 + c1.acc
    override val climbers: List<Climber>
        get() = listOf(c1)
    override fun toString(): String = "$c1 hand=${hand.packedCardsToString()} deck=${deck.packedCardsToString()}"
}

private const val DAYS = 7
private const val SAMPLES = 3

private val stateValues1 = Array(DAYS) { HashMap<State1, Int>() }

data class MoveTrace1(
    val day: Int,
    val cc1: PackedCards,
    val state: State1
)

fun solveStateValue1(day: Int, state: State1, trace: MutableList<MoveTrace1>?, paths: BoardPaths): Int {
    if (day == DAYS) return state.compositeScore
    if (trace == null) stateValues1[day][state]?.let { return it }
    val nHand = state.hand.countCards()
    if (nHand < HAND_SIZE && state.deck != 0L || nHand == 0) {
        val deck = if (state.deck == 0L) allCards else state.deck
        val nSelect = HAND_SIZE - nHand
        val nDeck = deck.countCards()
        if (nSelect == nDeck) {
            // must take all remaining cards
            return solveStateValue1(day, State1(state.c1,state.hand + deck, 0L), trace, paths)
        }
        var result = if (trace == null) Int.MAX_VALUE else stateValues1[day][state]!!
        val rnd = Random(day)
        for (sample in 1..SAMPLES) {
            val sel = rnd.sampleCardsShuffle(nSelect, deck)
            val next = State1(state.c1, state.hand + sel, deck - sel)
            if (trace == null) {
                result = minOf(result, solveStateValue1(day, next, trace, paths))
            } else {
                if (stateValues1[day][next]!! == result) {
                    return solveStateValue1(day, next, trace, paths)
                }
            }
        }
        stateValues1[day][state] = result
        return result
    }
    var best = if (trace == null) 0 else stateValues1[day][state]!!
    var done = false
    chooseCards(USE_CARDS, state.hand) loop1@{ useCards ->
        if (done) return@loop1
        val ms1 = findClimberMoves(state.c1, useCards, paths)
        for ((s1, t1n, sc1, a1wt) in ms1) {
            val t1 = state.c1.tent ?: t1n
            var a1 = a1wt
            if (s1 == t1) a1++
            if (a1 >= 1) {
                val c1 = Climber(s1, t1, maxOf(state.c1.score, sc1), a1.coerceAtMost(MAX_ACC))
                val next = State1(c1, state.hand - useCards, state.deck)
                val win = solveStateValue1(day + 1, next, null, paths)
                if (trace == null) {
                    best = maxOf(best, win)
                } else {
                    if (win == best) {
                        trace.add(MoveTrace1(day, useCards, next))
                        solveStateValue1(day + 1, next, trace, paths)
                        done = true
                        return@loop1
                    }
                }
            }
        }
    }
    stateValues1[day][state] = best
    return best
}

fun main() {
    val board = easyBoard
    val paths = BoardPaths(board)
    println("--- Board ---")
    val c0 = Climber(board.start, null, 1, 1)
    val s0 = State1(c0, 0L, 0L)
    board.printState(s0)
    println("Finding solution for game with $DAYS days, sampling $SAMPLES card shuffles...")
    val score = solveStateValue1(0, s0, null, paths)
    println("Found solution that gives worst-case score of $score")
    println("Tracing a worst-case sample from the solution")
    val trace = ArrayList<MoveTrace1>()
    solveStateValue1(0, s0, trace, paths)
    for (t in trace) {
        println("--- DAY ${t.day} ---")
        board.printState(t.state, t.cc1)
    }
}