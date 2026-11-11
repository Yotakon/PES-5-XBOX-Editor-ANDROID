package com.yotakon.pes5editor.core

/**
 * Uniformes (Kits de PESFan Editor 5): un bloque por equipo, 544 bytes en los 138 clubes y 352 en las
 * 64 selecciones. PESFan no edita los colores del uniforme por separado (su estructura no está
 * documentada); lo que sí se conoce son los 4 logos que lleva cada uniforme (+256 + n*24):
 * +2 = 1 si el logo se usa, +3 = número de logo (0-79; 88 = ninguno).
 */
object Kits {
    private const val CLUB_START = 845468
    private const val CLUB_SIZE = 544
    private const val NATION_START = 822940
    private const val NATION_SIZE = 352
    const val LOGO_SLOTS = 4
    private const val NO_LOGO = 88

    /** Equipos con uniforme: selecciones 0-63 y clubes 74-211 (índices de [Teams]). */
    fun hasKit(t: Int) = t in 0..63 || t in 74..211

    private fun adr(t: Int) = if (t >= 74) CLUB_START + (t - 74) * CLUB_SIZE else NATION_START + t * NATION_SIZE
    fun size(t: Int) = if (t >= 74) CLUB_SIZE else NATION_SIZE

    private fun logoAdr(t: Int, n: Int) = adr(t) + 256 + n * 24

    /** Logo del hueco [n] o null si no lleva. */
    fun logo(of: OptionFile, t: Int, n: Int): Int? {
        val a = logoAdr(t, n)
        val v = of.u8(a + 3)
        return if (of.u8(a + 2) == 1 && v < Logos.COUNT) v else null
    }

    fun setLogo(of: OptionFile, t: Int, n: Int, logo: Int?) {
        require(n in 0 until LOGO_SLOTS)
        val a = logoAdr(t, n)
        if (logo == null) {
            of.data[a + 2] = 0
            of.data[a + 3] = NO_LOGO.toByte()
        } else {
            require(logo in 0 until Logos.COUNT)
            of.data[a + 2] = 1
            of.data[a + 3] = logo.toByte()
        }
    }

    /** Copia el uniforme de [src] en [dst] dentro del mismo archivo (mismos logos). */
    fun copy(of: OptionFile, src: Int, dst: Int) {
        if ((src >= 74) != (dst >= 74)) throw OptionFileException("Solo se puede copiar de club a club o de selección a selección.")
        System.arraycopy(of.data, adr(src), of.data, adr(dst), size(src))
    }

    data class ImportResult(val logosCopied: Int, val logosDropped: Int, val emblemCopied: Boolean, val warnings: List<String>)

    /**
     * Importa el uniforme del equipo [src] de otro option file en [dst] (ImportKit de PESFan).
     * Los logos del uniforme se copian a huecos de logo libres de este archivo; si no hay sitio, se
     * quitan del uniforme. En clubes también se puede traer el escudo y los datos del club.
     */
    fun importFrom(of: OptionFile, dst: Int, other: OptionFile, src: Int, withEmblem: Boolean, withClubData: Boolean): ImportResult {
        if (!hasKit(dst) || !hasKit(src)) throw OptionFileException("Ese equipo no tiene uniforme.")
        if ((src >= 74) != (dst >= 74)) throw OptionFileException("Solo se puede importar de club a club o de selección a selección.")
        val warnings = ArrayList<String>()
        System.arraycopy(other.data, adr(src), of.data, adr(dst), size(src))

        // Logos: cada logo distinto del uniforme va a un hueco libre de este archivo
        var copied = 0
        var dropped = 0
        val remap = HashMap<Int, Int?>()
        val free = (0 until Logos.COUNT).filter { !Logos.isUsed(of, it) }.toMutableList()
        for (n in 0 until LOGO_SLOTS) {
            val l = logo(other, src, n) ?: continue
            val target = remap.getOrPut(l) {
                if (free.isEmpty()) null else free.removeAt(0).also {
                    Logos.set(of, it, Logos.image(other, l))
                    copied++
                }
            }
            if (target == null) dropped++
            setLogo(of, dst, n, target)
        }
        if (dropped > 0) warnings += "No quedaban huecos de logo libres: $dropped logos se quitaron del uniforme."

        var emblem = false
        if (dst >= 74) {
            val dc = dst - 74
            val sc = src - 74
            if (withClubData) {
                System.arraycopy(other.data, Teams.CLUB_BASE + sc * Teams.CLUB_SIZE, of.data, Teams.CLUB_BASE + dc * Teams.CLUB_SIZE, Teams.CLUB_SIZE)
                // El escudo guardado en el club se resuelve abajo
                ClubData.setOriginalEmblem(of, dc)
            }
            if (withEmblem) {
                val value = ClubData.emblem(other, sc)
                val e = Emblems.list(other).firstOrNull { it.value == value }
                if (e != null) {
                    val kind = e.kind
                    val canAdd = if (kind == Emblems.Kind.C128) Emblems.free128(of) > 0 else Emblems.free16(of) > 0
                    if (canAdd) {
                        ClubData.setEmblem(of, dc, Emblems.add(of, kind, Emblems.image(other, kind, e.slot)))
                        emblem = true
                    } else {
                        warnings += "No queda sitio para escudos: el club conserva su escudo."
                    }
                } else if (value == sc + 111) {
                    // Escudo original del juego del club de origen
                    if (sc == dc) ClubData.setOriginalEmblem(of, dc) else ClubData.setEmblem(of, dc, value)
                    emblem = true
                }
            }
        }
        return ImportResult(copied, dropped, emblem, warnings)
    }
}
