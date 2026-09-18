package net.orionx.messenger

/**
 * Minimal QR encoder — byte mode, ECC level M, versions 1..6, mask 0.
 *
 * This is a direct port of `qr.js` from the redesign canvas. Keeping the same version range and
 * the same fixed mask matters: the artboard's QR is drawn from this exact algorithm, so porting
 * it (rather than pulling in a general-purpose encoder) is what makes the rendered code identical
 * to the design down to the module pattern.
 */
internal object OrionQrEncoder {

    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11d
        }
        for (j in 255 until 512) EXP[j] = EXP[j - 255]
    }

    private fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

    private fun genPoly(n: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until n) {
            val next = IntArray(g.size + 1)
            for (j in g.indices) {
                next[j] = next[j] xor mul(g[j], 1)
                next[j + 1] = next[j + 1] xor mul(g[j], EXP[i])
            }
            g = next
        }
        return g
    }

    private fun ecc(data: IntArray, n: Int): IntArray {
        val g = genPoly(n)
        val res = IntArray(n)
        for (element in data) {
            val factor = element xor res[0]
            for (k in 0 until n - 1) res[k] = res[k + 1]
            res[n - 1] = 0
            if (factor != 0) for (j in 0 until g.size - 1) res[j] = res[j] xor mul(g[j + 1], factor)
        }
        return res
    }

    private class Spec(val cap: Int, val blocks: IntArray, val ec: Int, val align: IntArray)

    private val V = mapOf(
        1 to Spec(14, intArrayOf(16), 10, intArrayOf()),
        2 to Spec(26, intArrayOf(28), 16, intArrayOf(6, 18)),
        3 to Spec(42, intArrayOf(44), 26, intArrayOf(6, 22)),
        4 to Spec(62, intArrayOf(32, 32), 18, intArrayOf(6, 26)),
        5 to Spec(84, intArrayOf(43, 43), 24, intArrayOf(6, 30)),
        6 to Spec(106, intArrayOf(27, 27, 27, 27), 16, intArrayOf(6, 34)),
    )

    /** `size` is the module count per side; `modules[row][col]` is true for a dark module. */
    class QrCode(val size: Int, val modules: Array<BooleanArray>)

    fun build(text: String): QrCode {
        var bytes = text.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xff }
        var ver = (1..6).firstOrNull { bytes.size <= V.getValue(it).cap } ?: 0
        if (ver == 0) {
            bytes = bytes.take(V.getValue(6).cap)
            ver = 6
        }
        val spec = V.getValue(ver)
        val totalData = spec.blocks.sum()

        val bits = ArrayList<Int>(totalData * 8 + 16)
        fun push(value: Int, len: Int) {
            for (i in len - 1 downTo 0) bits.add((value shr i) and 1)
        }
        push(4, 4)                  // byte mode
        push(bytes.size, 8)         // char count, versions below 10
        bytes.forEach { push(it, 8) }
        val cap = totalData * 8
        var t = 0
        while (t < 4 && bits.size < cap) {
            bits.add(0)
            t++
        }
        while (bits.size % 8 != 0) bits.add(0)

        val words = ArrayList<Int>(totalData)
        var i = 0
        while (i < bits.size) {
            var v = 0
            for (j in 0 until 8) v = (v shl 1) or bits[i + j]
            words.add(v)
            i += 8
        }
        val pads = intArrayOf(0xEC, 0x11)
        var pi = 0
        while (words.size < totalData) words.add(pads[pi++ % 2])

        val dBlocks = ArrayList<IntArray>()
        val eBlocks = ArrayList<IntArray>()
        var pos = 0
        for (n in spec.blocks) {
            val chunk = IntArray(n) { words[pos + it] }
            pos += n
            dBlocks.add(chunk)
            eBlocks.add(ecc(chunk, spec.ec))
        }
        val out = ArrayList<Int>()
        val maxD = spec.blocks.max()
        for (c in 0 until maxD) dBlocks.forEach { if (c < it.size) out.add(it[c]) }
        for (e in 0 until spec.ec) eBlocks.forEach { out.add(it[e]) }

        val size = 17 + ver * 4
        val m = Array(size) { BooleanArray(size) }
        val fn = Array(size) { BooleanArray(size) }
        fun set(r: Int, c: Int, v: Boolean) {
            m[r][c] = v
            fn[r][c] = true
        }

        fun finder(r0: Int, c0: Int) {
            for (r in -1..7) for (c in -1..7) {
                val rr = r0 + r
                val cc = c0 + c
                if (rr < 0 || cc < 0 || rr >= size || cc >= size) continue
                val inRing = (r in 0..6 && (c == 0 || c == 6)) || (c in 0..6 && (r == 0 || r == 6))
                val inCore = r in 2..4 && c in 2..4
                set(rr, cc, inRing || inCore)
            }
        }
        finder(0, 0); finder(0, size - 7); finder(size - 7, 0)

        for (k in 8 until size - 8) {
            set(6, k, k % 2 == 0)
            set(k, 6, k % 2 == 0)
        }

        val lastAlign = spec.align.lastOrNull()
        for (ar in spec.align) for (ac in spec.align) {
            if ((ar == 6 && ac == 6) || (ar == 6 && ac == lastAlign) || (ac == 6 && ar == lastAlign)) continue
            for (r in -2..2) for (c in -2..2) {
                val dark = maxOf(kotlin.math.abs(r), kotlin.math.abs(c)) != 1
                set(ar + r, ac + c, dark)
            }
        }

        set(size - 8, 8, true) // dark module
        for (k in 0..8) {
            if (!fn[8][k]) set(8, k, false)
            if (!fn[k][8]) set(k, 8, false)
        }
        for (k in 0 until 8) {
            if (!fn[8][size - 1 - k]) set(8, size - 1 - k, false)
            if (!fn[size - 1 - k][8]) set(size - 1 - k, 8, false)
        }

        // Data placement, zigzag, mask 0 — ((row + col) % 2 == 0).
        val bitStream = ArrayList<Int>(out.size * 8)
        out.forEach { w -> for (b in 7 downTo 0) bitStream.add((w shr b) and 1) }
        var idx = 0
        var up = true
        var col = size - 1
        while (col > 0) {
            if (col == 6) col--
            for (n in 0 until size) {
                val row = if (up) size - 1 - n else n
                for (cOff in 0 until 2) {
                    val cc = col - cOff
                    if (fn[row][cc]) continue
                    var bit = if (idx < bitStream.size) bitStream[idx++] else 0
                    if ((row + cc) % 2 == 0) bit = bit xor 1
                    m[row][cc] = bit == 1
                }
            }
            up = !up
            col -= 2
        }

        // Format info: level M (0b00) with mask 0.
        val fmt = 0
        var d = fmt shl 10
        val g = 0x537
        for (b in 4 downTo 0) if (d and (1 shl (b + 10)) != 0) d = d xor (g shl b)
        val bitsFmt = ((fmt shl 10) or d) xor 0x5412
        fun fbit(i: Int): Boolean = ((bitsFmt shr i) and 1) == 1
        for (i2 in 0..5) m[8][i2] = fbit(i2)
        m[8][7] = fbit(6); m[8][8] = fbit(7); m[7][8] = fbit(8)
        for (i3 in 9..14) m[14 - i3][8] = fbit(i3)
        for (i4 in 0..7) m[size - 1 - i4][8] = fbit(i4)
        for (i5 in 8..14) m[8][size - 15 + i5] = fbit(i5)
        m[size - 8][8] = true

        return QrCode(size, m)
    }
}
