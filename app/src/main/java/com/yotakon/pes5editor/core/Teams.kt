package com.yotakon.pes5editor.core

/**
 * Plantillas, dorsales y formaciones. Direcciones de PESFan Editor 5
 * (SquadList, SquadNumList, FormFixer, Clubs).
 *
 * Índice de plantilla:
 *   0-56    selecciones           23 jugadores, con formación
 *   57-63   clásicas              23, con formación
 *   64-71   Japón / editadas      23, sin formación
 *   72      editada               14, sin formación
 *   73      editada               32, sin formación
 *   74-211  clubes                32, con formación
 *   212-216 ML y tienda           32, sin formación
 */
object Teams {
    const val COUNT = 217
    const val IDS_23 = 664054
    const val IDS_32 = 667458
    const val NUMS_23 = 657712
    const val NUMS_32 = 659414
    const val FORM_BASE = 676744 // 670512 + 6232
    const val FORM_SIZE = 628
    const val CLUB_BASE = 803608
    const val CLUB_SIZE = 140
    const val CLUB_COUNT = 138
    const val CLUB_NAME_SIZE = 80

    val NATIONS = listOf(
        "Austria", "Belgium", "Bulgaria", "Croatia", "Czech Republic", "Denmark", "England", "Finland", "France",
        "Germany", "Greece", "Hungary", "Ireland", "Italy", "Latvia", "Netherlands", "Northern Ireland", "Norway",
        "Poland", "Portugal", "Romania", "Russia", "Scotland", "Serbia and Montenegro", "Slovakia", "Slovenia",
        "Spain", "Sweden", "Switzerland", "Turkey", "Ukraine", "Wales", "Cameroon", "Cote d'Ivoire", "Morocco",
        "Nigeria", "Senegal", "South Africa", "Tunisia", "Costa Rica", "Mexico", "USA", "Argentina", "Brazil",
        "Chile", "Colombia", "Ecuador", "Paraguay", "Peru", "Uruguay", "Venezuela", "China", "Iran", "Japan",
        "Saudi Arabia", "South Korea", "Australia",
    )

    val EXTRA = listOf(
        "Classic Argentina", "Classic Brazil", "Classic England", "Classic France", "Classic Germany",
        "Classic Italy", "Classic Netherlands", "<Japan 1>", "<Japan 2>", "<Edited> National 1",
        "<Edited> National 2", "<Edited> National 3", "<Edited> National 4", "<Edited> National 5",
        "<Edited> National 6", "<Edited> National 7", "<Edited>", "<ML Default>", "<Shop 1>", "<Shop 2>",
        "<Shop 3>", "<Shop 4>", "<ML Default 2>",
    )

    fun hasFormation(t: Int) = t in 0..63 || t in 74..211
    fun isClub(t: Int) = t in 74..211

    fun slots(t: Int) = when {
        t < 72 -> 23
        t == 72 -> 14
        else -> 32
    }

    fun idsAdr(t: Int) = if (t <= 72) IDS_23 + t * 46 else IDS_32 + (t - 74) * 64
    fun numsAdr(t: Int) = if (t <= 72) NUMS_23 + t * 23 else NUMS_32 + (t - 74) * 32
    fun formAdr(t: Int) = FORM_BASE + FORM_SIZE * (if (t < 64) t else t - 10)
    fun clubAdr(t: Int) = CLUB_BASE + CLUB_SIZE * (t - 74)

    fun clubName(of: OptionFile, club: Int): String {
        val a = CLUB_BASE + club * CLUB_SIZE
        if (of.data[a].toInt() == 0) return "<$club>"
        var len = 0
        while (len < 49 && of.data[a + len].toInt() != 0) len++
        return String(of.data, a, len, Charsets.UTF_8)
    }

    fun name(of: OptionFile, t: Int): String = when {
        t < 57 -> NATIONS[t]
        t < 74 -> EXTRA[t - 57]
        t < 212 -> clubName(of, t - 74)
        else -> EXTRA.getOrElse(t - 212 + 17) { "<$t>" }
    }

    /** IDs de los jugadores de la plantilla, en el orden guardado. */
    fun squadIds(of: OptionFile, t: Int): IntArray {
        val a = idsAdr(t)
        return IntArray(slots(t)) { of.u16(a + it * 2) }
    }

    /** Dorsal mostrado (1-99) o null si no tiene ("##" en PESFan). */
    fun shirtNumber(of: OptionFile, t: Int, slot: Int): Int? {
        val v = of.u8(numsAdr(t) + slot) + 1
        return if (v == 256) null else v
    }

    /**
     * Ordena la plantilla según la tabla de orden de su formación y deja esa tabla en 0..31
     * (FormFixer.fixForm). PESFan lo hace al abrir cada option file.
     */
    fun fixFormation(of: OptionFile, t: Int) {
        if (!hasFormation(t)) return
        val n = slots(t)
        val ids = idsAdr(t)
        val nums = numsAdr(t)
        val form = formAdr(t)
        val oldIds = of.data.copyOfRange(ids, ids + 64.coerceAtMost(n * 2))
        val oldNums = of.data.copyOfRange(nums, nums + n)
        val idsBuf = ByteArray(64).also { System.arraycopy(oldIds, 0, it, 0, oldIds.size) }
        val numsBuf = ByteArray(32).also { System.arraycopy(oldNums, 0, it, 0, oldNums.size) }
        for (i in 0 until n) {
            val src = of.u8(form + i)
            System.arraycopy(idsBuf, src * 2, of.data, ids + i * 2, 2)
            System.arraycopy(numsBuf, src, of.data, nums + i, 1)
        }
        // Capitán y lanzadores (6 bytes en +102) apuntan a posiciones de la tabla de orden
        for (k in 0 until 6) {
            val ref = form + 102 + k
            for (i in 0 until 32) {
                if (of.data[form + i] == of.data[ref]) {
                    of.data[ref] = i.toByte()
                    break
                }
            }
        }
        for (i in 0 until 32) of.data[form + i] = i.toByte()
    }

    fun fixAllFormations(of: OptionFile) {
        for (t in 0 until 212) fixFormation(of, t)
    }
}
