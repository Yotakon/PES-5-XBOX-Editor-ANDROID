package com.yotakon.pes5editor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealNamesTest {
    private fun realSave(): OptionFile =
        OptionFile.readXsv(javaClass.classLoader!!.getResourceAsStream("KONAMI-XBOX-PES5OPT.xsv")!!.readBytes())

    @Test
    fun emptyNamesComeFromGameTable() {
        val of = realSave()
        assertEquals("Delaney", Players.name(of, 715))   // Aston Villa
        assertEquals("Kompany", Players.name(of, 25))
        // Ningún jugador de 1 a 4895 queda sin nombre
        assertTrue((1..4895).none { Players.name(of, it).startsWith("<L ") })
    }

    @Test
    fun allSpecialFacesHaveEquivalence() {
        assertEquals(280, FaceMap.defaults().size)
    }
}
