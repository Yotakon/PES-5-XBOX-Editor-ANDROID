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
        /** Datos comprobados por el usuario en el editor del juego. */
        fun defaults() = FaceMap(
            mapOf(
                Key(1, 68) to 126, // Víctor Valdés
                Key(3, 2) to 3,    // Ronaldo
                Key(3, 7) to 9,    // Ronaldinho
            )
        )

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
 * Pasa a cara normal a los jugadores con cara especial que tengan equivalencia en la tabla.
 * Los que no la tienen no se tocan y aparecen en el informe.
 */
fun FaceTools.removeSpecialWithMap(of: OptionFile, ff: FaceFields, ids: IntArray, map: FaceMap): List<FaceTools.Change> {
    val out = ArrayList<FaceTools.Change>()
    for (id in ids) {
        if (!Players.isValid(id) || ff.type.get(of, id) != FaceFields.SPECIAL) continue
        val f = ff.read(of, id)
        val name = Players.name(of, id)
        val n = map.get(f.skin, f.number)
        if (n == null || n > ff.face.mask + 1) {
            out += FaceTools.Change(id, name, f.type, f.number, f.type, f.number, "sin cambios: sin equivalencia (piel ${f.skin})")
            continue
        }
        ff.write(of, id, f.copy(type = FaceFields.NORMAL, number = n))
        out += FaceTools.Change(id, name, f.type, f.number, FaceFields.NORMAL, n)
    }
    return out
}
