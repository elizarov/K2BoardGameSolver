import kotlinx.coroutines.*
import java.util.concurrent.*
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

class StateValues1 {
    private val a = Array(DAYS + 1) { ConcurrentHashMap<State1, Any>() }

    fun totalStates() = a.sumOf { it.size }
    fun dayStates(day: Int): Int = a[day].size

    suspend fun getOrStart(day: Int, state: State1): Int? {
        var started = false
        val result = a[day].computeIfAbsent(state) {
            started = true
            CompletableDeferred<Int>()
        }
        if (started) return null
        return when(result) {
            is Int -> result
            is Deferred<*> -> result.await() as Int
            else -> error("cannot be $result")
        }
    }

    operator fun get(day: Int, state: State1): Int = a[day][state] as Int

    @Suppress("UNCHECKED_CAST")
    operator fun set(day: Int, state: State1, value: Int) {
        when (val cur = a[day][state]) {
            is Int -> require(cur == value)
            is CompletableDeferred<*> -> (cur as CompletableDeferred<Int>).complete(value)
        }
        a[day][state] = value
    }
}

private val stateValues1 = StateValues1()

data class MoveTrace1(
    val day: Int,
    val cc1: PackedCards,
    val state: State1,
    val via: List<Space>
)

suspend fun solveStateValue1(day: Int, state: State1, trace: MutableList<MoveTrace1>?, paths: BoardPaths): Int {
    if (day == DAYS) return state.compositeScore
    if (trace == null) stateValues1.getOrStart(day, state)?.let { return it }
    val nHand = state.hand.countCards()
    if (nHand < HAND_SIZE && state.deck != 0L || nHand == 0) {
        val deck = if (state.deck == 0L) allCards else state.deck
        val nSelect = HAND_SIZE - nHand
        val nDeck = deck.countCards()
        if (nSelect == nDeck) {
            // must take all remaining cards
            val result = solveStateValue1(day, State1(state.c1, state.hand + deck, 0L), trace, paths)
            stateValues1[day, state] = result
            return result
        }
        var result = if (trace == null) Int.MAX_VALUE else stateValues1[day, state]
        val rnd = Random(day)
        for (sample in 1..SAMPLES) {
            val sel = rnd.sampleCardsShuffle(nSelect, deck)
            val next = State1(state.c1, state.hand + sel, deck - sel)
            if (trace == null) {
                result = minOf(result, solveStateValue1(day, next, trace, paths))
            } else {
                if (stateValues1[day, next] == result) {
                    return solveStateValue1(day, next, trace, paths)
                }
            }
        }
        stateValues1[day, state] = result
        return result
    }
    var best = if (trace == null) 0 else stateValues1[day, state]
    var done = false
    coroutineScope {
        val forks = ArrayList<Deferred<Int>>()
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
                    if (trace == null) {
                        forks += async { solveStateValue1(day + 1, next, null, paths) }
                    } else {
                        val win = solveStateValue1(day + 1, next, null, paths)
                        if (win == best) {
                            trace.add(MoveTrace1(day, useCards, next, m1.via))
                            solveStateValue1(day + 1, next, trace, paths)
                            done = true
                            return@loop1
                        }
                    }
                }
            }
        }
        if (trace == null) {
            best = forks.maxOfOrNull { it.await() } ?: 0
        }
    }
    stateValues1[day, state] = best
    return best
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
    val (score, time) = measureTimedValue {
        solveStateValue1(0, s0, null, paths)
    }
    println(
        "Found solution that gives worst-case score of $score " +
        "in ${time.toString(DurationUnit.SECONDS, 3)} sec (${stateValues1.totalStates()} states)"
    )
    println("Tracing a worst-case sample from the solution")
    val trace = ArrayList<MoveTrace1>()
    solveStateValue1(0, s0, trace, paths)
    for (t in trace) {
        val statesCnt = stateValues1.dayStates(t.day + 1)
        println("--- DAY ${t.day + 1} ($statesCnt states)---")
        board.printState(t.state, t.cc1, vias = listOf(t.via))
    }
}