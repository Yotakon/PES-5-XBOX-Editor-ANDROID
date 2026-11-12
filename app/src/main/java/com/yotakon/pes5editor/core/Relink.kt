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
 *
 * Con el 0_text ampliado (herramientas/ampliar_caras.py) cada piel tiene al final del 0_text un bloque
 * con sus FACE normales seguidas de sus especiales, y el XBE apunta las bases de FACE normal a esos bloques.
 */
object Relink {
    /** Bases por piel, en el orden del option file: 1 = W, 2 = Y, 3 = R, 4 = K. */
    val SPECIAL_FACE = intArrayOf(1640, 1786, 1608, 1590)
    val SPECIAL_COUNT = intArrayOf(146, 84, 32, 18)
    val ORIGINAL_NORMAL_FACE = intArrayOf(1959, 2224, 1902, 1870)
    val ORIGINAL_NORMAL_COUNT = intArrayOf(265, 139, 57, 32)
    const val HAIR_BASE = 2829
    const val SPECIAL_HAIR_OFFSET = 2559

    /** Dónde están las FACE normales de cada piel y cuántas muestra el editor del juego. */
    class Layout(val label: String, val normalBase: IntArray, val normalCount: IntArray, val expanded: Boolean) {
        val files: IntRange = normalBase.minOrNull()!!..(0 until 4).maxOf { normalBase[it] + normalCount[it] - 1 }
    }

    val STANDARD = Layout("0_text original", ORIGINAL_NORMAL_FACE, ORIGINAL_NORMAL_COUNT, false)

    /** Resultado de ampliar_caras.py sobre el 0_text de PES 5 Xbox (11778 archivos). */
    val EXPANDED = Layout(
        "0_text ampliado",
        intArrayOf(11778, 12189, 12412, 12501),
        IntArray(4) { ORIGINAL_NORMAL_COUNT[it] + SPECIAL_COUNT[it] },
        true,
    )

    /** Distribución que usa la app. Se elige en Ajustes. */
    @Volatile var layout: Layout = STANDARD

    val NORMAL_FACE: IntArray get() = layout.normalBase
    val NORMAL_FACES: IntRange get() = layout.files

    val SPECIAL_FACES = 1590..1869
    val ORIGINAL_NORMAL_FACES = 1870..2362
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

    /**
     * Piel y número de la FACE especial que está en [file] (cada piel tiene su grupo:
     * K 1590-1607, R 1608-1639, W 1640-1785, Y 1786-1869), o null si no es una FACE especial.
     */
    fun specialOwner(file: Int): Pair<Int, Int>? {
        if (file !in SPECIAL_FACES) return null
        val skin = (1..4).filter { SPECIAL_FACE[it - 1] <= file }.maxByOrNull { SPECIAL_FACE[it - 1] } ?: return null
        return skin to file - SPECIAL_FACE[skin - 1] + 1
    }

    /** Número de FACE normal que es copia exacta de la FACE especial ([skin], [special]) en el 0_text ampliado. */
    fun expandedNumber(skin: Int, special: Int): Int = ORIGINAL_NORMAL_COUNT[skin - 1] + special

    /**
     * Con el 0_text ampliado: archivo del bloque nuevo que es copia de [file], si [file] es una FACE
     * especial (1590-1869) o normal (1870-2362) del 0_text original. Si no, null.
     */
    fun translate(file: Int): Int? {
        if (!layout.expanded) return null
        specialOwner(file)?.let { (skin, number) -> return NORMAL_FACE[skin - 1] + expandedNumber(skin, number) - 1 }
        if (file !in ORIGINAL_NORMAL_FACES) return null
        val skin = (1..4).firstOrNull { file - ORIGINAL_NORMAL_FACE[it - 1] in 0 until ORIGINAL_NORMAL_COUNT[it - 1] } ?: return null
        return NORMAL_FACE[skin - 1] + file - ORIGINAL_NORMAL_FACE[skin - 1]
    }

    /**
     * Archivo de la FACE normal que es copia de la FACE especial de [file]: la copia exacta con el
     * 0_text ampliado, o la más parecida según [map] con el original.
     */
    fun normalCopy(file: Int, map: FaceMap?): Int? {
        translate(file)?.let { return it }
        val (skin, number) = specialOwner(file) ?: return null
        val n = map?.get(skin, number) ?: return null
        return NORMAL_FACE[skin - 1] + n - 1
    }

    /** Pieles (1-4) con las que se puede cargar el archivo como FACE normal. */
    fun skinsFor(file: Int): List<Int> {
        val f = translate(file) ?: file
        if (f !in NORMAL_FACES) return emptyList()
        return (1..4).filter { f - NORMAL_FACE[it - 1] in 0 until MAX_NUMBER }
    }

    /** Texto con los archivos de FACE que acepta el relinkeo. */
    fun faceHint(): String =
        if (layout.expanded) "${NORMAL_FACES.first}-${NORMAL_FACES.last}, o ${SPECIAL_FACES.first}-${ORIGINAL_NORMAL_FACES.last} del 0_text original"
        else "${NORMAL_FACES.first}-${NORMAL_FACES.last}"

    data class Result(val face: Face?, val error: String? = null, val notes: List<String> = emptyList())

    /**
     * Calcula los campos para que el jugador cargue el archivo de FACE [faceEntry] y el de HAIR [hairEntry]
     * con la piel [skin]. El relinkeo nunca pone FACE especial: con ella el juego no deja editar la cara.
     * - [faceEntry] null: se conserva la FACE. Si es especial, pasa a su copia normal de [map].
     *   Si es normal y solo cambia la piel, se busca el mismo archivo con la piel nueva.
     * - [hairEntry] null: se conserva el ID de HAIR.
     * Con el 0_text original los archivos de FACE especiales (1590-1869) no se pueden cargar con FACE
     * normal; con el ampliado se usa su copia. Los de HAIR especiales (4149-4428) sí, con el ID de HAIR 1320-1599.
     */
    fun plan(cur: Face, faceEntry: Int?, hairEntry: Int?, skin: Int = cur.skin, map: FaceMap? = null): Result {
        if (skin !in 1..4) return Result(null, "La piel debe estar entre 1 y 4.")
        var f = cur.copy(skin = skin)
        val notes = ArrayList<String>()

        var target = faceEntry
        if (target == null) {
            when (cur.type) {
                FaceFields.SPECIAL -> {
                    val from = faceFile(cur)
                    target = from?.let { normalCopy(it, map) } ?: return Result(
                        null,
                        "El jugador tiene FACE especial${from?.let { " (archivo $it)" }.orEmpty()} y no hay copia normal en la tabla. " +
                            "Indica un archivo de FACE normal (${faceHint()}).",
                    )
                    notes += "La FACE especial $from pasa a su copia normal $target."
                }
                FaceFields.NORMAL -> if (skin != cur.skin) target = faceFile(cur)
                FaceFields.BUILD -> {}
                else -> return Result(null, "El tipo de FACE es desconocido (${cur.type}). Indica un archivo de FACE.")
            }
        }
        if (target != null) {
            var t: Int = target
            val moved = translate(t)
            if (moved != null) {
                notes += "El archivo $t está copiado en el $moved del 0_text ampliado."
                t = moved
            }
            if (t in SPECIAL_FACES) {
                val copy = normalCopy(t, map)
                return Result(
                    null,
                    "El archivo $t está en la zona de FACE especiales (${SPECIAL_FACES.first}-${SPECIAL_FACES.last}). " +
                        "Con FACE normal el juego no llega a esos archivos (la primera FACE normal es la ${NORMAL_FACES.first})." +
                        (copy?.let { " Su copia normal es el archivo $it." } ?: "") +
                        " Para usar ese modelo, ponlo en un archivo de FACE normal del 0_text o usa el 0_text ampliado.",
                )
            }
            if (t !in NORMAL_FACES) {
                return Result(null, "El archivo de FACE debe estar entre ${faceHint()}.")
            }
            val n = t - NORMAL_FACE[skin - 1]
            if (n !in 0 until MAX_NUMBER) {
                val ok = skinsFor(t).joinToString { skinName(it) }
                return Result(null, "El archivo de FACE $t no se puede cargar con la piel ${skinName(skin)}. Pieles posibles: $ok.")
            }
            if (n >= layout.normalCount[skin - 1]) {
                notes += "El número ${n + 1} no está en la lista de FACE del editor del juego para la piel ${skinName(skin)} " +
                    "(1-${layout.normalCount[skin - 1]}): en el partido se carga, pero no lo cambies desde el editor del juego."
            }
            f = f.copy(type = FaceFields.NORMAL, number = n + 1)
        }

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
            in SPECIAL_HAIRS -> notes += "Está en la zona de HAIR especiales y se carga como HAIR normal: sin probar en el juego."
        }
        when (f.type) {
            FaceFields.NORMAL -> notes.add(0, "FACE: archivo ${faceFile(f)} (normal, piel ${skinName(f.skin)}, número ${f.number}).")
            FaceFields.BUILD -> notes.add(0, "FACE creada en el editor: no carga ningún archivo de FACE.")
        }
        return Result(f, null, notes)
    }

    /**
     * Igual que [plan], con los archivos tal como se ven en pantalla o en el CSV: un archivo de FACE igual
     * al que ya se carga no cuenta como cambio (salvo una FACE normal con piel nueva).
     */
    fun relink(cur: Face, faceEntry: Int?, hairEntry: Int?, skin: Int = cur.skin, map: FaceMap? = null): Result {
        val fe = faceEntry?.takeIf { it != faceFile(cur) || (skin != cur.skin && cur.type == FaceFields.NORMAL) }
        return plan(cur, fe, hairEntry, skin, map)
    }
}
