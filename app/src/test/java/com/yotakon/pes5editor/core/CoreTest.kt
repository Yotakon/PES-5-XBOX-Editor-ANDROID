package com.yotakon.pes5editor.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class CoreTest {

    private fun randomOf(seed: Int): OptionFile {
        val data = ByteArray(OptionFile.LENGTH)
        Random(seed).nextBytes(data)
        return OptionFile.forTest(data)
    }

    @Test
    fun xsvRoundTrip() {
        val of = randomOf(1)
        val file = of.toXsv()
        assertEquals(OptionFile.FILE_SIZE, file.size)
        val back = OptionFile.readXsv(file)
        assertEquals(OptionFile.Game.PES5, back.game)
        // Al abrir se normalizan las formaciones; guardar de nuevo debe dar el mismo archivo
        val again = OptionFile.readXsv(back.toXsv())
        assertArrayEquals(back.data, again.data)
    }

    @Test
    fun badSignatureIsRejected() {
        val file = randomOf(2).toXsv()
        file[100] = (file[100] + 1).toByte()
        try {
            OptionFile.readXsv(file)
            fail("debía rechazar la firma")
        } catch (e: OptionFileException) {
            assertTrue(e.message!!.contains("firma"))
        }
    }

    @Test
    fun wrongSizeIsRejected() {
        try {
            OptionFile.readXsv(ByteArray(1250304))
            fail("debía rechazar el tamaño")
        } catch (e: OptionFileException) {
        }
    }

    @Test
    fun checksumsMatchBlockSums() {
        val of = randomOf(3)
        of.checkSums()
        for (i in 0 until 10) {
            var sum = 0
            var k = OptionFile.BLOCK[i]
            while (k < OptionFile.BLOCK[i] + OptionFile.BLOCK_SIZE[i]) {
                sum += of.u16(k) or (of.u16(k + 2) shl 16)
                k += 4
            }
            val a = OptionFile.BLOCK[i] - 8
            assertEquals(sum, of.u16(a) or (of.u16(a + 2) shl 16))
        }
    }

    @Test
    fun faceFieldsDoNotTouchOtherAttributes() {
        val of = randomOf(4)
        val ff = FaceFields()
        // Atributos de PESFan Editor 5 (Stats.java): altura, peso, edad, nacionalidad, ataque
        val others = listOf(Field(41, 0, 63), Field(41, 6, 127), Field(62, 5, 31), Field(63, 2, 127), Field(7, 0, 127))
        for (id in intArrayOf(1, 1000, 4999, 32768, 32951)) {
            val before = others.map { it.get(of, id) }
            ff.write(of, id, Face(2, 512, 4, 2047))
            assertEquals(Face(2, 512, 4, 2047), ff.read(of, id))
            ff.write(of, id, Face(0, 1, 1, 0))
            assertEquals(Face(0, 1, 1, 0), ff.read(of, id))
            assertEquals(before, others.map { it.get(of, id) })
        }
    }

    @Test
    fun removeSpecialWithOffset() {
        val of = randomOf(5)
        val ff = FaceFields()
        for (id in Players.allIds) ff.type.set(of, id, FaceFields.NORMAL)
        ff.write(of, 1000, Face(FaceFields.SPECIAL, 101, 1, 0))
        ff.write(of, 2000, Face(FaceFields.SPECIAL, 6, 1, 0))
        ff.write(of, 32770, Face(FaceFields.SPECIAL, 512, 1, 0))
        val ch = FaceTools.removeSpecial(of, ff, Players.allIds, 1)
        assertEquals(3, ch.size)
        assertEquals(Face(FaceFields.NORMAL, 102, 1, 0), ff.read(of, 1000))
        assertEquals(Face(FaceFields.NORMAL, 7, 1, 0), ff.read(of, 2000))
        assertEquals(FaceFields.SPECIAL, ff.read(of, 32770).type) // 513 no existe
        assertFalse(ch.first { it.id == 32770 }.changed)

        // El informe sirve para deshacer
        val undo = FaceTools.importCsv(of, ff, FaceTools.report(ch))
        assertEquals(2, undo.changedPlayers)
        assertEquals(Face(FaceFields.SPECIAL, 101, 1, 0), ff.read(of, 1000))
    }

    @Test
    fun facesCsvExportImport() {
        val of = randomOf(6)
        val ff = FaceFields()
        val csv = FaceTools.exportCsv(of, ff)
        assertEquals(1 + Players.allIds.size, csv.trimEnd().lines().size)
        // Importar lo exportado no cambia nada
        assertEquals(0, FaceTools.importCsv(of, ff, csv).changedPlayers)
        val r = FaceTools.importCsv(of, ff, "ID,NUMERO CARA,PIEL\n1000,300,4\n99999,1,1\n1001,999,\n")
        assertEquals(1, r.changedPlayers)
        assertEquals(2, r.errors.size)
        assertEquals(300, ff.read(of, 1000).number)
        assertEquals(4, ff.read(of, 1000).skin)
    }

    @Test
    fun csvQuotes() {
        assertEquals(listOf("1", "a;b", "c\"d"), FaceTools.splitCsv("1;\"a;b\";\"c\"\"d\"", ';'))
    }

    @Test
    fun teamExportImport() {
        val src = randomOf(7)
        val ids = IntArray(32) { if (it < 30) 1500 + it else if (it == 30) 32800 else 0 }
        ids.forEachIndexed { i, id -> src.setU16(Teams.idsAdr(74) + i * 2, id) }
        for (i in 0 until 32) src.data[Teams.formAdr(74) + i] = i.toByte()

        val bytes = TeamFile.export(src, 74).toBytes()
        val tf = TeamFile.read(bytes)
        assertEquals(32, tf.slots)
        assertEquals(31, tf.playerCount)
        assertTrue(tf.hasClubName)

        val dst = randomOf(8)
        tf.applyTo(dst, 80, withPlayers = true, withClubName = true)
        assertArrayEquals(ids, Teams.squadIds(dst, 80))
        fun range(of: OptionFile, a: Int, n: Int) = of.data.copyOfRange(a, a + n)
        assertArrayEquals(range(src, Teams.numsAdr(74), 32), range(dst, Teams.numsAdr(80), 32))
        assertArrayEquals(range(src, Teams.formAdr(74) + 32, 596), range(dst, Teams.formAdr(80) + 32, 596))
        assertArrayEquals(range(src, Players.recordAdr(32800), 124), range(dst, Players.recordAdr(32800), 124))
        assertArrayEquals(range(src, Teams.clubAdr(74), 80), range(dst, Teams.clubAdr(80), 80))

        try {
            tf.applyTo(dst, 5, withPlayers = true, withClubName = true)
            fail("club en selección")
        } catch (e: OptionFileException) {
        }
        try {
            TeamFile.export(src, 70)
            fail("plantilla sin formación")
        } catch (e: OptionFileException) {
        }
        assertTrue(Teams.formAdr(211) + Teams.FORM_SIZE <= Teams.CLUB_BASE)
        assertEquals(Teams.idsAdr(73) + 64, Teams.IDS_32)
        assertEquals(Teams.idsAdr(72) + 28, Teams.idsAdr(73))
    }

    @Test
    fun fixFormationReordersSquad() {
        val of = randomOf(9)
        val t = 5
        val ids = IntArray(23) { 100 + it }
        ids.forEachIndexed { i, id -> of.setU16(Teams.idsAdr(t) + i * 2, id) }
        val form = Teams.formAdr(t)
        // orden invertido en los 23 primeros
        for (i in 0 until 32) of.data[form + i] = (if (i < 23) 22 - i else i).toByte()
        of.data[form + 102] = 22 // capitán apunta al valor 22 de la tabla (posición 0)
        Teams.fixFormation(of, t)
        assertArrayEquals(IntArray(23) { 122 - it }, Teams.squadIds(of, t))
        for (i in 0 until 32) assertEquals(i, of.u8(form + i))
        assertEquals(0, of.u8(form + 102))
    }
}
