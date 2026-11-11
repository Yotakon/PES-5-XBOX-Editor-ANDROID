package com.yotakon.pes5editor.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Option file de PES5 / WE9 de Xbox (KONAMI-XBOX-PES5OPT.xsv).
 *
 * Formato tomado de PESFan Editor 5 (OptionFile.java):
 *  - el archivo mide 1261588 bytes: 1261568 de datos + 20 de firma HMAC-SHA1;
 *  - los datos del juego (1250304 bytes, los mismos que en PC sin cifrar) son
 *    archivo[0, 5132) + archivo[16396, 1261568);
 *  - antes de firmar se recalculan las sumas de control de cada bloque.
 *
 * Los bytes entre 5132 y 16396 del archivo no son datos del juego: se conservan tal cual.
 */
class OptionFile private constructor(
    val data: ByteArray,
    private val gap: ByteArray,
    val game: Game,
) {
    enum class Game(val label: String) { PES5("PES 5"), WE9U("Winning Eleven 9") }

    /** Copia independiente (para trabajar sin tocar el original). */
    fun copy(): OptionFile = OptionFile(data.copyOf(), gap.copyOf(), game)

    fun toXsv(): ByteArray {
        // Mismos indicadores que pone PESFan Editor al guardar
        data[45] = 1
        data[46] = 1
        data[5958] = 1
        data[5959] = 1
        checkSums()
        val body = ByteArray(BODY_SIZE)
        System.arraycopy(data, 0, body, 0, HEAD)
        System.arraycopy(gap, 0, body, HEAD, gap.size)
        System.arraycopy(data, HEAD, body, GAP_END, LENGTH - HEAD)
        val out = ByteArray(FILE_SIZE)
        System.arraycopy(body, 0, out, 0, BODY_SIZE)
        System.arraycopy(hmac(body, game), 0, out, BODY_SIZE, SIG_SIZE)
        return out
    }

    fun checkSums() {
        for (i in 0 until 10) {
            var sum = 0
            var k = BLOCK[i]
            while (k < BLOCK[i] + BLOCK_SIZE[i]) {
                sum += int32(k)
                k += 4
            }
            val a = BLOCK[i] - 8
            data[a] = sum.toByte()
            data[a + 1] = (sum ushr 8).toByte()
            data[a + 2] = (sum ushr 16).toByte()
            data[a + 3] = (sum ushr 24).toByte()
        }
    }

    private fun int32(a: Int): Int =
        u8(a) or (u8(a + 1) shl 8) or (u8(a + 2) shl 16) or (u8(a + 3) shl 24)

    fun u8(a: Int): Int = data[a].toInt() and 0xFF

    fun u16(a: Int): Int = u8(a) or (u8(a + 1) shl 8)

    fun setU16(a: Int, v: Int) {
        data[a] = v.toByte()
        data[a + 1] = (v ushr 8).toByte()
    }

    companion object {
        const val LENGTH = 1250304
        const val FILE_SIZE = 1261588
        const val BODY_SIZE = 1261568
        const val SIG_SIZE = 20
        const val HEAD = 5132
        const val GAP_END = 16396
        const val FILE_NAME = "KONAMI-XBOX-PES5OPT.xsv"

        val BLOCK = intArrayOf(12, 5144, 9508, 14044, 36872, 657712, 803608, 822940, 969192, 1228568, 1250316)
        val BLOCK_SIZE = intArrayOf(4948, 1232, 4520, 22816, 620000, 145880, 19320, 146240, 259364, 21016)

        // Claves de firma de Xbox (PES4Utils.authKeyBytes / authKeyBytesWE9u, "gothi (XBox authKey)")
        private val KEY_PES5 = bytes(-14, -55, -125, -28, 82, 115, 125, -54, 84, -61, 101, -62, -110, -117, -91, -80)
        private val KEY_WE9U = bytes(-79, -72, -17, -123, 66, -96, -127, 116, -100, 63, 122, 58, -19, 116, -22, -15)

        private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

        private fun hmac(body: ByteArray, game: Game): ByteArray {
            val key = if (game == Game.WE9U) KEY_WE9U else KEY_PES5
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(key, "HmacSHA1"))
            return mac.doFinal(body)
        }

        /** Lee un .xsv. Lanza [OptionFileException] si no es un option file de Xbox válido. */
        fun readXsv(file: ByteArray): OptionFile {
            if (file.size != FILE_SIZE) {
                throw OptionFileException(
                    "El archivo mide ${file.size} bytes y un option file de PES5 Xbox mide $FILE_SIZE. " +
                        "Elige KONAMI-XBOX-PES5OPT.xsv."
                )
            }
            val m0 = file[GAP_END].toInt() and 0xFF
            val m1 = file[GAP_END + 1].toInt() and 0xFF
            if (m0 != 0xD0 || m1 != 0x04 || file[GAP_END + 2].toInt() != 0 || file[GAP_END + 3].toInt() != 0) {
                throw OptionFileException("No es un option file de PES5 / WE9 de Xbox.")
            }
            val body = file.copyOfRange(0, BODY_SIZE)
            val sig = file.copyOfRange(BODY_SIZE, FILE_SIZE)
            val game = when {
                hmac(body, Game.PES5).contentEquals(sig) -> Game.PES5
                hmac(body, Game.WE9U).contentEquals(sig) -> Game.WE9U
                else -> throw OptionFileException("La firma del archivo no es válida: puede estar dañado o ser de otro juego.")
            }
            val data = ByteArray(LENGTH)
            System.arraycopy(file, 0, data, 0, HEAD)
            System.arraycopy(file, GAP_END, data, HEAD, LENGTH - HEAD)
            val gap = file.copyOfRange(HEAD, GAP_END)
            val of = OptionFile(data, gap, game)
            Teams.fixAllFormations(of)
            return of
        }

        /** Solo para pruebas: option file con los datos dados y firma PES5. */
        internal fun forTest(data: ByteArray): OptionFile {
            require(data.size == LENGTH)
            data[HEAD] = 0xD0.toByte()
            data[HEAD + 1] = 0x04
            data[HEAD + 2] = 0
            data[HEAD + 3] = 0
            return OptionFile(data, ByteArray(GAP_END - HEAD), Game.PES5)
        }
    }
}

class OptionFileException(message: String) : Exception(message)
