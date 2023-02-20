import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

data class State1(
    val c1: Climber,
    override val hand: PackedCards,
    override val deck: PackedCards
) : ClimbersState {
    val compositeScore: Int
        get() = c1.score * 100 + c1.acc
    override val climbers: List<Climber>
        get() = listOf(c1)
    override fun toString(): String = "$c1 hand=${hand.packedCardsToString()} deck=${deck.packedCardsToString()}"
}

private const val DAYS = 7
private const val SAMPLES = 3

data class MoveTrace1(
    val day: Int,
    val cc1: PackedCards,
    val state: State1,
    val via: List<Space>
)

class Solver1(
    val paths: BoardPaths,
    val coroutineScope: CoroutineScope
) {
    val states = Array(DAYS + 1) { ConcurrentHashMap<State1, Int>() }

    suspend fun solveStateValue1(day: Int, state: State1, trace: ArrayList<MoveTrace1>?): Int {
        if (day == DAYS) return state.compositeScore
        if (trace == null) states[day][state]?.let { return it }
        val nHand = state.hand.countCards()
        return if (nHand < HAND_SIZE && state.deck != 0L || nHand == 0)
            refillHand(day, state, trace)
        else
            makeMove(day, state, trace)
    }

    private suspend fun refillHand(day: Int, state: State1, trace: ArrayList<MoveTrace1>?): Int {
        val nHand = state.hand.countCards()
        val deck = if (state.deck == 0L) allCards else state.deck
        val nSelect = HAND_SIZE - nHand
        val nDeck = deck.countCards()
        if (nSelect == nDeck) {
            // must take all remaining cards
            return solveStateValue1(day, State1(state.c1, state.hand + deck, 0L), trace)
        }
        var result = if (trace == null) Int.MAX_VALUE else states[day][state]!!
        val rnd = Random(day)
        for (sample in 1..SAMPLES) {
            val sel = rnd.sampleCardsShuffle(nSelect, deck)
            val next = State1(state.c1, state.hand + sel, deck - sel)
            if (trace == null) {
                result = minOf(result, solveStateValue1(day, next, trace))
            } else {
                if (states[day][next]!! == result) {
                    return solveStateValue1(day, next, trace)
                }
            }
        }
        states[day][state] = result
        return result
    }

    private suspend fun makeMove(day: Int, state: State1, trace: ArrayList<MoveTrace1>?): Int {
        var best = if (trace == null) 0 else states[day][state]!!
        var done = false
        val moves: ArrayList<Deferred<Int>>? = if (trace == null && day <= 1) ArrayList() else null
        chooseCards(USE_CARDS, state.hand) loop1@{ useCards ->
            if (done) return@loop1
            val ms1 = findClimberMoves(state.c1, useCards, paths)
            for (m1 in ms1) {
                val t1 = state.c1.tent ?: m1.tent
                var a1 = m1.accWithoutTent
                if (m1.space == t1) a1++
                if (a1 >= 1) {
                    val c1 = Climber(m1.space, t1, maxOf(state.c1.score, m1.score), a1.coerceAtMost(MAX_ACC))
                    val next = State1(c1, state.hand - useCards, state.deck)
                    if (moves != null) {
                        moves += coroutineScope.async { solveStateValue1(day + 1, next, null) }
                    } else if (trace == null) {
                        val win = solveStateValue1(day + 1, next, null)
                        best = maxOf(best, win)
                    } else {
                        val win = solveStateValue1(day + 1, next, null)
                        if (win == best) {
                            trace.add(MoveTrace1(day, useCards, next, m1.via))
                            solveStateValue1(day + 1, next, trace)
                            done = true
                            return@loop1
                        }
                    }
                }
            }
        }
        if (moves != null) {
            best = moves.maxOfOrNull { it.await() } ?: 0
        }
        states[day][state] = best
        return best
    }

}

@OptIn(ExperimentalTime::class)
fun main() = runBlocking(Dispatchers.Default) {
    val board = easyBoard
    val paths = BoardPaths(board)
    println("--- Board ---")
    val c0 = Climber(board.start, null, 1, 1)
    val s0 = State1(c0, 0L, 0L)
    board.printState(s0)
    println("Finding solution for game with $DAYS days, sampling $SAMPLES card shuffles...")
    val solver = Solver1(paths, this)
    val (score, time) = measureTimedValue {
        solver.solveStateValue1(0, s0, null)
    }
    val totalStates = solver.states.sumOf { it.size }
    println("Found solution that gives worst-case score of $score in ${time.toString(DurationUnit.SECONDS, 3)} sec ($totalStates states)")
    println("Tracing a worst-case sample from the solution")
    val trace = ArrayList<MoveTrace1>()
    solver.solveStateValue1(0, s0, trace)
    for (t in trace) {
        val statesCnt = solver.states[t.day + 1].size
        println("--- DAY ${t.day + 1} ($statesCnt states)---")
        board.printState(t.state, t.cc1, vias = listOf(t.via))
    }
}