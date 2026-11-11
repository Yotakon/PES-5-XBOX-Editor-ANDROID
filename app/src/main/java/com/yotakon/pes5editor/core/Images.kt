package com.yotakon.pes5editor.core

/** Imagen con paleta. [palette] en ARGB, [pixels] = índice de paleta de cada píxel (fila a fila). */
class IndexedImage(val width: Int, val height: Int, val palette: IntArray, val pixels: IntArray) {
    fun toArgb() = IntArray(pixels.size) { palette.getOrElse(pixels[it]) { 0 } }
}

/**
 * Reduce una imagen ARGB a [colors] colores. El índice 0 queda reservado para transparente
 * (píxeles con alfa < 128), como espera el juego.
 */
object Quantizer {
    fun quantize(argb: IntArray, width: Int, height: Int, colors: Int): IndexedImage {
        require(argb.size == width * height)
        val maxOpaque = colors - 1
        val opaque = argb.filter { (it ushr 24) >= 128 }.map { it and 0xFFFFFF }
        val unique = opaque.distinct()
        val pal: List<Int> = if (unique.size <= maxOpaque) unique else medianCut(opaque, maxOpaque)
        val palette = IntArray(colors)
        palette[0] = 0
        pal.forEachIndexed { i, c -> palette[i + 1] = c or (0xFF shl 24) }
        val cache = HashMap<Int, Int>()
        val pixels = IntArray(argb.size) { i ->
            val p = argb[i]
            if ((p ushr 24) < 128) 0 else cache.getOrPut(p and 0xFFFFFF) { nearest(pal, p and 0xFFFFFF) + 1 }
        }
        return IndexedImage(width, height, palette, pixels)
    }

    private fun nearest(pal: List<Int>, c: Int): Int {
        if (pal.isEmpty()) return 0
        var best = 0
        var bestD = Int.MAX_VALUE
        val r = c ushr 16 and 0xFF
        val g = c ushr 8 and 0xFF
        val b = c and 0xFF
        for (i in pal.indices) {
            val q = pal[i]
            val dr = r - (q ushr 16 and 0xFF)
            val dg = g - (q ushr 8 and 0xFF)
            val db = b - (q and 0xFF)
            val d = dr * dr * 2 + dg * dg * 4 + db * db * 3
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun channel(c: Int, ch: Int) = c ushr (16 - 8 * ch) and 0xFF

    /** Corte por la mediana: divide repetidamente la caja con el mayor rango de color. */
    private fun medianCut(pixels: List<Int>, n: Int): List<Int> {
        val boxes = ArrayList<MutableList<Int>>()
        boxes += pixels.toMutableList()
        while (boxes.size < n) {
            var bi = -1
            var bestRange = 0
            var bestCh = 0
            for ((i, box) in boxes.withIndex()) {
                if (box.size < 2) continue
                for (ch in 0..2) {
                    var lo = 255
                    var hi = 0
                    for (c in box) {
                        val v = channel(c, ch)
                        if (v < lo) lo = v
                        if (v > hi) hi = v
                    }
                    if (hi - lo > bestRange) { bestRange = hi - lo; bi = i; bestCh = ch }
                }
            }
            if (bi < 0) break
            val box = boxes.removeAt(bi)
            box.sortBy { channel(it, bestCh) }
            val mid = box.size / 2
            boxes += box.subList(0, mid).toMutableList()
            boxes += box.subList(mid, box.size).toMutableList()
        }
        return boxes.filter { it.isNotEmpty() }.map { box ->
            var r = 0L; var g = 0L; var b = 0L
            for (c in box) { r += channel(c, 0); g += channel(c, 1); b += channel(c, 2) }
            val k = box.size
            ((r / k).toInt() shl 16) or ((g / k).toInt() shl 8) or (b / k).toInt()
        }.distinct()
    }
}

/** Lectura y escritura de imágenes con paleta RGBA y píxeles de 8 o 4 bits (nibble alto = primer píxel). */
internal object PalettedData {
    fun readPalette(of: OptionFile, a: Int, n: Int) = IntArray(n) {
        val p = a + it * 4
        (of.u8(p + 3) shl 24) or (of.u8(p) shl 16) or (of.u8(p + 1) shl 8) or of.u8(p + 2)
    }

    fun writePalette(of: OptionFile, a: Int, palette: IntArray) {
        for (i in palette.indices) {
            val c = palette[i]
            val p = a + i * 4
            of.data[p] = (c ushr 16).toByte()
            of.data[p + 1] = (c ushr 8).toByte()
            of.data[p + 2] = c.toByte()
            of.data[p + 3] = (c ushr 24).toByte()
        }
    }

    fun read8(of: OptionFile, a: Int, count: Int) = IntArray(count) { of.u8(a + it) }

    fun write8(of: OptionFile, a: Int, px: IntArray) {
        for (i in px.indices) of.data[a + i] = px[i].toByte()
    }

    fun read4(of: OptionFile, a: Int, count: Int) = IntArray(count) {
        val b = of.u8(a + it / 2)
        if (it % 2 == 0) b ushr 4 else b and 0xF
    }

    fun write4(of: OptionFile, a: Int, px: IntArray) {
        var i = 0
        while (i < px.size) {
            of.data[a + i / 2] = (((px[i] and 0xF) shl 4) or (px[i + 1] and 0xF)).toByte()
            i += 2
        }
    }
}

/**
 * Escudos (Flags de PESFan Editor 5). Hay sitio para 100 "unidades": cada escudo de 16 colores ocupa 1
 * y cada uno de 128 colores ocupa 2. Los de 128 se guardan desde el principio y los de 16 desde el final.
 */
object Emblems {
    const val SIZE = 64
    private const val START = 969356
    private const val SIZE128 = 5184
    private const val SIZE16 = 2176
    private const val COUNT128 = 969196
    private const val COUNT16 = 969197
    private const val TABLE128 = 969198 // 50 entradas: hueco de cada escudo de 128, 0x99 = libre
    private const val TABLE16 = 969248  // 100 entradas: 50 + hueco de cada escudo de 16
    private const val FREE: Byte = 0x99.toByte()

    enum class Kind(val colors: Int) { C128(128), C16(16) }

    /** Escudo cargado. [value] es el número que se guarda en el club para usarlo. */
    data class Emblem(val kind: Kind, val slot: Int, val value: Int)

    fun count128(of: OptionFile) = of.u8(COUNT128)
    fun count16(of: OptionFile) = of.u8(COUNT16)
    fun free16(of: OptionFile) = (100 - count128(of) * 2 - count16(of)).coerceAtLeast(0)
    fun free128(of: OptionFile) = free16(of) / 2

    private fun adr128(k: Int) = START + k * SIZE128
    private fun adr16(k: Int) = START + 49 * SIZE128 - (k / 2) * SIZE128 + (k % 2) * SIZE16
    private fun adr(kind: Kind, slot: Int) = if (kind == Kind.C128) adr128(slot) else adr16(slot)

    fun list(of: OptionFile): List<Emblem> {
        val c128 = count128(of).coerceAtMost(50)
        val c16 = count16(of).coerceAtMost(100)
        return (0 until c128).map { Emblem(Kind.C128, it, of.u16(adr128(it) + 4)) } +
            (0 until c16).map { Emblem(Kind.C16, it, of.u16(adr16(it) + 4)) }
    }

    fun image(of: OptionFile, kind: Kind, slot: Int): IndexedImage {
        val a = adr(kind, slot)
        return if (kind == Kind.C128) {
            IndexedImage(SIZE, SIZE, PalettedData.readPalette(of, a + 64, 128), PalettedData.read8(of, a + 1088, SIZE * SIZE))
        } else {
            IndexedImage(SIZE, SIZE, PalettedData.readPalette(of, a + 64, 16), PalettedData.read4(of, a + 128, SIZE * SIZE))
        }
    }

    /** Imagen del escudo con ese valor de club, o null si no es un escudo cargado. */
    fun imageForValue(of: OptionFile, value: Int): IndexedImage? {
        val j = value - ClubData.FIRST_CUSTOM_EMBLEM
        if (j < 0 || j >= 150) return null
        return if (j < 50) {
            val slot = of.u8(TABLE128 + j)
            if (slot < count128(of)) image(of, Kind.C128, slot) else null
        } else {
            val slot = of.u8(TABLE16 + j - 50) - 50
            if (slot in 0 until count16(of)) image(of, Kind.C16, slot) else null
        }
    }

    private fun writeImage(of: OptionFile, kind: Kind, slot: Int, img: IndexedImage) {
        require(img.width == SIZE && img.height == SIZE && img.palette.size == kind.colors)
        val a = adr(kind, slot)
        PalettedData.writePalette(of, a + 64, img.palette)
        if (kind == Kind.C128) PalettedData.write8(of, a + 1088, img.pixels) else PalettedData.write4(of, a + 128, img.pixels)
    }

    /** Añade un escudo nuevo. Devuelve el valor para asignarlo a un club. */
    fun add(of: OptionFile, kind: Kind, img: IndexedImage): Int {
        if (kind == Kind.C128 && free128(of) < 1) throw OptionFileException("No queda sitio para escudos de 128 colores.")
        if (kind == Kind.C16 && free16(of) < 1) throw OptionFileException("No queda sitio para escudos.")
        val (table, entries, firstValue, slot) = if (kind == Kind.C128) {
            listOf(TABLE128, 50, ClubData.FIRST_CUSTOM_EMBLEM, count128(of))
        } else {
            listOf(TABLE16, 100, ClubData.FIRST_CUSTOM_EMBLEM + 50, count16(of))
        }
        val m = (0 until entries).firstOrNull { of.data[table + it] == FREE }
            ?: throw OptionFileException("La tabla de escudos está llena.")
        val a = adr(kind, slot)
        val size = if (kind == Kind.C128) SIZE128 else SIZE16
        for (i in 0 until size) of.data[a + i] = 0
        writeImage(of, kind, slot, img)
        of.data[a] = 1
        val value = firstValue + m
        of.setU16(a + 4, value)
        of.data[table + m] = (if (kind == Kind.C128) slot else 50 + slot).toByte()
        if (kind == Kind.C128) of.data[COUNT128] = (slot + 1).toByte() else of.data[COUNT16] = (slot + 1).toByte()
        return value
    }

    fun replace(of: OptionFile, e: Emblem, img: IndexedImage) = writeImage(of, e.kind, e.slot, img)

    /** Borra el escudo, devuelve el escudo original a los clubes que lo usaban y compacta los huecos. */
    fun delete(of: OptionFile, e: Emblem) {
        val a = adr(e.kind, e.slot)
        val value = of.u16(a + 4)
        for (c in 0 until ClubData.COUNT) if (ClubData.emblem(of, c) == value) ClubData.setOriginalEmblem(of, c)
        val count = if (e.kind == Kind.C128) count128(of) else count16(of)
        val size = if (e.kind == Kind.C128) SIZE128 else SIZE16
        val table = if (e.kind == Kind.C128) TABLE128 else TABLE16
        val first = if (e.kind == Kind.C128) ClubData.FIRST_CUSTOM_EMBLEM else ClubData.FIRST_CUSTOM_EMBLEM + 50
        val m = value - first
        if (m in 0 until (if (e.kind == Kind.C128) 50 else 100)) of.data[table + m] = FREE
        val last = adr(e.kind, count - 1)
        if (e.slot != count - 1) {
            System.arraycopy(of.data, last, of.data, a, size)
            val movedM = of.u16(a + 4) - first
            of.data[table + movedM] = (if (e.kind == Kind.C128) e.slot else 50 + e.slot).toByte()
        }
        for (i in 0 until size) of.data[last + i] = 0
        if (e.kind == Kind.C128) of.data[COUNT128] = (count - 1).toByte() else of.data[COUNT16] = (count - 1).toByte()
    }
}

/** Logos de 32x32 y 16 colores (Logos de PESFan Editor 5): 80 huecos fijos. */
object Logos {
    const val COUNT = 80
    const val SIZE = 32
    private const val START = 920540
    private const val SLOT = 608

    private fun adr(i: Int) = START + i * SLOT

    fun isUsed(of: OptionFile, i: Int) = of.data[adr(i)].toInt() == 1

    fun image(of: OptionFile, i: Int) =
        IndexedImage(SIZE, SIZE, PalettedData.readPalette(of, adr(i) + 32, 16), PalettedData.read4(of, adr(i) + 96, SIZE * SIZE))

    fun set(of: OptionFile, i: Int, img: IndexedImage) {
        require(img.width == SIZE && img.height == SIZE && img.palette.size == 16)
        PalettedData.writePalette(of, adr(i) + 32, img.palette)
        PalettedData.write4(of, adr(i) + 96, img.pixels)
        of.data[adr(i)] = 1
    }

    fun delete(of: OptionFile, i: Int) {
        for (k in 0 until SLOT) of.data[adr(i) + k] = 0
    }
}
