package com.yotakon.pes5editor.core

/**
 * Relinkeo directo por número de archivo del 0_text.afs (cuenta desde 0, como en GGS y AFS Explorer).
 *
 * Así elige el juego los archivos (tablas del PES5.xbe de Xbox: TABLA_CARA en el offset 0x578E18 y
 * TABLA_PELO en 0x578A24):
 * - Archivo de FACE = base[tipo de FACE][piel] + (número de FACE - 1). El número va de 1 a 512.
 * - Archivo de HAIR = 2829 + ID de HAIR, salvo con FACE especial: entonces el juego ignora el ID de HAIR
 *   y carga siempre archivo de FACE + 2559 (el HAIR especial que va con esa FACE).
 *
 * Por eso la piel limita qué archivos de FACE se pueden cargar: cada piel empieza en una base distinta.
 */
object Relink {
    /** Bases por piel, en el orden del option file: 1 = W, 2 = Y, 3 = R, 4 = K. */
    val SPECIAL_FACE = intArrayOf(1640, 1786, 1608, 1590)
    val NORMAL_FACE = intArrayOf(1959, 2224, 1902, 1870)
    const val HAIR_BASE = 2829
    const val SPECIAL_HAIR_OFFSET = 2559

    val SPECIAL_FACES = 1590..1869
    val NORMAL_FACES = 1870..2362
    val SPECIAL_HAIRS = 4149..4428
    val ORIGINAL_HAIRS = 2829..4428
    const val MAX_NUMBER = 512
    const val MAX_HAIR_ID = 2047
    val HAIR_FILES = HAIR_BASE..HAIR_BASE + MAX_HAIR_ID

    val SKIN_NAMES = listOf("1 (W)", "2 (Y)", "3 (R)", "4 (K)")
    fun skinName(skin: Int) = SKIN_NAMES.getOrElse(skin - 1) { "$skin" }

    private fun table(type: Int) = when (type) {
        FaceFields.SPECIAL -> SPECIAL_FACE
        FaceFields.NORMAL -> NORMAL_FACE
        else -> null
    }

    /** Archivo de FACE que carga el juego, o null si la FACE es creada o de tipo desconocido. */
    fun faceFile(f: Face): Int? = table(f.type)?.getOrNull(f.skin - 1)?.plus(f.number - 1)

    /** Archivo de HAIR que carga el juego, o null si el tipo de FACE es desconocido. */
    fun hairFile(f: Face): Int? = when (f.type) {
        FaceFields.SPECIAL -> faceFile(f)?.plus(SPECIAL_HAIR_OFFSET)
        FaceFields.BUILD, FaceFields.NORMAL -> HAIR_BASE + f.hair
        else -> null
    }

    /** Tipo de FACE con el que se carga un archivo, o null si no es un archivo de FACE. */
    fun typeFor(file: Int): Int? = when (file) {
        in SPECIAL_FACES -> FaceFields.SPECIAL
        in NORMAL_FACES -> FaceFields.NORMAL
        else -> null
    }

    /** Pieles (1-4) con las que se puede cargar el archivo de FACE. */
    fun skinsFor(file: Int): List<Int> {
        val t = typeFor(file)?.let { table(it) } ?: return emptyList()
        return (1..4).filter { file - t[it - 1] in 0 until MAX_NUMBER }
    }

    data class Result(val face: Face?, val error: String? = null, val notes: List<String> = emptyList())

    /**
     * Calcula los campos para cargar el archivo de FACE [faceEntry] y el de HAIR [hairEntry] con la piel [skin].
     * - [faceEntry] null: se conserva la FACE; si solo cambia la piel, se busca el mismo archivo con la piel nueva.
     * - [hairEntry] null: se conserva el ID de HAIR.
     */
    fun plan(cur: Face, faceEntry: Int?, hairEntry: Int?, skin: Int = cur.skin): Result {
        if (skin !in 1..4) return Result(null, "La piel debe estar entre 1 y 4.")
        var f = cur.copy(skin = skin)
        val notes = ArrayList<String>()

        val target = faceEntry ?: if (skin != cur.skin) faceFile(cur) else null
        if (target != null) {
            val type = typeFor(target) ?: return Result(
                null,
                "El archivo de FACE debe estar entre ${SPECIAL_FACES.first} y ${NORMAL_FACES.last} " +
                    "(${SPECIAL_FACES.first}-${SPECIAL_FACES.last} especiales, ${NORMAL_FACES.first}-${NORMAL_FACES.last} normales).",
            )
            val n = target - table(type)!![skin - 1]
            if (n !in 0 until MAX_NUMBER) {
                val ok = skinsFor(target).joinToString { skinName(it) }
                return Result(null, "El archivo de FACE $target no se puede cargar con la piel ${skinName(skin)}. Pieles posibles: $ok.")
            }
            f = f.copy(type = type, number = n + 1)
        } else if (f.type !in 0..2) {
            return Result(null, "El tipo de FACE es desconocido (${f.type}). Indica un archivo de FACE.")
        }

        if (f.type == FaceFields.SPECIAL) {
            val forced = hairFile(f)!!
            if (hairEntry != null && hairEntry != forced) {
                return Result(
                    null,
                    "Con FACE especial el juego carga siempre el HAIR $forced, que va unido a la FACE ${faceFile(f)}. " +
                        "Para otro HAIR usa una FACE normal (${NORMAL_FACES.first}-${NORMAL_FACES.last}) " +
                        "o reemplaza el archivo $forced en el 0_text.",
                )
            }
            notes += "HAIR: archivo $forced (va unido a la FACE especial; el ID de HAIR no se usa)."
        } else {
            if (hairEntry != null) {
                if (hairEntry !in HAIR_FILES) {
                    return Result(null, "El archivo de HAIR debe estar entre ${HAIR_FILES.first} y ${HAIR_FILES.last}.")
                }
                f = f.copy(hair = hairEntry - HAIR_BASE)
            }
            val h = HAIR_BASE + f.hair
            notes += "HAIR: archivo $h (ID de HAIR ${f.hair})."
            when (h) {
                !in ORIGINAL_HAIRS -> notes += "El archivo $h no es un HAIR en el 0_text original (${ORIGINAL_HAIRS.first}-${ORIGINAL_HAIRS.last})."
                in SPECIAL_HAIRS -> notes += "Es un HAIR de FACE especial cargado por ID de HAIR: sin probar en el juego."
            }
        }
        faceFile(f)?.let { notes.add(0, "FACE: archivo $it (${FaceFields.typeName(f.type)}, piel ${skinName(f.skin)}, número ${f.number}).") }
        if (f.type == FaceFields.BUILD) notes.add(0, "FACE creada en el editor: no carga ningún archivo de FACE.")
        return Result(f, null, notes)
    }

    /**
     * Igual que [plan], pero con los archivos tal como se ven en pantalla o en el CSV:
     * - un archivo de FACE igual al que ya se carga (con la misma piel) no cuenta como cambio;
     * - si el archivo de HAIR no se ha tocado y choca con una FACE especial nueva, se deja el HAIR de esa FACE.
     */
    fun relink(cur: Face, faceEntry: Int?, hairEntry: Int?, skin: Int = cur.skin): Result {
        val fe = faceEntry?.takeIf { it != faceFile(cur) || skin != cur.skin }
        val r = plan(cur, fe, hairEntry, skin)
        if (r.face == null && hairEntry != null && hairEntry == hairFile(cur)) {
            val again = plan(cur, fe, null, skin)
            if (again.face != null) return again
        }
        return r
    }
}
