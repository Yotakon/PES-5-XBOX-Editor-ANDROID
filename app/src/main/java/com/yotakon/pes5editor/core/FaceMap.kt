package com.yotakon.pes5editor.core

/**
 * Tabla de equivalencias: para cada cara especial (color de piel + número), el número de la cara
 * normal que es su copia. Las caras especiales se numeran por separado en cada color de piel.
 */
class FaceMap(entries: Map<Key, Int> = emptyMap()) {
    data class Key(val skin: Int, val special: Int)

    private val map = LinkedHashMap(entries)

    val size get() = map.size
    fun get(skin: Int, special: Int): Int? = map[Key(skin, special)]

    fun set(skin: Int, special: Int, normal: Int?) {
        if (normal == null) map.remove(Key(skin, special)) else map[Key(skin, special)] = normal
    }

    fun entries(): List<Pair<Key, Int>> = map.entries.sortedWith(compareBy({ it.key.skin }, { it.key.special })).map { it.key to it.value }

    /** Formato de texto (también CSV): PIEL;ESPECIAL;NORMAL por línea. */
    fun serialize(): String = buildString {
        append("PIEL;ESPECIAL;NORMAL\r\n")
        for ((k, v) in entries()) append("${k.skin};${k.special};$v\r\n")
    }

    companion object {
        /**
         * Tabla sacada del 0_text.afs de PES 5 Xbox comparando la textura de cada cara especial con las
         * normales de su misma piel. Comprobada en el juego con Valdés, Ronaldo, Ronaldinho, Kaká y Drogba.
         */
        fun defaults(): FaceMap {
            val text = FaceMap::class.java.classLoader?.getResourceAsStream("equivalencias_caras.csv")
                ?.use { String(it.readBytes(), Charsets.UTF_8) }
            val m = if (text != null) parse(text) else FaceMap()
            // Datos comprobados por el usuario (siempre presentes)
            m.set(1, 68, 126) // Víctor Valdés
            m.set(3, 2, 3)    // Ronaldo
            m.set(3, 7, 9)    // Ronaldinho
            m.set(2, 3, 9)    // Kaká
            m.set(4, 1, 2)    // Drogba
            return m
        }

        /** Lee PIEL;ESPECIAL;NORMAL (separador ; o ,). Ignora la cabecera y las líneas no válidas. */
        fun parse(text: String): FaceMap {
            val m = FaceMap()
            for (line in text.removePrefix("\uFEFF").lines()) {
                val p = line.split(';', ',').map { it.trim() }
                if (p.size < 3) continue
                val s = p[0].toIntOrNull() ?: continue
                val e = p[1].toIntOrNull() ?: continue
                val n = p[2].toIntOrNull() ?: continue
                if (s in 1..4 && e in 1..512 && n in 1..512) m.set(s, e, n)
            }
            return m
        }
    }
}

/** Una cara especial usada en el option file, con los jugadores que la llevan. */
data class SpecialFace(val skin: Int, val number: Int, val players: List<Int>)

fun FaceFields.specialFaces(of: OptionFile): List<SpecialFace> {
    val g = LinkedHashMap<FaceMap.Key, MutableList<Int>>()
    for (id in Players.allIds) {
        val f = read(of, id)
        if (f.type == FaceFields.SPECIAL) g.getOrPut(FaceMap.Key(f.skin, f.number)) { ArrayList() } += id
    }
    return g.entries.map { SpecialFace(it.key.skin, it.key.special, it.value) }
        .sortedWith(compareBy({ it.skin }, { it.number }))
}

/**
 * Pasa a cara normal a los jugadores con cara especial que tengan equivalencia en la tabla
 * (o su copia exacta, con el 0_text ampliado).
 * Los que no la tienen no se tocan y aparecen en el informe.
 */
fun FaceTools.removeSpecialWithMap(of: OptionFile, ff: FaceFields, ids: IntArray, map: FaceMap): List<FaceTools.Change> {
    val out = ArrayList<FaceTools.Change>()
    for (id in ids) {
        if (!Players.isValid(id) || ff.type.get(of, id) != FaceFields.SPECIAL) continue
        val f = ff.read(of, id)
        val name = Players.name(of, id)
        // Con el 0_text ampliado cada FACE especial tiene una copia exacta entre las normales de su piel
        val n = if (Relink.layout.expanded && f.skin in 1..4 && f.number <= Relink.SPECIAL_COUNT[f.skin - 1]) {
            Relink.expandedNumber(f.skin, f.number)
        } else {
            map.get(f.skin, f.number)
        }
        if (n == null || n > ff.face.mask + 1) {
            out += FaceTools.Change(id, name, f.type, f.number, f.type, f.number, "sin cambios: sin equivalencia (piel ${f.skin})")
            continue
        }
        ff.write(of, id, f.copy(type = FaceFields.NORMAL, number = n))
        out += FaceTools.Change(id, name, f.type, f.number, FaceFields.NORMAL, n)
    }
    return out
}
