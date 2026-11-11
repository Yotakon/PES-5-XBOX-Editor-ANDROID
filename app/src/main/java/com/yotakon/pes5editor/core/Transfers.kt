package com.yotakon.pes5editor.core

/**
 * Transferencias entre plantillas. Misma lógica que TransferPanel y SquadTidy de PESFan Editor 5:
 * cada plantilla tiene una lista de IDs (2 bytes por hueco) y una de dorsales (1 byte, 0xFF = sin dorsal).
 * Los 11 primeros huecos son los titulares; los suplentes se compactan después de cada cambio.
 */
object Transfers {
    private const val NO_NUMBER = 0xFF
    private const val CLUB_SLOTS_END = Teams.IDS_32 + 138 * 64

    fun playerAt(of: OptionFile, t: Int, slot: Int) = of.u16(Teams.idsAdr(t) + slot * 2)

    private fun setPlayer(of: OptionFile, t: Int, slot: Int, id: Int) = of.setU16(Teams.idsAdr(t) + slot * 2, id)

    private fun numAdr(t: Int, slot: Int) = Teams.numsAdr(t) + slot

    /** Dorsal guardado (0-98) o null. */
    fun rawNumber(of: OptionFile, t: Int, slot: Int): Int? = of.u8(numAdr(t, slot)).takeIf { it != NO_NUMBER }

    fun inSquad(of: OptionFile, t: Int, id: Int): Boolean =
        id != 0 && (0 until Teams.slots(t)).any { playerAt(of, t, it) == id }

    /** Primer dorsal libre (valor guardado, 0 = dorsal 1), como getNextNum. */
    fun nextFreeNumber(of: OptionFile, t: Int): Int {
        val used = (0 until Teams.slots(t)).map { of.u8(numAdr(t, it)) }.toSet()
        return (0 until 99).firstOrNull { it !in used } ?: 0
    }

    /** Cambia el dorsal (1-99). Solo si el hueco ya tiene dorsal, igual que PESFan. */
    fun setNumber(of: OptionFile, t: Int, slot: Int, number: Int) {
        require(number in 1..99) { "El dorsal va de 1 a 99" }
        if (rawNumber(of, t, slot) == null) throw OptionFileException("Ese hueco no tiene dorsal.")
        of.data[numAdr(t, slot)] = (number - 1).toByte()
    }

    /** Junta los suplentes (huecos 11 en adelante) y deja los huecos vacíos al final sin dorsal. */
    fun tidy(of: OptionFile, t: Int) {
        if (!Teams.hasFormation(t)) return
        val n = Teams.slots(t)
        val ids = ArrayList<Int>()
        val nums = ArrayList<Int>()
        for (s in 11 until n) {
            val id = playerAt(of, t, s)
            if (id != 0) {
                ids += id
                nums += of.u8(numAdr(t, s))
            }
        }
        for (s in 11 until n) {
            val k = s - 11
            setPlayer(of, t, s, if (k < ids.size) ids[k] else 0)
            of.data[numAdr(t, s)] = (if (k < nums.size) nums[k] else NO_NUMBER).toByte()
        }
    }

    /** Huecos sin jugador pierden el dorsal; jugadores sin dorsal reciben el primero libre. */
    private fun fixNumbers(of: OptionFile, t: Int, slot: Int) {
        if (playerAt(of, t, slot) == 0) {
            of.data[numAdr(t, slot)] = NO_NUMBER.toByte()
        } else if (rawNumber(of, t, slot) == null) {
            of.data[numAdr(t, slot)] = nextFreeNumber(of, t).toByte()
        }
    }

    /**
     * Intercambia los jugadores de dos huecos (de la misma plantilla o de dos distintas).
     * Si uno de los huecos está vacío, el jugador se mueve.
     */
    fun swap(of: OptionFile, tA: Int, sA: Int, tB: Int, sB: Int) {
        val a = playerAt(of, tA, sA)
        val b = playerAt(of, tB, sB)
        if (a == 0 && b == 0) return
        if (tA != tB) {
            if (a != 0 && inSquad(of, tB, a)) throw OptionFileException("${Players.name(of, a)} ya está en ${Teams.name(of, tB)}.")
            if (b != 0 && inSquad(of, tA, b)) throw OptionFileException("${Players.name(of, b)} ya está en ${Teams.name(of, tA)}.")
        }
        setPlayer(of, tA, sA, b)
        setPlayer(of, tB, sB, a)
        if (tA == tB) {
            // misma plantilla: el dorsal viaja con el jugador
            val na = of.data[numAdr(tA, sA)]
            of.data[numAdr(tA, sA)] = of.data[numAdr(tB, sB)]
            of.data[numAdr(tB, sB)] = na
        } else {
            fixNumbers(of, tA, sA)
            fixNumbers(of, tB, sB)
        }
        tidy(of, tA)
        if (tB != tA) tidy(of, tB)
    }

    /**
     * Pone al jugador [id] en el hueco indicado (reemplaza al que había, que queda sin equipo).
     * Si el destino es un club y [releaseFromClubs] está activo, primero lo quita de cualquier otro club.
     * @return los clubes de los que se liberó.
     */
    fun sign(of: OptionFile, id: Int, t: Int, slot: Int, releaseFromClubs: Boolean): List<Int> {
        if (!Players.isValid(id)) throw OptionFileException("Jugador no válido.")
        if (playerAt(of, t, slot) == id) return emptyList()
        if (inSquad(of, t, id)) throw OptionFileException("${Players.name(of, id)} ya está en ${Teams.name(of, t)}.")
        val released = if (releaseFromClubs && Teams.isClub(t)) releaseFromAllClubs(of, id) else emptyList()
        setPlayer(of, t, slot, id)
        fixNumbers(of, t, slot)
        tidy(of, t)
        return released
    }

    /** Quita al jugador del hueco (queda vacío y sin dorsal). */
    fun release(of: OptionFile, t: Int, slot: Int) {
        setPlayer(of, t, slot, 0)
        of.data[numAdr(t, slot)] = NO_NUMBER.toByte()
        tidy(of, t)
    }

    /** Quita al jugador de todos los clubes (clubRelease). */
    fun releaseFromAllClubs(of: OptionFile, id: Int): List<Int> {
        val out = ArrayList<Int>()
        var a = Teams.IDS_32
        while (a < CLUB_SLOTS_END) {
            if (of.u16(a) == id) {
                val t = (a - Teams.IDS_32) / 64 + 74
                val slot = (a - Teams.IDS_32) % 64 / 2
                release(of, t, slot)
                out += t
            }
            a += 2
        }
        return out
    }

    /** Clubes en los que está el jugador. */
    fun clubsOf(of: OptionFile, id: Int): List<Int> = (74 until 212).filter { inSquad(of, it, id) }
}
