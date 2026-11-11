package com.yotakon.pes5editor

import android.content.Context
import android.net.Uri
import com.yotakon.pes5editor.core.FaceFields
import com.yotakon.pes5editor.core.FaceMap
import com.yotakon.pes5editor.core.Field
import com.yotakon.pes5editor.core.OptionFile

/** Option file abierto. Vive mientras viva el proceso (sobrevive a girar la pantalla). */
object Session {
    var of: OptionFile? = null
    var uri: Uri? = null
    var fileName: String = ""
    var dirty: Boolean = false
}

/** Posiciones de los campos de cara guardadas en Ajustes. */
object FaceSettings {
    private const val PREFS = "caras"
    val KEYS = listOf("tipoCara", "numeroCara", "piel", "pelo")
    val LABELS = listOf("Tipo de cara", "Número de cara", "Color de piel", "Pelo")

    fun defaults(): List<String> {
        val d = FaceFields()
        return listOf(d.type, d.face, d.skin, d.hair).map { "${it.offset},${it.shift},${it.mask}" }
    }

    fun load(ctx: Context): FaceFields {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = FaceFields()
        fun f(key: String, def: Field) = p.getString(key, null)?.let { runCatching { Field.parse(it) }.getOrNull() } ?: def
        return FaceFields(f(KEYS[0], d.type), f(KEYS[1], d.face), f(KEYS[2], d.skin), f(KEYS[3], d.hair))
    }

    fun current(ctx: Context): List<String> {
        val ff = load(ctx)
        return listOf(ff.type, ff.face, ff.skin, ff.hair).map { "${it.offset},${it.shift},${it.mask}" }
    }

    fun save(ctx: Context, values: List<String>) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        KEYS.forEachIndexed { i, k -> e.putString(k, values[i]) }
        e.apply()
    }
}

/** Tabla de equivalencias cara especial -> cara normal, guardada en el móvil. */
object FaceMapStore {
    private const val PREFS = "equivalencias"
    private const val KEY = "tabla"

    fun load(ctx: Context): FaceMap {
        // La tabla incluida en la app, con los cambios del usuario encima
        val m = FaceMap.defaults()
        val t = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return m
        for ((k, v) in FaceMap.parse(t).entries()) m.set(k.skin, k.special, v)
        return m
    }

    fun save(ctx: Context, m: FaceMap) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, m.serialize()).apply()
    }
}
