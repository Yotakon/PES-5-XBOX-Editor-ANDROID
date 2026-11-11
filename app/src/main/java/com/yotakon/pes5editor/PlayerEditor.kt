package com.yotakon.pes5editor

import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.yotakon.pes5editor.core.OptionFile
import com.yotakon.pes5editor.core.OptionFileException
import com.yotakon.pes5editor.core.PlayerFields
import com.yotakon.pes5editor.core.PlayerFields.Kind
import com.yotakon.pes5editor.core.PlayerFields.PField
import com.yotakon.pes5editor.core.PlayerFields.Section
import com.yotakon.pes5editor.core.Players

/**
 * Editor completo de un jugador: datos, posiciones, habilidades, estilos, habilidades especiales,
 * FACE, HAIR, cuerpo y accesorios. Solo escribe los valores que cambian.
 */
class PlayerEditor(private val act: MainActivity, private val id: Int, private val after: () -> Unit) {
    private val of: OptionFile = act.requireOf()
    private val ff = act.faces

    /** Catálogo con los campos de FACE y HAIR que se pueden corregir en Ajustes. */
    private val fields: List<PField> = PlayerFields.ALL.map {
        when (it.key) {
            "facetype" -> it.copy(field = ff.type)
            "face" -> it.copy(field = ff.face)
            "skin" -> it.copy(field = ff.skin)
            "hair" -> it.copy(field = ff.hair)
            else -> it
        }
    }

    private class Control(val f: PField, val view: View, val old: Int)
    private val controls = ArrayList<Control>()

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    private lateinit var nameEdit: EditText
    private lateinit var shirtEdit: EditText
    private lateinit var footSide: Spinner

    fun show() {
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val oldName = Players.name(of, id)
        val oldShirt = PlayerFields.shirtName(of, id)
        val oldFoot = PlayerFields.footSide(of, id)

        for (s in Section.values()) {
            val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            val open = s == Section.DATA || s == Section.FACE || s == Section.HAIR
            val header = TextView(act).apply {
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(12), 0, dp(4))
                fun title(o: Boolean) { text = (if (o) "▾ " else "▸ ") + s.label }
                title(open)
                body.visibility = if (open) View.VISIBLE else View.GONE
                setOnClickListener {
                    val now = body.visibility != View.VISIBLE
                    body.visibility = if (now) View.VISIBLE else View.GONE
                    title(now)
                }
            }
            root.addView(header)
            root.addView(body)
            if (s == Section.DATA) {
                nameEdit = text(body, "Nombre (máx. ${PlayerFields.NAME_MAX})", oldName)
                shirtEdit = text(body, "Nombre en la camiseta (máx. ${PlayerFields.SHIRT_MAX})", oldShirt)
                body.addView(label("Pie y lado"))
                footSide = spinner(PlayerFields.FOOT_SIDE_LABELS, oldFoot)
                body.addView(footSide)
            }
            if (s == Section.FACE) {
                body.addView(label("Relinkeo: elige \"Normal (predefinida)\" y el número de la FACE que quieres cargar.", small = true))
            }
            if (s == Section.HAIR) {
                body.addView(label("Con FACE especial el juego usa su propio HAIR. Al pasar a FACE normal se aplica " +
                    "el color guardado aquí: preajuste 1-62 o 63 = color RGB.", small = true))
            }
            for (f in fields.filter { it.section == s }) addControl(body, f)
        }

        val dialog = AlertDialog.Builder(act)
            .setTitle("${Players.name(of, id)} (ID $id)")
            .setView(ScrollView(act).apply { addView(root) })
            .setPositiveButton("Aceptar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            if (Session.of?.signatureOk == false) ok.isEnabled = false
            ok.setOnClickListener {
                if (save(oldName, oldShirt, oldFoot)) dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun label(t: String, small: Boolean = false) = TextView(act).apply {
        text = t
        if (small) textSize = 12f
        setPadding(0, dp(6), 0, 0)
    }

    private fun spinner(items: List<String>, sel: Int) = Spinner(act).apply {
        adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, items)
        if (sel in items.indices) setSelection(sel)
    }

    private fun text(body: LinearLayout, title: String, value: String): EditText {
        body.addView(label(title))
        return EditText(act).apply {
            setText(value)
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            body.addView(this)
        }
    }

    private fun addControl(body: LinearLayout, f: PField) {
        val cur = f.get(of, id)
        val view: View = when (val k = f.kind) {
            is Kind.Choice -> {
                body.addView(label(f.label))
                spinner(k.labels, cur)
            }
            Kind.Bool -> CheckBox(act).apply {
                text = f.label
                isChecked = cur == 1
            }
            is Kind.Num -> {
                val r = f.displayRange
                body.addView(label("${f.label}  (${r.first} a ${r.last})"))
                EditText(act).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or (if (r.first < 0) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
                    setText(cur.toString())
                    maxLines = 1
                }
            }
        }
        view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        body.addView(view)
        controls += Control(f, view, cur)
    }

    private fun value(c: Control): Int? = when (c.f.kind) {
        is Kind.Choice -> (c.view as Spinner).selectedItemPosition
        Kind.Bool -> if ((c.view as CheckBox).isChecked) 1 else 0
        is Kind.Num -> {
            val e = c.view as EditText
            val v = e.text.toString().trim().toIntOrNull()
            if (v == null || v !in c.f.displayRange) {
                e.error = "Entre ${c.f.displayRange.first} y ${c.f.displayRange.last}"
                e.requestFocus()
                null
            } else v
        }
    }

    private fun save(oldName: String, oldShirt: String, oldFoot: Int): Boolean {
        val newValues = ArrayList<Pair<Control, Int>>()
        for (c in controls) {
            val v = value(c) ?: return false
            // Un tipo de FACE desconocido (3) se conserva si el usuario no lo cambia
            if (c.f.key == "facetype" && c.old > 2 && v == c.old) continue
            if (v != c.old) newValues += c to v
        }
        val name = nameEdit.text.toString().trim()
        val shirt = shirtEdit.text.toString().trim()
        val foot = footSide.selectedItemPosition
        try {
            if (name != oldName) PlayerFields.setName(of, id, name)
            if (shirt != oldShirt) PlayerFields.setShirtName(of, id, shirt)
            if (foot != oldFoot) PlayerFields.setFootSide(of, id, foot)
            for ((c, v) in newValues) c.f.set(of, id, v)
            val statsChanged = foot != oldFoot || newValues.any {
                it.first.f.section in setOf(Section.DATA, Section.POSITIONS, Section.ABILITIES, Section.STYLES, Section.SPECIAL)
            }
            if (statsChanged) PlayerFields.STATS_EDITED.set(of, id, 1)
        } catch (e: OptionFileException) {
            act.showError(e.message ?: "Error")
            return false
        } catch (e: IllegalArgumentException) {
            act.showError(e.message ?: "Valor no válido")
            return false
        }
        if (newValues.isNotEmpty() || name != oldName || shirt != oldShirt || foot != oldFoot) {
            act.markDirty()
            after()
        }
        return true
    }
}
