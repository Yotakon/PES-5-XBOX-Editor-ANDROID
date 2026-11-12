package com.yotakon.pes5editor.core

/**
 * Campo de bits dentro de los datos de un jugador.
 * Igual que Stat.java de PESFan Editor 5: se leen 2 bytes en (inicio + 48 + offset - 1, inicio + 48 + offset),
 * el segundo es el alto.
 */
data class Field(val offset: Int, val shift: Int, val mask: Int) {
    init {
        require(offset in 1..75) { "posición fuera del registro del jugador" }
        require(shift >= 0 && mask > 0 && (mask.toLong() shl shift) <= 0xFFFF) { "el campo no cabe en 16 bits" }
    }

    fun get(of: OptionFile, id: Int): Int {
        val a = Players.statAdr(id) + offset
        val v = (of.u8(a) shl 8) or of.u8(a - 1)
        return (v ushr shift) and mask
    }

    fun set(of: OptionFile, id: Int, value: Int) {
        val a = Players.statAdr(id) + offset
        var v = (of.u8(a) shl 8) or of.u8(a - 1)
        v = v and (mask shl shift).inv() and 0xFFFF
        v = v or ((value and mask) shl shift)
        of.data[a - 1] = v.toByte()
        of.data[a] = (v ushr 8).toByte()
    }

    companion object {
        /** "60,2,3" -> Field(60, 2, 3) */
        fun parse(s: String): Field {
            val p = s.split(",").map { it.trim().toInt() }
            require(p.size == 3) { "formato: posición,desplazamiento,máscara" }
            return Field(p[0], p[1], p[2])
        }
    }
}

object Players {
    const val RECORD = 124
    const val BASE = 36872        // jugadores 1..4999
    const val BASE_EDITED = 14044 // jugadores editados 32768..32951
    const val FIRST_EDITED = 32768
    const val EDITED_COUNT = 184

    fun isValid(id: Int) = id in 1..4999 || id in FIRST_EDITED until FIRST_EDITED + EDITED_COUNT

    fun recordAdr(id: Int): Int =
        if (id > 4999) BASE_EDITED + (id - FIRST_EDITED) * RECORD else BASE + id * RECORD

    fun statAdr(id: Int) = recordAdr(id) + 48

    /**
     * Nombres de los jugadores que el option file deja vacíos: el juego los toma de su tabla WEPLDATA
     * (archivo 31 del 0_text.afs de PES 5 Xbox). Se incluyen en la app.
     */
    val REAL_NAMES: Map<Int, String> by lazy {
        val text = Players::class.java.classLoader?.getResourceAsStream("nombres_reales.csv")
            ?.use { String(it.readBytes(), Charsets.UTF_8) } ?: return@lazy emptyMap()
        text.lines().drop(1).mapNotNull { l ->
            val p = l.split(';')
            val id = p.getOrNull(0)?.toIntOrNull()
            val n = p.getOrNull(1)?.trim().orEmpty()
            if (id != null && n.isNotEmpty()) id to n else null
        }.toMap()
    }

    val allIds: IntArray by lazy {
        IntArray(4999 + EDITED_COUNT) { if (it < 4999) it + 1 else FIRST_EDITED + (it - 4999) }
    }

    /** Nombre (32 bytes UTF-16LE al inicio del registro), con los mismos textos que PESFan para los vacíos. */
    fun name(of: OptionFile, id: Int): String {
        if (id == 0) return "<vacío>"
        val a = recordAdr(id)
        var len = 0
        while (len < 30 && !(of.data[a + len].toInt() == 0 && of.data[a + len + 1].toInt() == 0)) len += 2
        val n = String(of.data, a, len, Charsets.UTF_16LE)
        return when {
            n.isNotEmpty() -> n
            id in REAL_NAMES -> REAL_NAMES.getValue(id)
            id > 4999 -> "<Editado ${id - FIRST_EDITED}>"
            id > 4895 -> "<Sin usar $id>"
            else -> "<L $id>"
        }
    }
}

/**
 * Campos de cara y pelo. PESFan Editor 5 no los edita: las posiciones vienen de
 * PES5_WE9_LE_OF_Team_Editor (moth1995, player_data.py) y se pueden cambiar en Ajustes.
 */
data class FaceFields(
    val type: Field = Field(60, 2, 3),
    val face: Field = Field(53, 3, 0x1FF),
    val skin: Field = Field(43, 1, 3),
    val hair: Field = Field(45, 0, 0x7FF),
) {
    companion object {
        const val BUILD = 0
        const val SPECIAL = 1
        const val NORMAL = 2
        val TYPE_NAMES = listOf("Creada en el editor", "Especial", "Normal (predefinida)")

        fun typeName(t: Int) = TYPE_NAMES.getOrElse(t) { "Desconocido ($t)" }
    }
}

/** Valores de cara de un jugador, con el número de cara y la piel como en el juego (desde 1). */
data class Face(val type: Int, val number: Int, val skin: Int, val hair: Int)

fun FaceFields.read(of: OptionFile, id: Int) =
    Face(type.get(of, id), face.get(of, id) + 1, skin.get(of, id) + 1, hair.get(of, id))

/** Escribe solo los campos que cambian. */
fun FaceFields.write(of: OptionFile, id: Int, f: Face) {
    val old = read(of, id)
    if (f.type != old.type) type.set(of, id, f.type)
    if (f.number != old.number) face.set(of, id, f.number - 1)
    if (f.skin != old.skin) skin.set(of, id, f.skin - 1)
    if (f.hair != old.hair) hair.set(of, id, f.hair)
}
