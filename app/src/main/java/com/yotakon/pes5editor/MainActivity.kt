package com.yotakon.pes5editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yotakon.pes5editor.core.BadSignatureException
import com.yotakon.pes5editor.core.Face
import com.yotakon.pes5editor.core.FaceFields
import com.yotakon.pes5editor.core.FaceTools
import com.yotakon.pes5editor.core.OptionFile
import com.yotakon.pes5editor.core.OptionFileException
import com.yotakon.pes5editor.core.Players
import com.yotakon.pes5editor.core.TeamFile
import com.yotakon.pes5editor.core.Teams
import com.yotakon.pes5editor.core.read
import com.yotakon.pes5editor.core.write

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var openButton: Button
    private lateinit var editor: View
    private lateinit var teamSpinner: Spinner
    private lateinit var search: EditText
    private val adapter = PlayerAdapter()

    private lateinit var faces: FaceFields

    /** Índice del desplegable que significa "todos los jugadores". */
    private val allPlayersIndex = Teams.COUNT

    // Qué escribir cuando el usuario elija dónde guardar
    private var pendingWrite: ((Uri) -> Unit)? = null

    private val openXsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadOptionFile(uri)
    }
    private val openCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFacesCsv(uri)
    }
    private val openTeam = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importTeam(uri)
    }
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) exportAllTeams(uri)
    }
    private val createBinary = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        runPendingWrite(uri)
    }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        runPendingWrite(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        faces = FaceSettings.load(this)
        status = findViewById(R.id.status)
        openButton = findViewById(R.id.openButton)
        editor = findViewById(R.id.editor)
        teamSpinner = findViewById(R.id.teamSpinner)
        search = findViewById(R.id.search)
        val list = findViewById<RecyclerView>(R.id.players)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        openButton.setOnClickListener { askOpen() }
        teamSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refreshList()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshList()
        })
        showState()
    }

    override fun onResume() {
        super.onResume()
        faces = FaceSettings.load(this)
        if (Session.of != null) refreshList()
    }

    // ---------------------------------------------------------------- menú

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val open = Session.of != null
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val writes = item.itemId in setOf(R.id.action_save, R.id.action_save_as, R.id.action_import_faces,
                R.id.action_remove_special, R.id.action_import_team)
            item.isEnabled = (open && !(writes && readOnly)) || item.itemId == R.id.action_open || item.itemId == R.id.action_settings
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open -> askOpen()
            R.id.action_save -> saveOverwrite()
            R.id.action_save_as -> saveAs(requireOf(), "Guardado") { }
            R.id.action_export_faces -> exportFacesCsv()
            R.id.action_import_faces -> openCsv.launch(arrayOf("text/*", "application/*", "*/*"))
            R.id.action_remove_special -> removeSpecialDialog()
            R.id.action_export_team -> exportCurrentTeam()
            R.id.action_export_all -> openTree.launch(null)
            R.id.action_import_team -> openTeam.launch(arrayOf("*/*"))
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun requireOf(): OptionFile = Session.of!!

    // ---------------------------------------------------------------- abrir / guardar

    private fun askOpen() {
        if (Session.dirty) {
            AlertDialog.Builder(this)
                .setMessage("Hay cambios sin guardar en ${Session.fileName}. ¿Abrir otro archivo y perderlos?")
                .setPositiveButton("Abrir otro") { _, _ -> openXsv.launch(arrayOf("*/*")) }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            openXsv.launch(arrayOf("*/*"))
        }
    }

    private fun loadOptionFile(uri: Uri, allowBadSignature: Boolean = false) {
        try {
            val bytes = readAll(uri)
            val of = OptionFile.readXsv(bytes, allowBadSignature)
            Session.of = of
            Session.uri = uri
            Session.fileName = displayName(uri)
            Session.dirty = false
            setupTeams()
            showState()
            invalidateOptionsMenu()
        } catch (e: BadSignatureException) {
            AlertDialog.Builder(this)
                .setTitle("Firma desconocida")
                .setMessage(e.message + "\n\nPuedes abrirlo en solo lectura para ver jugadores y caras, " +
                    "exportar CSV y exportar equipos. No se podrá guardar.")
                .setPositiveButton("Abrir en solo lectura") { _, _ -> loadOptionFile(uri, true) }
                .setNegativeButton("Cancelar", null)
                .show()
        } catch (e: OptionFileException) {
            error(e.message ?: "No se pudo abrir")
        } catch (e: Exception) {
            error("No se pudo leer el archivo: ${e.message}")
        }
    }

    private val readOnly get() = Session.of?.signatureOk == false

    private fun saveOverwrite() {
        val uri = Session.uri ?: return
        AlertDialog.Builder(this)
            .setTitle("Guardar")
            .setMessage("Se reemplazará ${Session.fileName}. Si no tienes copia de seguridad, usa \"Guardar como…\".")
            .setPositiveButton("Guardar") { _, _ ->
                if (writeTo(uri, requireOf().toXsv())) {
                    Session.dirty = false
                    showState()
                    toast("Guardado")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Pide dónde guardar [of] como .xsv. */
    private fun saveAs(of: OptionFile, okMessage: String, after: () -> Unit) {
        pendingWrite = { uri ->
            if (writeTo(uri, of.toXsv())) {
                if (of === Session.of) {
                    Session.uri = uri
                    Session.fileName = displayName(uri)
                    Session.dirty = false
                    showState()
                }
                toast(okMessage)
                after()
            }
        }
        createBinary.launch(OptionFile.FILE_NAME)
    }

    private fun runPendingWrite(uri: Uri?) {
        val w = pendingWrite
        pendingWrite = null
        if (uri != null && w != null) w(uri)
    }

    // ---------------------------------------------------------------- lista

    private fun setupTeams() {
        val of = requireOf()
        val names = (0 until Teams.COUNT).map { "$it - ${Teams.name(of, it)}" } + "Todos los jugadores"
        teamSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        teamSpinner.setSelection(74)
    }

    private fun showState() {
        val of = Session.of
        if (of == null) {
            status.text = getString(R.string.no_file)
            openButton.visibility = View.VISIBLE
            editor.visibility = View.GONE
            return
        }
        status.text = "${Session.fileName} · " + (if (of.signatureOk) of.game.label else "SOLO LECTURA (firma desconocida)") +
            if (Session.dirty) " · cambios sin guardar" else ""
        openButton.visibility = View.GONE
        editor.visibility = View.VISIBLE
        if (teamSpinner.adapter == null) setupTeams()
    }

    private fun selectedTeam(): Int = teamSpinner.selectedItemPosition

    private fun refreshList() {
        val of = Session.of ?: return
        val team = selectedTeam()
        val rows = ArrayList<Row>()
        if (team == allPlayersIndex || team < 0) {
            for (id in Players.allIds) rows += Row(id, null)
        } else {
            Teams.squadIds(of, team).forEachIndexed { slot, id -> rows += Row(id, Teams.shirtNumber(of, team, slot)) }
        }
        val q = search.text.toString().trim().lowercase()
        adapter.rows = if (q.isEmpty()) rows else rows.filter {
            it.id.toString() == q || Players.name(of, it.id).lowercase().contains(q)
        }
        adapter.notifyDataSetChanged()
    }

    private fun markDirty() {
        Session.dirty = true
        showState()
    }

    data class Row(val id: Int, val number: Int?)

    inner class PlayerAdapter : RecyclerView.Adapter<PlayerAdapter.VH>() {
        var rows: List<Row> = emptyList()

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.name)
            val detail: TextView = v.findViewById(R.id.detail)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_player, parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val of = Session.of ?: return
            val r = rows[position]
            if (!Players.isValid(r.id)) {
                h.name.text = "<vacío>"
                h.detail.text = ""
                h.itemView.setOnClickListener(null)
                return
            }
            val f = faces.read(of, r.id)
            val dorsal = r.number?.let { "$it · " } ?: ""
            h.name.text = dorsal + Players.name(of, r.id)
            h.detail.text = "ID ${r.id} · ${FaceFields.typeName(f.type)} · cara ${f.number} · piel ${f.skin} · pelo ${f.hair}"
            h.itemView.setOnClickListener { editFaceDialog(r.id) }
        }
    }

    // ---------------------------------------------------------------- editar cara

    private fun editFaceDialog(id: Int) {
        val of = requireOf()
        val old = faces.read(of, id)
        val box = verticalBox()
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, FaceFields.TYPE_NAMES)
            setSelection(if (old.type in 0..2) old.type else 0)
        }
        box.addView(label("Tipo de cara"))
        box.addView(type)
        val number = numberField(box, "Número de cara (1-${faces.face.mask + 1})", old.number)
        val skin = numberField(box, "Color de piel (1-${faces.skin.mask + 1})", old.skin)
        val hair = numberField(box, "ID de pelo (0-${faces.hair.mask})", old.hair)
        box.addView(label("Relinkeo: elige \"Normal\" y el número de la cara que quieres cargar."))

        val dialog = AlertDialog.Builder(this)
            .setTitle("${Players.name(of, id)} (ID $id)")
            .setView(box)
            .setPositiveButton("Aceptar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            if (readOnly) dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = parseIn(number, 1..faces.face.mask + 1) ?: return@setOnClickListener
                val s = parseIn(skin, 1..faces.skin.mask + 1) ?: return@setOnClickListener
                val hr = parseIn(hair, 0..faces.hair.mask) ?: return@setOnClickListener
                val t = if (old.type > 2 && type.selectedItemPosition == 0) old.type else type.selectedItemPosition
                val nf = Face(t, n, s, hr)
                if (nf != old) {
                    faces.write(of, id, nf)
                    markDirty()
                    refreshList()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ---------------------------------------------------------------- CSV de caras

    private fun exportFacesCsv() {
        val text = FaceTools.exportCsv(requireOf(), faces)
        pendingWrite = { uri -> if (writeTo(uri, text.toByteArray(Charsets.UTF_8))) toast("Caras exportadas") }
        createCsv.launch("caras_pes5.csv")
    }

    private fun importFacesCsv(uri: Uri) {
        try {
            val r = FaceTools.importCsv(requireOf(), faces, String(readAll(uri), Charsets.UTF_8))
            if (r.changedPlayers > 0) markDirty()
            refreshList()
            val msg = StringBuilder("Jugadores modificados: ${r.changedPlayers}")
            if (r.errors.isNotEmpty()) {
                msg.append("\n\nAvisos (${r.errors.size}):")
                r.errors.take(20).forEach { msg.append('\n').append(it) }
                if (r.errors.size > 20) msg.append("\n…")
            }
            info("Importar caras", msg.toString())
        } catch (e: Exception) {
            error("No se pudo leer el CSV: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- quitar caras especiales

    private fun removeSpecialDialog() {
        val of = requireOf()
        val team = selectedTeam()
        val box = verticalBox()
        val group = RadioGroup(this)
        val all = RadioButton(this).apply { text = "Todos los jugadores"; id = View.generateViewId() }
        val onlyTeam = RadioButton(this).apply {
            id = View.generateViewId()
            if (team in 0 until Teams.COUNT) text = "Solo ${Teams.name(of, team)}" else { text = "Solo el equipo elegido"; isEnabled = false }
        }
        group.addView(all)
        group.addView(onlyTeam)
        group.check(all.id)
        box.addView(group)
        box.addView(label("Cada jugador pasa a cara normal con su número actual más este desplazamiento. " +
            "Con 0 conserva el mismo número."))
        val offset = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText("0")
        }
        box.addView(offset)
        val separate = CheckBox(this).apply {
            text = "Guardar en un option file aparte (el abierto no cambia)"
            isChecked = true
        }
        box.addView(separate)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Quitar caras especiales")
            .setView(box)
            .setPositiveButton("Aplicar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val off = parseIn(offset, -511..511) ?: return@setOnClickListener
                val ids = if (group.checkedRadioButtonId == onlyTeam.id) Teams.squadIds(of, team) else Players.allIds
                dialog.dismiss()
                if (separate.isChecked) {
                    val copy = of.copy()
                    val changes = FaceTools.removeSpecial(copy, faces, ids, off)
                    if (changes.none { it.changed }) {
                        showRemoveResult(changes, null)
                    } else {
                        saveAs(copy, "Option file sin caras especiales guardado") { showRemoveResult(changes, "Guardado en un archivo aparte.") }
                    }
                } else {
                    val changes = FaceTools.removeSpecial(of, faces, ids, off)
                    if (changes.any { it.changed }) markDirty()
                    refreshList()
                    showRemoveResult(changes, "Cambios hechos en el archivo abierto. Guárdalo para conservarlos.")
                }
            }
        }
        dialog.show()
    }

    private fun showRemoveResult(changes: List<FaceTools.Change>, extra: String?) {
        val changed = changes.count { it.changed }
        val msg = buildString {
            append("Jugadores con cara especial: ${changes.size}\n")
            append("Pasados a cara normal: $changed\n")
            if (changes.size > changed) append("Sin cambios (número fuera de rango): ${changes.size - changed}\n")
            if (extra != null && changed > 0) append('\n').append(extra)
        }
        val b = AlertDialog.Builder(this).setTitle("Quitar caras especiales").setMessage(msg)
            .setPositiveButton("Cerrar", null)
        if (changes.isNotEmpty()) {
            b.setNeutralButton("Guardar informe CSV") { _, _ ->
                val text = FaceTools.report(changes)
                pendingWrite = { uri -> if (writeTo(uri, text.toByteArray(Charsets.UTF_8))) toast("Informe guardado") }
                createCsv.launch("informe_caras_especiales.csv")
            }
        }
        b.show()
    }

    // ---------------------------------------------------------------- equipos

    private fun exportCurrentTeam() {
        val of = requireOf()
        val team = selectedTeam()
        if (!Teams.hasFormation(team)) {
            error("Elige una selección o un club. Las plantillas sin formación propia (clásicos de Japón, editadas, ML, tienda) no se pueden exportar.")
            return
        }
        val bytes = TeamFile.export(of, team).toBytes()
        pendingWrite = { uri -> if (writeTo(uri, bytes)) toast("Equipo exportado") }
        createBinary.launch("${safeName(Teams.name(of, team))}.${TeamFile.EXTENSION}")
    }

    private fun exportAllTeams(tree: Uri) {
        val of = requireOf()
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var ok = 0
        val errors = ArrayList<String>()
        for (t in 0 until Teams.COUNT) {
            if (!Teams.hasFormation(t)) continue
            val name = "${t} - ${safeName(Teams.name(of, t))}.${TeamFile.EXTENSION}"
            try {
                val doc = DocumentsContract.createDocument(contentResolver, parent, "application/octet-stream", name)
                    ?: throw Exception("no se pudo crear")
                contentResolver.openOutputStream(doc)!!.use { it.write(TeamFile.export(of, t).toBytes()) }
                ok++
            } catch (e: Exception) {
                errors += "$name: ${e.message}"
            }
        }
        info("Exportar equipos", "Equipos exportados: $ok" + if (errors.isEmpty()) "" else "\n\nErrores:\n" + errors.take(10).joinToString("\n"))
    }

    private fun importTeam(uri: Uri) {
        val of = requireOf()
        val tf = try {
            TeamFile.read(readAll(uri))
        } catch (e: OptionFileException) {
            error(e.message ?: "Archivo no válido"); return
        } catch (e: Exception) {
            error("No se pudo leer el archivo: ${e.message}"); return
        }
        val targets = (0 until Teams.COUNT).filter { Teams.hasFormation(it) && Teams.slots(it) == tf.slots }
        val box = verticalBox()
        box.addView(label("Archivo: ${tf.name} (${tf.slots} jugadores)\nEquipo destino (se reemplaza su plantilla y formación):"))
        val target = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                targets.map { "$it - ${Teams.name(of, it)}" })
            val pre = targets.indexOf(tf.team)
            if (pre >= 0) setSelection(pre)
        }
        box.addView(target)
        val players = CheckBox(this).apply {
            text = "Importar también los datos de los jugadores (${tf.playerCount}). Sobrescribe a los jugadores con esos mismos IDs."
            isChecked = true
        }
        box.addView(players)
        val club = CheckBox(this).apply {
            text = "Importar el nombre y la abreviatura del club"
            isChecked = tf.hasClubName
            isEnabled = tf.hasClubName
        }
        box.addView(club)
        AlertDialog.Builder(this)
            .setTitle("Importar equipo")
            .setView(box)
            .setPositiveButton("Importar") { _, _ ->
                val t = targets[target.selectedItemPosition]
                try {
                    tf.applyTo(of, t, players.isChecked, club.isChecked && club.isEnabled)
                    markDirty()
                    setupTeams()
                    teamSpinner.setSelection(t)
                    toast("Equipo importado. Recuerda guardar.")
                } catch (e: OptionFileException) {
                    error(e.message ?: "No se pudo importar")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------------------------------------------------------- utilidades

    private fun readAll(uri: Uri): ByteArray =
        contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception("no se pudo abrir")

    private fun writeTo(uri: Uri, bytes: ByteArray): Boolean = try {
        val out = try {
            contentResolver.openOutputStream(uri, "wt")
        } catch (e: Exception) {
            contentResolver.openOutputStream(uri, "w")
        }
        out!!.use { it.write(bytes) }
        true
    } catch (e: Exception) {
        error("No se pudo guardar: ${e.message}")
        false
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: OptionFile.FILE_NAME
        }
        return uri.lastPathSegment ?: OptionFile.FILE_NAME
    }

    private fun verticalBox(): LinearLayout {
        val pad = (20 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun numberField(box: LinearLayout, title: String, value: Int): EditText {
        box.addView(label(title))
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
            box.addView(this)
        }
    }

    private fun parseIn(field: EditText, range: IntRange): Int? {
        val v = field.text.toString().trim().toIntOrNull()
        if (v == null || v !in range) {
            field.error = "Valor entre ${range.first} y ${range.last}"
            return null
        }
        return v
    }

    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun info(title: String, msg: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Cerrar", null).show()
    }

    private fun error(msg: String) {
        AlertDialog.Builder(this).setTitle("Error").setMessage(msg).setPositiveButton("Cerrar", null).show()
    }
}
