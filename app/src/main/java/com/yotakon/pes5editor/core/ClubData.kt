package com.yotakon.pes5editor.core

/**
 * Datos de los 138 clubes (bloque de 140 bytes en 803608). Posiciones de TeamPanel / Clubs de PESFan Editor 5.
 *  +0    nombre (48 bytes UTF-8 + 0)
 *  +73   abreviatura (3 letras); también se copia en 716932 + club * 628
 *  +110  indicador "nombre editado"
 *  +112  escudo (2 bytes, repetido en +116); +120 y +121 indicadores
 *  +124  color 1 (RGB); +128 color 2 (RGB)
 *  +133  estadio; +135 indicador
 */
object ClubData {
    const val COUNT = Teams.CLUB_COUNT
    const val NAME_MAX = 48
    private const val ABBR_COPY = 716932
    /** Valor de escudo del primer escudo cargado por el usuario (Clubs / TeamPanel.firstFlag). */
    const val FIRST_CUSTOM_EMBLEM = 284

    private fun base(c: Int): Int {
        require(c in 0 until COUNT) { "club fuera de rango" }
        return Teams.CLUB_BASE + c * Teams.CLUB_SIZE
    }

    fun name(of: OptionFile, c: Int) = Teams.clubName(of, c)

    fun setName(of: OptionFile, c: Int, name: String) {
        val b = name.toByteArray(Charsets.UTF_8)
        if (name.isEmpty() || b.size > NAME_MAX) throw OptionFileException("El nombre debe tener entre 1 y $NAME_MAX bytes.")
        val a = base(c)
        for (i in 0..NAME_MAX) of.data[a + i] = if (i < b.size) b[i] else 0
        of.data[a + 110] = 1
    }

    fun abbreviation(of: OptionFile, c: Int): String = String(of.data, base(c) + 73, 3, Charsets.US_ASCII).trimEnd('\u0000')

    fun setAbbreviation(of: OptionFile, c: Int, abbr: String) {
        val s = abbr.uppercase()
        if (s.length != 3 || s.any { it.code !in 0x20..0x7E }) throw OptionFileException("La abreviatura debe tener 3 letras o números sin acentos.")
        val b = s.toByteArray(Charsets.US_ASCII)
        System.arraycopy(b, 0, of.data, base(c) + 73, 3)
        System.arraycopy(b, 0, of.data, ABBR_COPY + c * Teams.FORM_SIZE, 3)
        of.data[base(c) + 110] = 1
    }

    /** Color como 0xRRGGBB. [which] = 1 o 2. */
    fun color(of: OptionFile, c: Int, which: Int): Int {
        val a = base(c) + if (which == 1) 124 else 128
        return (of.u8(a) shl 16) or (of.u8(a + 1) shl 8) or of.u8(a + 2)
    }

    fun setColor(of: OptionFile, c: Int, which: Int, rgb: Int) {
        val a = base(c) + if (which == 1) 124 else 128
        of.data[a] = (rgb ushr 16).toByte()
        of.data[a + 1] = (rgb ushr 8).toByte()
        of.data[a + 2] = rgb.toByte()
    }

    fun stadium(of: OptionFile, c: Int) = of.u8(base(c) + 133)

    fun setStadium(of: OptionFile, c: Int, s: Int) {
        require(s in 0 until Names.STADIUM_COUNT)
        of.data[base(c) + 133] = s.toByte()
        of.data[base(c) + 135] = 1
    }

    /** Valor de escudo guardado: club + 111 = escudo original; 284 en adelante = escudo cargado. */
    fun emblem(of: OptionFile, c: Int) = of.u16(base(c) + 112)

    fun usesOriginalEmblem(of: OptionFile, c: Int) = emblem(of, c) == c + 111

    fun setEmblem(of: OptionFile, c: Int, value: Int) {
        val a = base(c)
        of.setU16(a + 112, value)
        of.setU16(a + 116, value)
        of.data[a + 120] = 1
        of.data[a + 121] = 1
    }

    fun setOriginalEmblem(of: OptionFile, c: Int) {
        val a = base(c)
        of.setU16(a + 112, c + 111)
        of.setU16(a + 116, c + 111)
        of.data[a + 120] = 0
        of.data[a + 121] = 0
    }
}

/** Nombres de estadios (Stadia) y de ligas y copas (Leagues) de PESFan Editor 5. */
object Names {
    const val STADIUM_COUNT = 35
    private const val STADIUM_ADR = 9508
    private const val STADIUM_LEN = 61
    private const val STADIUM_FLAG = 11643

    const val LEAGUE_COUNT = 28
    private const val LEAGUE_ADR = 11699
    private const val LEAGUE_MAX = 61
    private const val LEAGUE_FIELD = 84
    private val LEAGUE_DEFAULT = listOf(
        "ISS", "England", "French", "German", "Italian", "Netherlands", "Spanish", "International", "European",
        "African", "American", "Asia-Oceanian", "Konami", "D2", "D1", "WEFA Masters", "WEFA Championships",
        "International League",
    )

    private fun cString(of: OptionFile, a: Int, max: Int): String {
        var n = 0
        while (n < max && of.data[a + n].toInt() != 0) n++
        return String(of.data, a, n, Charsets.UTF_8)
    }

    fun stadium(of: OptionFile, i: Int) = cString(of, STADIUM_ADR + i * STADIUM_LEN, STADIUM_LEN)

    fun setStadium(of: OptionFile, i: Int, name: String) {
        val b = name.toByteArray(Charsets.UTF_8)
        if (name.isEmpty() || b.size > STADIUM_LEN - 1) throw OptionFileException("El nombre debe tener entre 1 y ${STADIUM_LEN - 1} bytes.")
        val a = STADIUM_ADR + i * STADIUM_LEN
        for (k in 0 until STADIUM_LEN) of.data[a + k] = if (k < b.size) b[k] else 0
        of.data[STADIUM_FLAG + i] = 1
    }

    fun league(of: OptionFile, i: Int): String {
        val a = LEAGUE_ADR + i * LEAGUE_FIELD
        if (of.data[a].toInt() != 0) return cString(of, a, LEAGUE_MAX + 1)
        if (i > 10) return LEAGUE_DEFAULT[i - 11] + if (i < 27) " Cup" else ""
        return "<$i>"
    }

    fun setLeague(of: OptionFile, i: Int, name: String) {
        val b = name.toByteArray(Charsets.UTF_8)
        if (name.isEmpty() || b.size > LEAGUE_MAX) throw OptionFileException("El nombre debe tener entre 1 y $LEAGUE_MAX bytes.")
        val a = LEAGUE_ADR + i * LEAGUE_FIELD
        for (k in 0 until LEAGUE_MAX + 1) of.data[a + k] = if (k < b.size) b[k] else 0
        of.data[a + LEAGUE_MAX + 1] = 1
    }
}
