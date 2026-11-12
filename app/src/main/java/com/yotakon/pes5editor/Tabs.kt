package com.yotakon.pes5editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yotakon.pes5editor.core.ClubData
import com.yotakon.pes5editor.core.Emblems
import com.yotakon.pes5editor.core.FaceMap
import com.yotakon.pes5editor.core.specialFaces
import com.yotakon.pes5editor.core.IndexedImage
import com.yotakon.pes5editor.core.Kits
import com.yotakon.pes5editor.core.Logos
import com.yotakon.pes5editor.core.Names
import com.yotakon.pes5editor.core.OptionFile
import com.yotakon.pes5editor.core.OptionFileException
import com.yotakon.pes5editor.core.Players
import com.yotakon.pes5editor.core.Quantizer
import com.yotakon.pes5editor.core.Teams
import com.yotakon.pes5editor.core.Transfers

/** Una pestaña del editor. */
abstract class EditorTab(protected val act: MainActivity) {
    abstract val view: View

    /** Se llama al abrir un option file (para rellenar desplegables). */
    open fun onFileOpened() {}

    /** Se llama al mostrar la pestaña. */
    abstract fun refresh()

    protected val of: OptionFile get() = act.requireOf()
    protected fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    protected fun vbox(padding: Int = 8) = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    protected fun label(text: String, small: Boolean = false) = TextView(act).apply {
        this.text = text
        if (small) textSize = 12f
        setPadding(0, dp(4), 0, dp(2))
    }

    protected fun spinner(items: List<String>) = Spinner(act).apply {
        adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, items)
    }

    protected fun onSelect(s: Spinner, f: (Int) -> Unit) {
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = f(position)
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    protected fun list(): RecyclerView = RecyclerView(act).apply {
        layoutManager = LinearLayoutManager(act)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    /** Ejecuta un cambio y muestra el error si falla. */
    protected fun change(block: () -> Unit): Boolean {
        if (!act.checkWritable()) return false
        return try {
            block()
            act.markDirty()
            true
        } catch (e: OptionFileException) {
            act.showError(e.message ?: "Error")
            false
        } catch (e: IllegalArgumentException) {
            act.showError(e.message ?: "Valor no válido")
            false
        }
    }

    protected fun editText(value: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(act).apply {
        setText(value)
        inputType = type
        maxLines = 1
    }

    /** Pide un texto en un diálogo. */
    protected fun askText(title: String, value: String, onOk: (String) -> Unit) {
        val e = editText(value)
        val box = vbox(16)
        box.addView(e)
        AlertDialog.Builder(act).setTitle(title).setView(box)
            .setPositiveButton("Aceptar") { _, _ -> onOk(e.text.toString().trim()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

/** Fila de texto de dos líneas para las listas. */
class TextRow(ctx: android.content.Context) : LinearLayout(ctx) {
    val title = TextView(ctx).apply { textSize = 15f }
    val detail = TextView(ctx).apply { textSize = 12f }

    init {
        orientation = VERTICAL
        val p = (8 * ctx.resources.displayMetrics.density).toInt()
        setPadding(p, p / 2, p, p / 2)
        addView(title)
        addView(detail)
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        isClickable = true
        isFocusable = true
    }
}

/** Adaptador sencillo para listas de [TextRow]. */
class RowAdapter(
    private val bind: (row: TextRow, position: Int) -> Unit,
) : RecyclerView.Adapter<RowAdapter.VH>() {
    var count = 0
    class VH(val row: TextRow) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(TextRow(parent.context))
    override fun getItemCount() = count
    override fun onBindViewHolder(h: VH, position: Int) = bind(h.row, position)

    fun update(n: Int) {
        count = n
        notifyDataSetChanged()
    }
}

object Bitmaps {
    fun of(img: IndexedImage): Bitmap = Bitmap.createBitmap(img.toArgb(), img.width, img.height, Bitmap.Config.ARGB_8888)

    /** Encaja la imagen en un cuadrado de [size] píxeles, con fondo transparente, y la reduce a [colors] colores. */
    fun toIndexed(src: Bitmap, size: Int, colors: Int): IndexedImage {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = minOf(size.toFloat() / src.width, size.toFloat() / src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val left = (size - w) / 2
        val top = (size - h) / 2
        Canvas(out).drawBitmap(src, null, Rect(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(size * size)
        out.getPixels(px, 0, size, 0, 0, size, size)
        return Quantizer.quantize(px, size, size, colors)
    }

    fun imageView(ctx: android.content.Context, sizePx: Int) = ImageView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(Color.rgb(200, 200, 200))
    }
}

// ============================================================================ Transferencias

class TransferTab(act: MainActivity) : EditorTab(act) {
    private val ALL = Teams.COUNT
    private val autoRelease = CheckBox(act).apply {
        text = "Al fichar en un club, quitarlo de su club anterior"
        isChecked = true
    }
    private val sides = arrayOf(Side(0), Side(1))

    /** Selección: lado y fila. */
    private var selSide = -1
    private var selRow = -1

    override val view: View = vbox(4).apply {
        addView(autoRelease)
        addView(label("Toca un jugador y después el hueco de destino (de la misma lista o de la otra). " +
            "Mantén pulsado para liberar, cambiar dorsal o editar el jugador.", small = true))
        val columns = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        sides.forEach { columns.addView(it.column) }
        addView(columns)
    }

    private inner class Side(val index: Int) {
        val team = Spinner(act)
        val adapter = RowAdapter { row, pos -> bind(row, pos) }
        val recycler = list().apply { adapter = this@Side.adapter }
        val column = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            addView(team)
            addView(recycler)
        }
        var rows: IntArray = IntArray(0) // squad: hueco; "todos": ID

        init {
            onSelect(team) {
                clearSelection()
                reload()
            }
        }

        val teamIndex get() = team.selectedItemPosition
        val isAll get() = teamIndex == ALL

        fun reload() {
            if (Session.of == null || teamIndex < 0) return
            rows = if (isAll) Players.allIds.copyOf() else IntArray(Teams.slots(teamIndex)) { it }
            adapter.update(rows.size)
        }

        fun playerOf(row: Int) = if (isAll) rows[row] else Transfers.playerAt(of, teamIndex, rows[row])

        fun bind(r: TextRow, pos: Int) {
            val id = playerOf(pos)
            val selected = selSide == index && selRow == pos
            r.setBackgroundColor(if (selected) Color.argb(90, 249, 168, 37) else Color.TRANSPARENT)
            if (isAll) {
                r.title.text = Players.name(of, id)
                r.detail.text = "ID $id"
            } else {
                val n = Transfers.rawNumber(of, teamIndex, pos)?.let { "${it + 1}  " } ?: ""
                r.title.text = if (id == 0) "—" else n + Players.name(of, id)
                r.detail.text = (if (pos < 11) "Titular" else "Suplente") + if (id != 0) " · ID $id" else ""
            }
            r.setOnClickListener { tapped(index, pos) }
            r.setOnLongClickListener { longPress(index, pos); true }
        }
    }

    override fun onFileOpened() {
        val names = (0 until Teams.COUNT).map { "$it - ${Teams.name(of, it)}" } + "Todos los jugadores"
        sides[0].team.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, names)
        sides[1].team.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, names)
        sides[0].team.setSelection(74)
        sides[1].team.setSelection(75)
    }

    override fun refresh() {
        if (sides[0].team.adapter == null) onFileOpened()
        sides.forEach { it.reload() }
    }

    private fun clearSelection() {
        selSide = -1
        selRow = -1
    }

    private fun tapped(side: Int, row: Int) {
        val s = sides[side]
        if (selSide < 0) {
            selSide = side
            selRow = row
            refresh()
            return
        }
        if (selSide == side && selRow == row) {
            clearSelection()
            refresh()
            return
        }
        val src = sides[selSide]
        val srcRow = selRow
        when {
            src.isAll && s.isAll -> { selSide = side; selRow = row; refresh(); return }
            src.isAll -> signInto(src.playerOf(srcRow), s, row)
            s.isAll -> signInto(s.playerOf(row), src, srcRow)
            else -> {
                if (src.playerOf(srcRow) == 0 && s.playerOf(row) == 0) {
                    selSide = side; selRow = row; refresh(); return
                }
                change { Transfers.swap(of, src.teamIndex, src.rows[srcRow], s.teamIndex, s.rows[row]) }
            }
        }
        clearSelection()
        refresh()
    }

    private fun signInto(id: Int, target: Side, row: Int) {
        change {
            val released = Transfers.sign(of, id, target.teamIndex, target.rows[row], autoRelease.isChecked)
            if (released.isNotEmpty()) {
                act.showToast("${Players.name(of, id)} sale de " + released.joinToString { Teams.name(of, it) })
            }
        }
    }

    private fun longPress(side: Int, row: Int) {
        val s = sides[side]
        val id = s.playerOf(row)
        if (s.isAll) {
            val clubs = Transfers.clubsOf(of, id).joinToString { Teams.name(of, it) }.ifEmpty { "ninguno" }
            AlertDialog.Builder(act).setTitle(Players.name(of, id))
                .setMessage("ID $id\nClub: $clubs")
                .setPositiveButton("Editar jugador") { _, _ -> act.openFaceEditor(id) { refresh() } }
                .setNegativeButton("Cerrar", null)
                .show()
            return
        }
        val t = s.teamIndex
        val slot = s.rows[row]
        val options = ArrayList<Pair<String, () -> Unit>>()
        if (id != 0) {
            options += "Liberar del equipo" to { change { Transfers.release(of, t, slot) }; clearSelection(); refresh() }
            options += "Cambiar dorsal" to {
                val cur = Transfers.rawNumber(of, t, slot)?.plus(1)?.toString() ?: ""
                askText("Dorsal (1-99)", cur) { v ->
                    val n = v.toIntOrNull()
                    if (n == null || n !in 1..99) act.showError("El dorsal va de 1 a 99")
                    else change { Transfers.setNumber(of, t, slot, n) }
                    refresh()
                }
            }
            options += "Editar jugador" to { act.openFaceEditor(id) { refresh() } }
        }
        if (options.isEmpty()) return
        AlertDialog.Builder(act).setTitle(if (id != 0) Players.name(of, id) else "Hueco vacío")
            .setItems(options.map { it.first }.toTypedArray()) { _, w -> options[w].second() }
            .show()
    }
}

// ============================================================================ Equipos

class TeamsTab(act: MainActivity) : EditorTab(act) {
    private val club = Spinner(act)
    private val name = editText("")
    private val abbr = editText("", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
    private val color1 = editText("")
    private val color2 = editText("")
    private val swatch1 = View(act)
    private val swatch2 = View(act)
    private val stadium = Spinner(act)
    private val emblem = Bitmaps.imageView(act, (96 * act.resources.displayMetrics.density).toInt())
    private val emblemText = label("")
    private var loading = false

    // Uniforme: selecciones (0-63) y clubes (74-211)
    private val kitTeams = (0 until Teams.COUNT).filter { Kits.hasKit(it) }
    private val kitTeam = Spinner(act)
    private val kitLogos = Array(Kits.LOGO_SLOTS) { Bitmaps.imageView(act, (48 * act.resources.displayMetrics.density).toInt()) }
    private val kitLogoText = Array(Kits.LOGO_SLOTS) { TextView(act) }

    override val view: View = ScrollView(act).apply {
        val box = vbox(12)
        box.addView(label("Club"))
        box.addView(club)
        box.addView(label("Nombre (máx. ${ClubData.NAME_MAX} bytes)"))
        box.addView(name)
        box.addView(label("Abreviatura (3 letras)"))
        box.addView(abbr)
        box.addView(colorRow("Color 1 (#RRGGBB)", color1, swatch1))
        box.addView(colorRow("Color 2 (#RRGGBB)", color2, swatch2))
        box.addView(label("Estadio"))
        box.addView(stadium)
        box.addView(label("Escudo"))
        val er = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(emblem)
            val col = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(emblemText)
                addView(Button(act).apply { text = "Cambiar escudo"; setOnClickListener { chooseEmblem() } })
            }
            addView(col)
        }
        box.addView(er)
        box.addView(Button(act).apply { text = "Guardar cambios del club"; setOnClickListener { save() } })
        box.addView(label("Los cambios se escriben en el option file abierto. Usa Guardar del menú para grabarlo.", small = true))

        box.addView(TextView(act).apply {
            text = "Uniforme"
            textSize = 18f
            setPadding(0, dp(20), 0, dp(4))
        })
        box.addView(label("Equipo del uniforme (selecciones y clubes)"))
        box.addView(kitTeam)
        box.addView(label("Logos del uniforme. Toca uno para cambiarlo. Los logos se cargan en la pestaña Escudos y logos.", small = true))
        for (n in 0 until Kits.LOGO_SLOTS) {
            box.addView(LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
                addView(kitLogos[n])
                kitLogoText[n].setPadding(dp(12), 0, 0, 0)
                addView(kitLogoText[n])
                setOnClickListener { chooseKitLogo(n) }
            })
        }
        box.addView(Button(act).apply { text = "Copiar uniforme de otro equipo de este archivo"; setOnClickListener { copyKit() } })
        box.addView(Button(act).apply { text = "Importar uniforme desde otro option file"; setOnClickListener { importKit() } })
        box.addView(label("PESFan Editor 5 no documenta los colores del uniforme por separado: se copia el uniforme completo " +
            "de otro equipo o de otro option file, con sus logos y, en clubes, su escudo.", small = true))
        addView(box)
    }

    private fun colorRow(title: String, field: EditText, swatch: View) = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(title))
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        field.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        swatch.layoutParams = LinearLayout.LayoutParams(dp(40), dp(32))
        row.addView(field)
        row.addView(swatch)
        addView(row)
    }

    init {
        onSelect(club) {
            if (!loading) {
                load()
                val k = kitTeams.indexOf(clubIndex + 74)
                if (k >= 0 && kitTeam.adapter != null) kitTeam.setSelection(k)
            }
        }
        onSelect(kitTeam) { if (!loading) loadKit() }
    }

    private val kitIndex get() = kitTeams.getOrNull(kitTeam.selectedItemPosition) ?: -1

    private fun loadKit() {
        val t = kitIndex
        if (t < 0 || Session.of == null) return
        for (n in 0 until Kits.LOGO_SLOTS) {
            val l = Kits.logo(of, t, n)
            if (l != null && Logos.isUsed(of, l)) kitLogos[n].setImageBitmap(Bitmaps.of(Logos.image(of, l))) else kitLogos[n].setImageDrawable(null)
            kitLogoText[n].text = "Logo ${n + 1}: " + (l?.let { "logo $it" } ?: "ninguno")
        }
    }

    private fun chooseKitLogo(n: Int) {
        if (!act.checkWritable()) return
        val t = kitIndex
        val used = (0 until Logos.COUNT).filter { Logos.isUsed(of, it) }
        val size = dp(40)
        val adapter = object : BaseAdapter() {
            override fun getCount() = used.size + 1
            override fun getItem(position: Int) = position
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(6), dp(16), dp(6))
                }
                val iv = Bitmaps.imageView(act, size)
                val tv = TextView(act).apply { setPadding(dp(12), 0, 0, 0) }
                if (position == 0) tv.text = "Sin logo" else {
                    iv.setImageBitmap(Bitmaps.of(Logos.image(of, used[position - 1])))
                    tv.text = "Logo ${used[position - 1]}"
                }
                row.addView(iv)
                row.addView(tv)
                return row
            }
        }
        AlertDialog.Builder(act).setTitle(if (used.isEmpty()) "No hay logos cargados" else "Logo ${n + 1} del uniforme")
            .setAdapter(adapter) { _, w ->
                change { Kits.setLogo(of, t, n, if (w == 0) null else used[w - 1]) }
                loadKit()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun kitLabel(o: OptionFile, t: Int) = "$t - ${Teams.name(o, t)}"

    private fun copyKit() {
        if (!act.checkWritable()) return
        val t = kitIndex
        val sources = kitTeams.filter { it != t && (it >= 74) == (t >= 74) }
        AlertDialog.Builder(act).setTitle("Copiar el uniforme de…")
            .setItems(sources.map { kitLabel(of, it) }.toTypedArray()) { _, w ->
                if (change { Kits.copy(of, sources[w], t) }) act.showToast("Uniforme copiado")
                loadKit()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun importKit() {
        if (!act.checkWritable()) return
        val t = kitIndex
        act.pickBytes { bytes ->
            val other = try {
                OptionFile.readXsv(bytes, allowBadSignature = true)
            } catch (e: OptionFileException) {
                act.showError(e.message ?: "No es un option file válido"); return@pickBytes
            }
            val sources = kitTeams.filter { (it >= 74) == (t >= 74) }
            val box = vbox(16)
            box.addView(label("Equipo de origen"))
            val src = spinner(sources.map { kitLabel(other, it) })
            val pre = sources.indexOf(t)
            if (pre >= 0) src.setSelection(pre)
            box.addView(src)
            val emblem = CheckBox(act).apply { text = "Importar también el escudo"; isChecked = true; isEnabled = t >= 74 }
            val clubData = CheckBox(act).apply {
                text = "Importar también nombre, abreviatura, colores y estadio del club"
                isChecked = false
                isEnabled = t >= 74
            }
            box.addView(emblem)
            box.addView(clubData)
            box.addView(label("Los logos del uniforme se copian a huecos de logo libres de tu archivo.", small = true))
            AlertDialog.Builder(act).setTitle("Importar uniforme en ${Teams.name(of, t)}")
                .setView(box)
                .setPositiveButton("Importar") { _, _ ->
                    var r: Kits.ImportResult? = null
                    if (change { r = Kits.importFrom(of, t, other, sources[src.selectedItemPosition], emblem.isChecked && t >= 74, clubData.isChecked && t >= 74) }) {
                        val res = r!!
                        act.showInfo("Importar uniforme", buildString {
                            append("Uniforme importado.\n")
                            append("Logos copiados: ${res.logosCopied}\n")
                            if (t >= 74) append("Escudo: " + (if (res.emblemCopied) "importado" else "sin cambios") + "\n")
                            res.warnings.forEach { append("\n").append(it) }
                        })
                        onFileOpened()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private val clubIndex get() = club.selectedItemPosition

    override fun onFileOpened() {
        loading = true
        val sel = clubIndex.coerceAtLeast(0)
        club.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item,
            (0 until ClubData.COUNT).map { "${ClubData.abbreviation(of, it)}  ${ClubData.name(of, it)}" })
        stadium.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item,
            (0 until Names.STADIUM_COUNT).map { "$it - ${Names.stadium(of, it)}" })
        club.setSelection(sel.coerceAtMost(ClubData.COUNT - 1))
        val kitSel = kitTeam.selectedItemPosition.takeIf { it >= 0 } ?: kitTeams.indexOf(sel + 74)
        kitTeam.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, kitTeams.map { kitLabel(of, it) })
        kitTeam.setSelection(kitSel.coerceIn(0, kitTeams.size - 1))
        loading = false
        load()
        loadKit()
    }

    override fun refresh() {
        if (club.adapter == null) onFileOpened() else load()
    }

    private fun hex(rgb: Int) = String.format("#%06X", rgb and 0xFFFFFF)

    private fun load() {
        val c = clubIndex
        if (c < 0 || Session.of == null) return
        name.setText(ClubData.name(of, c))
        abbr.setText(ClubData.abbreviation(of, c))
        val c1 = ClubData.color(of, c, 1)
        val c2 = ClubData.color(of, c, 2)
        color1.setText(hex(c1))
        color2.setText(hex(c2))
        swatch1.setBackgroundColor(c1 or (0xFF shl 24))
        swatch2.setBackgroundColor(c2 or (0xFF shl 24))
        val st = ClubData.stadium(of, c)
        if (st < Names.STADIUM_COUNT) stadium.setSelection(st)
        showEmblem()
    }

    private fun showEmblem() {
        val v = ClubData.emblem(of, clubIndex)
        val img = Emblems.imageForValue(of, v)
        if (img != null) {
            emblem.setImageBitmap(Bitmaps.of(img))
            emblemText.text = "Escudo cargado ($v)"
        } else {
            emblem.setImageDrawable(null)
            emblemText.text = if (ClubData.usesOriginalEmblem(of, clubIndex)) "Escudo original del juego" else "Escudo del juego ($v)"
        }
    }

    private fun parseColor(e: EditText): Int? {
        val s = e.text.toString().trim().removePrefix("#")
        val v = if (s.length == 6) s.toIntOrNull(16) else null
        if (v == null) e.error = "Formato #RRGGBB"
        return v
    }

    private fun save() {
        val c = clubIndex
        val c1 = parseColor(color1) ?: return
        val c2 = parseColor(color2) ?: return
        val ok = change {
            val n = name.text.toString().trim()
            if (n != ClubData.name(of, c)) ClubData.setName(of, c, n)
            val a = abbr.text.toString().trim().uppercase()
            if (a != ClubData.abbreviation(of, c)) ClubData.setAbbreviation(of, c, a)
            ClubData.setColor(of, c, 1, c1)
            ClubData.setColor(of, c, 2, c2)
            val st = stadium.selectedItemPosition
            if (st >= 0 && st != ClubData.stadium(of, c)) ClubData.setStadium(of, c, st)
        }
        if (ok) {
            act.showToast("Club actualizado")
            onFileOpened()
        }
    }

    private fun chooseEmblem() {
        if (!act.checkWritable()) return
        val list = Emblems.list(of)
        val size = dp(48)
        val adapter = object : BaseAdapter() {
            override fun getCount() = list.size + 1
            override fun getItem(position: Int) = position
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(6), dp(16), dp(6))
                }
                val iv = Bitmaps.imageView(act, size)
                val tv = TextView(act).apply { setPadding(dp(12), 0, 0, 0) }
                if (position == 0) {
                    tv.text = "Escudo original del juego"
                } else {
                    val e = list[position - 1]
                    iv.setImageBitmap(Bitmaps.of(Emblems.image(of, e.kind, e.slot)))
                    tv.text = "Escudo ${e.value} (${e.kind.colors} colores)"
                }
                row.addView(iv)
                row.addView(tv)
                return row
            }
        }
        AlertDialog.Builder(act).setTitle(
            if (list.isEmpty()) "No hay escudos cargados (añádelos en Escudos y logos)" else "Elegir escudo"
        ).setAdapter(adapter) { _, w ->
            change {
                if (w == 0) ClubData.setOriginalEmblem(of, clubIndex) else ClubData.setEmblem(of, clubIndex, list[w - 1].value)
            }
            showEmblem()
        }.setNegativeButton("Cancelar", null).show()
    }
}

// ============================================================================ Escudos y logos

class ImagesTab(act: MainActivity) : EditorTab(act) {
    private val emblemsRadio = RadioButton(act).apply { text = "Escudos"; id = View.generateViewId() }
    private val logosRadio = RadioButton(act).apply { text = "Logos"; id = View.generateViewId() }
    private val info = label("", small = true)
    private val add128 = Button(act).apply { text = "Añadir escudo de 128 colores" }
    private val add16 = Button(act).apply { text = "Añadir escudo de 16 colores" }
    private val grid = RecyclerView(act).apply {
        layoutManager = GridLayoutManager(act, 4)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    private val adapter = GridAdapter()
    private val showingEmblems get() = emblemsRadio.isChecked
    private var emblems: List<Emblems.Emblem> = emptyList()

    override val view: View = vbox(8).apply {
        val group = RadioGroup(act).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(emblemsRadio)
            addView(logosRadio)
            check(emblemsRadio.id)
            setOnCheckedChangeListener { _, _ -> refresh() }
        }
        addView(group)
        addView(info)
        addView(add128)
        addView(add16)
        grid.adapter = adapter
        addView(grid)
    }

    init {
        add128.setOnClickListener { addEmblem(Emblems.Kind.C128) }
        add16.setOnClickListener { addEmblem(Emblems.Kind.C16) }
    }

    override fun refresh() {
        if (Session.of == null) return
        if (showingEmblems) {
            emblems = Emblems.list(of)
            info.text = "Escudos de 64×64. Cargados: ${emblems.size}. Sitio libre: ${Emblems.free128(of)} de 128 colores " +
                "o ${Emblems.free16(of)} de 16 colores (cada uno de 128 ocupa como dos de 16). " +
                "Asígnalos a un club en la pestaña Equipos."
            add128.visibility = View.VISIBLE
            add16.visibility = View.VISIBLE
            adapter.update(emblems.size)
        } else {
            info.text = "80 logos de 32×32 y 16 colores (los que llevan las camisetas). Toca un hueco para cargar una imagen."
            add128.visibility = View.GONE
            add16.visibility = View.GONE
            adapter.update(Logos.COUNT)
        }
    }

    private fun addEmblem(kind: Emblems.Kind) {
        if (!act.checkWritable()) return
        act.pickImage { uri ->
            change {
                val img = Bitmaps.toIndexed(act.readImage(uri), Emblems.SIZE, kind.colors)
                val v = Emblems.add(of, kind, img)
                act.showToast("Escudo $v añadido")
            }
            refresh()
        }
    }

    private fun tapped(pos: Int) {
        if (showingEmblems) {
            val e = emblems[pos]
            val img = Emblems.image(of, e.kind, e.slot)
            AlertDialog.Builder(act).setTitle("Escudo ${e.value} (${e.kind.colors} colores)")
                .setItems(arrayOf("Reemplazar imagen", "Exportar PNG", "Borrar")) { _, w ->
                    when (w) {
                        0 -> act.pickImage { uri ->
                            change { Emblems.replace(of, e, Bitmaps.toIndexed(act.readImage(uri), Emblems.SIZE, e.kind.colors)) }
                            refresh()
                        }
                        1 -> act.savePng("escudo_${e.value}.png", Bitmaps.of(img))
                        2 -> AlertDialog.Builder(act)
                            .setMessage("¿Borrar el escudo ${e.value}? Los clubes que lo usan volverán a su escudo original.")
                            .setPositiveButton("Borrar") { _, _ -> change { Emblems.delete(of, e) }; refresh() }
                            .setNegativeButton("Cancelar", null).show()
                    }
                }.show()
        } else {
            val used = Logos.isUsed(of, pos)
            val items = if (used) arrayOf("Cargar imagen", "Exportar PNG", "Borrar") else arrayOf("Cargar imagen")
            AlertDialog.Builder(act).setTitle("Logo $pos")
                .setItems(items) { _, w ->
                    when (w) {
                        0 -> act.pickImage { uri ->
                            change { Logos.set(of, pos, Bitmaps.toIndexed(act.readImage(uri), Logos.SIZE, 16)) }
                            refresh()
                        }
                        1 -> act.savePng("logo_$pos.png", Bitmaps.of(Logos.image(of, pos)))
                        2 -> { change { Logos.delete(of, pos) }; refresh() }
                    }
                }.show()
        }
    }

    private inner class GridAdapter : RecyclerView.Adapter<GridAdapter.VH>() {
        var count = 0
        inner class VH(val box: LinearLayout, val image: ImageView, val text: TextView) : RecyclerView.ViewHolder(box)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = Bitmaps.imageView(act, dp(64))
            val tv = TextView(act).apply { textSize = 11f; gravity = Gravity.CENTER }
            val box = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(6), dp(4), dp(6))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(iv)
                addView(tv)
            }
            return VH(box, iv, tv)
        }

        override fun getItemCount() = count

        override fun onBindViewHolder(h: VH, position: Int) {
            if (showingEmblems) {
                val e = emblems[position]
                h.image.setImageBitmap(Bitmaps.of(Emblems.image(of, e.kind, e.slot)))
                h.text.text = "${e.value} · ${e.kind.colors}c"
            } else {
                if (Logos.isUsed(of, position)) h.image.setImageBitmap(Bitmaps.of(Logos.image(of, position)))
                else h.image.setImageDrawable(null)
                h.text.text = "Logo $position"
            }
            h.box.setOnClickListener { tapped(position) }
        }

        fun update(n: Int) {
            count = n
            notifyDataSetChanged()
        }
    }
}

// ============================================================================ Estadios y ligas

class NamesTab(act: MainActivity) : EditorTab(act) {
    private val stadiumsRadio = RadioButton(act).apply { text = "Estadios"; id = View.generateViewId() }
    private val leaguesRadio = RadioButton(act).apply { text = "Ligas y copas"; id = View.generateViewId() }
    private val adapter = RowAdapter { row, pos -> bind(row, pos) }
    private val stadiums get() = stadiumsRadio.isChecked

    override val view: View = vbox(8).apply {
        addView(RadioGroup(act).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(stadiumsRadio)
            addView(leaguesRadio)
            check(stadiumsRadio.id)
            setOnCheckedChangeListener { _, _ -> refresh() }
        })
        addView(label("Toca un nombre para cambiarlo.", small = true))
        addView(list().apply { adapter = this@NamesTab.adapter })
    }

    override fun refresh() {
        if (Session.of == null) return
        adapter.update(if (stadiums) Names.STADIUM_COUNT else Names.LEAGUE_COUNT)
    }

    private fun bind(r: TextRow, pos: Int) {
        val name = if (stadiums) Names.stadium(of, pos) else Names.league(of, pos)
        r.title.text = name
        r.detail.text = (if (stadiums) "Estadio" else "Liga / copa") + " $pos"
        r.setOnClickListener {
            askText(if (stadiums) "Nombre del estadio" else "Nombre de la liga o copa", name) { v ->
                change { if (stadiums) Names.setStadium(of, pos, v) else Names.setLeague(of, pos, v) }
                refresh()
            }
        }
    }
}

// ============================================================================ Tabla de equivalencias

/** Lista de las FACE especiales del option file con la FACE normal que es su copia. */
class FaceMapDialog(private val act: MainActivity) {
    private val of = act.requireOf()
    private val map = FaceMapStore.load(act)
    private val specials = act.faces.specialFaces(of)
    private val adapter = RowAdapter { row, pos -> bind(row, pos) }
    private val summary = TextView(act).apply { textSize = 12f }

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    fun show() {
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        box.addView(TextView(act).apply {
            text = "Las FACE especiales se numeran por separado en cada color de piel. Toca una y escribe el número " +
                "de la FACE normal que es su copia (búscala en el editor del juego). Déjalo vacío para quitarla."
        })
        box.addView(summary)
        val rv = RecyclerView(act).apply {
            layoutManager = LinearLayoutManager(act)
            adapter = this@FaceMapDialog.adapter
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380))
        }
        box.addView(rv)
        updateSummary()
        adapter.update(specials.size)
        AlertDialog.Builder(act).setTitle("Tabla de equivalencias")
            .setView(box)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Importar CSV") { _, _ ->
                act.pickText { text ->
                    val m = FaceMap.parse(text)
                    for ((k, v) in m.entries()) map.set(k.skin, k.special, v)
                    FaceMapStore.save(act, map)
                    act.showToast("${m.size} equivalencias importadas")
                }
            }
            .setNegativeButton("Exportar CSV") { _, _ -> act.saveText("equivalencias_face.csv", "\uFEFF" + map.serialize()) }
            .show()
    }

    private fun updateSummary() {
        val known = specials.count { map.get(it.skin, it.number) != null }
        summary.text = "Con equivalencia: $known de ${specials.size}"
    }

    private fun bind(r: TextRow, pos: Int) {
        val sf = specials[pos]
        val n = map.get(sf.skin, sf.number)
        r.title.text = "Piel ${sf.skin} · especial ${sf.number} → " + (n?.let { "normal $it" } ?: "sin equivalencia")
        r.detail.text = sf.players.joinToString { Players.name(of, it) }
        r.setOnClickListener {
            val e = EditText(act).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(n?.toString() ?: "")
            }
            val b = LinearLayout(act).apply { setPadding(dp(16), dp(8), dp(16), 0); addView(e) }
            AlertDialog.Builder(act).setTitle("FACE normal para ${Players.name(of, sf.players.first())}")
                .setView(b)
                .setPositiveButton("Aceptar") { _, _ ->
                    val t = e.text.toString().trim()
                    val v = t.toIntOrNull()
                    if (t.isNotEmpty() && (v == null || v !in 1..512)) {
                        act.showError("El número va de 1 a 512")
                    } else {
                        map.set(sf.skin, sf.number, v)
                        FaceMapStore.save(act, map)
                        adapter.notifyItemChanged(pos)
                        updateSummary()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
}
