package com.yotakon.pes5editor

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yotakon.pes5editor.core.Field

/** Permite corregir las posiciones de los campos de cara sin cambiar la app. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Formato: posición,desplazamiento,máscara (posición dentro de los datos del jugador, " +
                "como en PESFan Editor). Los valores por defecto vienen de PES5_WE9_LE_OF_Team_Editor y " +
                "todavía no se han comprobado en una partida de Xbox."
        })
        val current = FaceSettings.current(this)
        val fields = FaceSettings.LABELS.mapIndexed { i, label ->
            root.addView(TextView(this).apply {
                text = label
                setPadding(0, pad, 0, 0)
            })
            EditText(this).apply {
                setText(current[i])
                inputType = InputType.TYPE_CLASS_TEXT
                root.addView(this)
            }
        }
        root.addView(Button(this).apply {
            text = "Guardar"
            setOnClickListener {
                val values = fields.map { it.text.toString().trim() }
                val bad = values.indexOfFirst { runCatching { Field.parse(it) }.isFailure }
                if (bad >= 0) {
                    fields[bad].error = "Valor no válido"
                    return@setOnClickListener
                }
                FaceSettings.save(this@SettingsActivity, values)
                Toast.makeText(this@SettingsActivity, "Guardado", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        root.addView(Button(this).apply {
            text = "Restaurar valores por defecto"
            setOnClickListener {
                val d = FaceSettings.defaults()
                fields.forEachIndexed { i, f -> f.setText(d[i]) }
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
