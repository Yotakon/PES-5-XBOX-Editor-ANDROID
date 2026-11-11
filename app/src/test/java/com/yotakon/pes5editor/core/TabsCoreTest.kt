package com.yotakon.pes5editor.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TabsCoreTest {

    private fun realSave(): OptionFile =
        OptionFile.readXsv(javaClass.classLoader!!.getResourceAsStream("KONAMI-XBOX-PES5OPT.xsv")!!.readBytes())

    @Test
    fun readsRealNames() {
        val of = realSave()
        assertEquals("Arsenal", ClubData.name(of, 0))
        assertEquals("ASN", ClubData.abbreviation(of, 0))
        assertEquals("Club House", Names.stadium(of, 0))
        assertEquals("Serie A", Names.league(of, 4))
        assertTrue(ClubData.usesOriginalEmblem(of, 0))
        assertEquals(0, Emblems.count128(of))
        assertEquals(100, Emblems.free16(of))
    }

    @Test
    fun editClubAndNames() {
        val of = realSave()
        ClubData.setName(of, 1, "Aston Villa FC")
        ClubData.setAbbreviation(of, 1, "avl")
        ClubData.setColor(of, 1, 2, 0x7A003C)
        ClubData.setStadium(of, 1, 7)
        Names.setStadium(of, 3, "Villa Park")
        Names.setLeague(of, 4, "Serie A TIM")
        val back = OptionFile.readXsv(of.toXsv())
        assertEquals("Aston Villa FC", ClubData.name(back, 1))
        assertEquals("AVL", ClubData.abbreviation(back, 1))
        assertEquals(0x7A003C, ClubData.color(back, 1, 2))
        assertEquals(7, ClubData.stadium(back, 1))
        assertEquals("Villa Park", Names.stadium(back, 3))
        assertEquals("Serie A TIM", Names.league(back, 4))
        // No pisa el club siguiente
        assertEquals("West Midlands City", ClubData.name(back, 2))
        try {
            ClubData.setAbbreviation(of, 1, "AB")
            fail()
        } catch (e: OptionFileException) {
        }
    }

    @Test
    fun transfersBetweenClubs() {
        val of = realSave()
        val a = 74
        val b = 75
        val p = Transfers.playerAt(of, a, 4)
        val q = Transfers.playerAt(of, b, 4)
        Transfers.swap(of, a, 4, b, 4)
        assertEquals(q, Transfers.playerAt(of, a, 4))
        assertEquals(p, Transfers.playerAt(of, b, 4))

        // Fichar a p en otro club con liberación automática: deja de estar en b
        val c = 80
        Transfers.sign(of, p, c, 12, releaseFromClubs = true)
        assertTrue(Transfers.inSquad(of, c, p))
        assertFalse(Transfers.inSquad(of, b, p))
        assertEquals(listOf(c), Transfers.clubsOf(of, p))
        assertEquals(0, Transfers.playerAt(of, b, 4))
        assertNull(Transfers.rawNumber(of, b, 4))
        assertNotNull(Transfers.rawNumber(of, c, Teams.squadIds(of, c).indexOf(p)))

        // No se puede repetir jugador en una plantilla
        try {
            Transfers.sign(of, p, c, 0, releaseFromClubs = false)
            fail()
        } catch (e: OptionFileException) {
        }
    }

    @Test
    fun releaseCompactsSubs() {
        val of = realSave()
        val t = 74
        val before = Teams.squadIds(of, t).toList()
        Transfers.release(of, t, 12)
        val after = Teams.squadIds(of, t).toList()
        assertEquals(before.subList(0, 12) + before.subList(13, 32) + 0, after)
        assertNull(Transfers.rawNumber(of, t, 31))
    }

    @Test
    fun quantizerKeepsTransparencyAndColors() {
        val w = 64
        val px = IntArray(w * w) { i ->
            when {
                i % w < 8 -> 0x00000000
                i % w < 32 -> 0xFFFF0000.toInt()
                else -> 0xFF0000FF.toInt()
            }
        }
        val img = Quantizer.quantize(px, w, w, 16)
        assertEquals(16, img.palette.size)
        assertEquals(0, img.pixels[0])
        assertEquals(0xFFFF0000.toInt(), img.palette[img.pixels[10]])
        assertEquals(0xFF0000FF.toInt(), img.palette[img.pixels[40]])
        // Muchos colores: no más de los permitidos
        val grad = IntArray(w * w) { (0xFF shl 24) or (it % 256 shl 16) or ((it / 256) * 16 shl 8) or 77 }
        val q = Quantizer.quantize(grad, w, w, 128)
        assertTrue(q.pixels.all { it in 1..127 })
    }

    private fun solid(color: Int, colors: Int, size: Int): IndexedImage =
        Quantizer.quantize(IntArray(size * size) { color }, size, size, colors)

    @Test
    fun emblemsAddAssignDelete() {
        val of = realSave()
        val v1 = Emblems.add(of, Emblems.Kind.C128, solid(0xFFFF0000.toInt(), 128, 64))
        val v2 = Emblems.add(of, Emblems.Kind.C16, solid(0xFF00FF00.toInt(), 16, 64))
        val v3 = Emblems.add(of, Emblems.Kind.C128, solid(0xFF0000FF.toInt(), 128, 64))
        assertEquals(2, Emblems.count128(of))
        assertEquals(1, Emblems.count16(of))
        assertEquals(100 - 4 - 1, Emblems.free16(of))
        ClubData.setEmblem(of, 0, v3)
        ClubData.setEmblem(of, 1, v1)

        val back = OptionFile.readXsv(of.toXsv())
        assertEquals(0xFF0000FF.toInt(), Emblems.imageForValue(back, ClubData.emblem(back, 0))!!.toArgb()[0])
        assertEquals(0xFF00FF00.toInt(), Emblems.imageForValue(back, v2)!!.toArgb()[100])

        // Borrar el primero: el club 1 vuelve al escudo original y el club 0 sigue viendo el suyo
        Emblems.delete(back, Emblems.list(back).first { it.value == v1 })
        assertTrue(ClubData.usesOriginalEmblem(back, 1))
        assertEquals(1, Emblems.count128(back))
        assertEquals(0xFF0000FF.toInt(), Emblems.imageForValue(back, ClubData.emblem(back, 0))!!.toArgb()[0])
        // El hueco libre se reutiliza
        val v4 = Emblems.add(back, Emblems.Kind.C128, solid(0xFFFFFF00.toInt(), 128, 64))
        assertEquals(v1, v4)
    }

    @Test
    fun logos() {
        val of = realSave()
        val img = solid(0xFF123456.toInt(), 16, 32)
        Logos.set(of, 5, img)
        assertTrue(Logos.isUsed(of, 5))
        assertArrayEquals(img.toArgb(), Logos.image(of, 5).toArgb())
        Logos.delete(of, 5)
        assertFalse(Logos.isUsed(of, 5))
    }
}

class FaceMapTest {
    private fun realSave(): OptionFile =
        OptionFile.readXsv(javaClass.classLoader!!.getResourceAsStream("KONAMI-XBOX-PES5OPT.xsv")!!.readBytes())

    @Test
    fun removeWithMapUsesOnlyKnownEquivalences() {
        val of = realSave()
        val ff = FaceFields()
        val ch = FaceTools.removeSpecialWithMap(of, ff, Players.allIds, FaceMap.defaults())
        assertEquals(233, ch.size)
        assertEquals(Face(FaceFields.NORMAL, 9, 3, 1068), ff.read(of, 999))       // Ronaldinho
        assertEquals(FaceFields.NORMAL, ff.read(of, 3753).type)                     // Víctor Valdés
        assertEquals(126, ff.read(of, 3753).number)
        val kaka = Players.allIds.first { Players.name(of, it) == "Kaka'" }
        assertEquals(Face(FaceFields.NORMAL, 9, 2, ff.read(of, kaka).hair), ff.read(of, kaka))
        assertEquals(126, ch.count { it.changed })
        assertTrue(ch.any { !it.changed })                                          // sin equivalencia: no se tocan
    }

    @Test
    fun specialFacesGroupedBySkin() {
        val list = FaceFields().specialFaces(realSave())
        assertEquals(233, list.sumOf { it.players.size })
        assertTrue(list.any { it.skin == 3 && it.number == 7 && 999 in it.players })
    }

    @Test
    fun mapTextRoundTrip() {
        val m = FaceMap.defaults()
        assertTrue(m.size > 100)
        m.set(2, 3, 50)
        val back = FaceMap.parse(m.serialize())
        assertEquals(m.size, back.size)
        assertEquals(50, back.get(2, 3))
        assertEquals(126, back.get(1, 68))
        back.set(1, 68, null)
        assertNull(back.get(1, 68))
    }
}
