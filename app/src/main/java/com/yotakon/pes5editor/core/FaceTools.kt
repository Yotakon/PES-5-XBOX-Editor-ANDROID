package com.yotakon.pes5editor.core

/** Operaciones en bloque sobre las caras. En los CSV la cara y la piel van como en el juego (desde 1). */
object FaceTools {

    data class Change(
        val id: Int,
        val name: String,
        val oldType: Int,
        val oldNumber: Int,
        val newType: Int,
        val newNumber: Int,
        val note: String = "",
    ) {
        val changed get() = oldType != newType || oldNumber != newNumber
    }

    /** Informe de cambios. Tiene las columnas de importCsv para deshacer (usa TIPO CARA / NUMERO CARA de antes). */
    fun report(changes: List<Change>): String = buildString {
        append('\uFEFF')
        append("ID;NOMBRE;TIPO CARA;NUMERO CARA;TIPO DESPUES;CARA DESPUES;NOTA\r\n")
        for (c in changes) {
            append("${c.id};${csv(c.name)};${c.oldType};${c.oldNumber};${c.newType};${c.newNumber};${c.note}\r\n")
        }
    }

    fun exportCsv(of: OptionFile, ff: FaceFields): String = buildString {
        append('\uFEFF')
        append("ID;NOMBRE;TIPO CARA;NUMERO CARA;PIEL;PELO;TIPO (texto);ARCHIVO FACE;ARCHIVO HAIR\r\n")
        for (id in Players.allIds) {
            val f = ff.read(of, id)
            val fa = Relink.faceFile(f)?.toString().orEmpty()
            val ha = Relink.hairFile(f)?.toString().orEmpty()
            append("$id;${csv(Players.name(of, id))};${f.type};${f.number};${f.skin};${f.hair};${FaceFields.typeName(f.type)};$fa;$ha\r\n")
        }
    }

    class ImportResult {
        var changedPlayers = 0
        val errors = ArrayList<String>()
    }

    /**
     * Columnas: ID y cualquiera de TIPO CARA (0-2), NUMERO CARA (1-512), PIEL (1-4), PELO (0-2047),
     * ARCHIVO FACE (1590-2362) y ARCHIVO HAIR (2829-4876) del 0_text.afs.
     * Los archivos se aplican después de las demás columnas y solo si cambian el archivo que se carga.
     * Con ellos el jugador queda siempre con FACE normal (ver [Relink.plan]); [map] da la copia normal
     * de las FACE especiales. Separador ';' o ','. Celdas vacías = sin cambios.
     */
    fun importCsv(of: OptionFile, ff: FaceFields, text: String, map: FaceMap? = null): ImportResult {
        val r = ImportResult()
        val lines = text.removePrefix("\uFEFF").lines()
        if (lines.isEmpty() || lines[0].isBlank()) {
            r.errors += "El archivo está vacío"
            return r
        }
        val sep = if (lines[0].contains(';')) ';' else ','
        val cols = splitCsv(lines[0], sep).map { it.trim().uppercase() }
        val cId = cols.indexOf("ID")
        if (cId < 0) {
            r.errors += "Falta la columna ID"
            return r
        }
        val cType = cols.indexOf("TIPO CARA")
        val cNum = cols.indexOf("NUMERO CARA")
        val cSkin = cols.indexOf("PIEL")
        val cHair = cols.indexOf("PELO")
        val cFaceFile = cols.indexOf("ARCHIVO FACE")
        val cHairFile = cols.indexOf("ARCHIVO HAIR")
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val lineNo = i + 1
            val v = splitCsv(line, sep)
            val id = cell(v, cId)?.toIntOrNull()
            if (id == null || !Players.isValid(id)) {
                r.errors += "Línea $lineNo: ID no válido"
                continue
            }
            val old = ff.read(of, id)
            fun num(c: Int, colName: String, range: IntRange, current: Int): Int {
                val s = cell(v, c)
                if (s.isNullOrEmpty()) return current
                val x = s.toIntOrNull()
                if (x == null || x !in range) {
                    r.errors += "Línea $lineNo: $colName fuera de rango (${range.first}-${range.last})"
                    return current
                }
                return x
            }
            var nf = Face(
                num(cType, "TIPO CARA", 0..2, old.type),
                num(cNum, "NUMERO CARA", 1..ff.face.mask + 1, old.number),
                num(cSkin, "PIEL", 1..ff.skin.mask + 1, old.skin),
                num(cHair, "PELO", 0..ff.hair.mask, old.hair),
            )
            fun entry(c: Int, colName: String): Int? {
                val s = cell(v, c)
                if (s.isNullOrEmpty()) return null
                return s.toIntOrNull() ?: run { r.errors += "Línea $lineNo: $colName no es un número"; null }
            }
            val faceEntry = entry(cFaceFile, "ARCHIVO FACE")
            val hairEntry = entry(cHairFile, "ARCHIVO HAIR")
            val faceChanges = faceEntry != null && faceEntry != Relink.faceFile(nf)
            val hairChanges = hairEntry != null && hairEntry != Relink.hairFile(nf)
            if (faceChanges || hairChanges) {
                val p = Relink.relink(nf, faceEntry, hairEntry, map = map)
                if (p.face == null) r.errors += "Línea $lineNo: ${p.error}" else nf = p.face
            }
            if (nf != old) {
                ff.write(of, id, nf)
                r.changedPlayers++
            }
        }
        return r
    }

    private fun cell(v: List<String>, i: Int): String? = if (i < 0 || i >= v.size) null else v[i].trim()

    /** Divide una línea CSV respetando comillas dobles. */
    fun splitCsv(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                q && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                q && c == '"' -> q = false
                q -> cur.append(c)
                c == '"' -> q = true
                c == sep -> { out += cur.toString(); cur.setLength(0) }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    /** Texto seguro para una celda: sin saltos de línea y entre comillas si hace falta. */
    fun csv(raw: String): String {
        val s = raw.map { if (it.isISOControl()) ' ' else it }.joinToString("")
        return if (s.any { it == ';' || it == ',' || it == '"' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
