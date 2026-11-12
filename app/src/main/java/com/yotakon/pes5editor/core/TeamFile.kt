package com.yotakon.pes5editor.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Equipo exportado (.pes5eq): plantilla, dorsales, formación (628 bytes con posiciones,
 * estrategia, capitán y lanzadores), nombre del club y datos de los jugadores.
 * Mismo formato que el editor de escritorio (TeamTransfer.java), así que los archivos sirven en los dos.
 */
class TeamFile(
    val team: Int,
    val slots: Int,
    val name: String,
    private val ids: ByteArray,
    private val nums: ByteArray,
    private val form: ByteArray,
    private val clubName: ByteArray?,
    private val playerIds: IntArray,
    private val players: Array<ByteArray>,
) {
    val playerCount get() = playerIds.size
    val hasClubName get() = clubName != null

    fun toBytes(): ByteArray {
        val bo = ByteArrayOutputStream()
        DataOutputStream(bo).use { out ->
            out.write(MAGIC)
            out.writeByte(VERSION)
            out.writeShort(team)
            out.writeByte(slots)
            out.writeUTF(name)
            out.write(ids)
            out.write(nums)
            out.write(form)
            if (clubName != null) {
                out.writeByte(1)
                out.write(clubName)
            } else {
                out.writeByte(0)
            }
            out.writeShort(playerIds.size)
            for (i in playerIds.indices) {
                out.writeInt(playerIds[i])
                out.write(players[i])
            }
        }
        return bo.toByteArray()
    }

    /**
     * Escribe el equipo en [target]: plantilla, dorsales y formación siempre; opcionalmente los datos
     * de los jugadores (sobrescribe a los de esos mismos IDs) y el nombre del club.
     */
    fun applyTo(of: OptionFile, target: Int, withPlayers: Boolean, withClubName: Boolean) {
        if (!Teams.hasFormation(target)) throw OptionFileException("El equipo destino no tiene formación propia.")
        if (Teams.slots(target) != slots) {
            throw OptionFileException(
                "El archivo tiene $slots jugadores y el destino ${Teams.slots(target)}. " +
                    "Solo se puede importar selección en selección y club en club."
            )
        }
        System.arraycopy(ids, 0, of.data, Teams.idsAdr(target), ids.size)
        System.arraycopy(nums, 0, of.data, Teams.numsAdr(target), nums.size)
        System.arraycopy(form, 0, of.data, Teams.formAdr(target), Teams.FORM_SIZE)
        if (withPlayers) {
            for (i in playerIds.indices) {
                System.arraycopy(players[i], 0, of.data, Players.recordAdr(playerIds[i]), Players.RECORD)
            }
        }
        if (withClubName && clubName != null && Teams.isClub(target)) {
            System.arraycopy(clubName, 0, of.data, Teams.clubAdr(target), Teams.CLUB_NAME_SIZE)
        }
        Teams.fixFormation(of, target)
    }

    companion object {
        private val MAGIC = "PES5EQ".toByteArray(Charsets.US_ASCII)
        private const val VERSION = 1
        const val EXTENSION = "pes5eq"

        fun export(of: OptionFile, t: Int): TeamFile {
            if (!Teams.hasFormation(t)) throw OptionFileException("Esa plantilla no tiene formación propia.")
            val n = Teams.slots(t)
            val sq = Teams.squadIds(of, t).filter { Players.isValid(it) }.toIntArray()
            return TeamFile(
                t, n, Teams.name(of, t),
                of.data.copyOfRange(Teams.idsAdr(t), Teams.idsAdr(t) + n * 2),
                of.data.copyOfRange(Teams.numsAdr(t), Teams.numsAdr(t) + n),
                of.data.copyOfRange(Teams.formAdr(t), Teams.formAdr(t) + Teams.FORM_SIZE),
                if (Teams.isClub(t)) of.data.copyOfRange(Teams.clubAdr(t), Teams.clubAdr(t) + Teams.CLUB_NAME_SIZE) else null,
                sq,
                Array(sq.size) { of.data.copyOfRange(Players.recordAdr(sq[it]), Players.recordAdr(sq[it]) + Players.RECORD) },
            )
        }

        fun read(bytes: ByteArray): TeamFile {
            try {
                DataInputStream(ByteArrayInputStream(bytes)).use { inp ->
                    val m = ByteArray(MAGIC.size)
                    inp.readFully(m)
                    if (!m.contentEquals(MAGIC)) throw OptionFileException("No es un archivo de equipo .pes5eq")
                    val v = inp.readUnsignedByte()
                    if (v != VERSION) throw OptionFileException("Versión de archivo no soportada: $v")
                    val team = inp.readUnsignedShort()
                    val slots = inp.readUnsignedByte()
                    if (slots != 23 && slots != 32) throw OptionFileException("Número de jugadores no válido: $slots")
                    val name = inp.readUTF()
                    val ids = ByteArray(slots * 2).also { inp.readFully(it) }
                    val nums = ByteArray(slots).also { inp.readFully(it) }
                    val form = ByteArray(Teams.FORM_SIZE).also { inp.readFully(it) }
                    val club = if (inp.readUnsignedByte() == 1) ByteArray(Teams.CLUB_NAME_SIZE).also { inp.readFully(it) } else null
                    val count = inp.readUnsignedShort()
                    if (count > slots) throw OptionFileException("Archivo dañado: demasiados jugadores")
                    val pIds = IntArray(count)
                    val players = Array(count) { ByteArray(Players.RECORD) }
                    for (i in 0 until count) {
                        pIds[i] = inp.readInt()
                        if (!Players.isValid(pIds[i])) throw OptionFileException("Archivo dañado: jugador ${pIds[i]}")
                        inp.readFully(players[i])
                    }
                    return TeamFile(team, slots, name, ids, nums, form, club, pIds, players)
                }
            } catch (e: IOException) {
                throw OptionFileException("Archivo de equipo incompleto o dañado")
            }
        }
    }
}
