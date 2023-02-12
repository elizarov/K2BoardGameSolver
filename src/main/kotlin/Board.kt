// [AC]    -- cells
//   Acc   -- acclimatization value -2, -1, 0, +1
//   Ent   -- entry cost value 1, 2, 3
//            * marks stating space
// =height -- height from here and up
// :score  -- score from here and up
// !limit  -- limit in the number of climbers from here and up

val easyBoard = parseBoard("""
                   [-23]
:10                  |
                   [-23]
:7 =8000           |   |
               [-12]   [-23]  
:6              |         |
              [-12]       |
                |         |
              [-12]     [-12] 
:5 =7000 !1   |   |       | 
          [-13]   [-12] [-12]
:4          |         |   |
            [01]      [-11] 
:3          |  |        |
         [02]  [02]   [02]
:2 =6000  |    |  |     |
         [01]--/  |  /-[02]
          |       [01]
         [01]     |
          |      /[12]-\
         [11]-\  |    /[11]-\
              [11]-\  |    /[11]-\
                   [11]-\  |     [11]-\
:1 !2                   [11]          [11]*
""")

fun parseBoard(spec0: String): Board {
    val spec = spec0.split("\n").filter { it.isNotBlank() }
    val spaces = ArrayList<Space>()
    var height = 0
    var score = 0
    var limit = 0
    for (row in spec.lastIndex downTo 0) {
        val s = spec[row]
        s.indexOf(':').takeIf { it >= 0 }?.let { i ->
            val j = s.indexOf(' ', i)
            score = s.substring(i + 1, j).toInt()
        }
        s.indexOf('=').takeIf { it >= 0 }?.let { i ->
            val j = s.indexOf(' ', i)
            height = s.substring(i + 1, j).toInt()
        }
        s.indexOf('!').takeIf { it >= 0 }?.let { i ->
            val j = s.indexOf(' ', i)
            limit = s.substring(i + 1, j).toInt()
        }
        require(score > 0)
        require(limit > 0)
        var i = 0
        while (i < s.length) {
            i = s.indexOf('[', i).takeIf { it >= 0 } ?: break
            val j = s.indexOf(']', i)
            require(j >= 0)
            require(j - i in 3..4)
            val acc = s.substring(i + 1, j - 1).toInt()
            val ent = s.substring(j - 1, j).toInt()
            spaces += Space(spaces.size, acc, ent, height, score, limit, row, i, j, s.getOrNull(j + 1) == '*')
            i = j + 1
        }
    }
    fun findMove(row0: Int, col0: Int, dr0: Int, dc0: Int): Space? {
        var row = row0
        var col = col0
        var dr = dr0
        var dc = dc0
        while (true) {
            if (row !in spec.indices) return null
            val s = spec[row]
            if (col !in s.indices) return null
            spaces.singleOrNull { row == it.row && col in it.col1..it.col2 }?.let { return it }
            when (s[col]) {
                '-' -> {
                    if (dr != 0) return null
                    col += dc
                }
                '|' -> {
                    if (dc != 0) return null
                    row += dr
                }
                '/' -> when {
                    dr != 0 -> {
                        col -= dr
                        dc = -dr
                        dr = 0
                    }
                    dc != 0 -> {
                        row -= dc
                        dr = -dc
                        dc = 0
                    }
                }
                '\\' -> when {
                    dr != 0 -> {
                        col += dr
                        dc = dr
                        dr = 0
                    }
                    dc != 0 -> {
                        row += dc
                        dr = dc
                        dc = 0
                    }
                }
                else -> return null
            }
        }
    }
    for (space in spaces) {
        findMove(space.row, space.col1 - 1, 0, -1)?.let { space.moves += it }
        findMove(space.row, space.col2 + 1, 0, +1)?.let { space.moves += it }
        for (col in space.col1..space.col2) {
            findMove(space.row + 1, col, +1, 0)?.let { space.moves += it }
            findMove(space.row - 1, col, -1, 0)?.let { space.moves += it }
        }
        require(space.moves.isNotEmpty())
    }
    return Board(spec, spaces, spaces.single { it.isStart })
}

class Space(
    val index: Int,
    val acc: Int,
    val ent: Int,
    val height: Int,
    val score: Int,
    val limit: Int,
    val row: Int,
    val col1: Int,
    val col2: Int,
    val isStart: Boolean
) {
    val moves = ArrayList<Space>()
    override fun toString(): String = index.toString()
}

class Board(
    val spec: List<String>,
    val spaces: List<Space>,
    val start: Space
) {
    override fun toString(): String = buildString {
        val n = spaces.size
        append("$n spaces, ")
        append("${spaces.sumOf { it.moves.size }} moves, ")
        append("${spaces.sumOf { it.acc } * 100 / n / 100.0} avg acc, ")
        append("${spaces.sumOf { it.ent } * 100 / n / 100.0} avg ent")
    }

    fun printState(state: State, cc1: PackedCards = 0, cc2: PackedCards = 0) {
        val c1 = state.c1
        val c2 = state.c2
        println("Climber 1 $c1${if (cc1 == 0L) "" else "; used cards ${cc1.packedCardsToString()}"}")
        println("Climber 2 $c2${if (cc2 == 0L) "" else "; used cards ${cc2.packedCardsToString()}"}")
        if (state.hand != 0L) println("hand ${state.hand.packedCardsToString()}")
        val ul = HashSet<Space>()
        val ur = HashSet<Space>()
        val lt = HashMap<Space, Char>()
        val rt = HashMap<Space, Char>()
        val ch2str = mapOf(
            'A' to "❶",
            'B' to "❷",
            'C' to "➀",
            'D' to "➁",
        )
        lt[c1.space] = 'A'
        rt[c2.space] = 'B'
        c1.tent?.let { lt[it] = 'C' }
        c2.tent?.let { rt[it] = 'D' }
        for (row in spec.indices) {
            val s0 = (spec[row] + "  ").toCharArray()
            for ((space, ch) in lt) if (space.row == row) {
                var c = space.col1 - 1
                if (!ul.add(space)) c--
                s0[c] = ch
            }
            for ((space, ch) in rt) if (space.row == row) {
                var c = space.col2 + 1
                if (!ur.add(space)) c++
                s0[c] = ch
            }
            var s = s0.concatToString()
            for ((ch, str) in ch2str) s = s.replace(ch.toString(), str)
            println(s)
        }
    }
}

fun main() {
    println("easyBoard = $easyBoard")
}