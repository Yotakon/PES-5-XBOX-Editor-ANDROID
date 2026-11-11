package com.yotakon.pes5editor

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Busca en GitHub la última compilación publicada y, si tiene un versionCode mayor que el de la app
 * instalada, ofrece descargarla. La descarga la hace el navegador; al abrir el APK, Android lo instala
 * encima de la versión actual porque todas las compilaciones van firmadas con la misma clave.
 */
object Updates {
    private const val API = "https://api.github.com/repos/Yotakon/PES-5-XBOX-Editor-ANDROID/releases/tags/ultima-app-editor"
    private const val APK = "https://github.com/Yotakon/PES-5-XBOX-Editor-ANDROID/releases/download/ultima-app-editor/PES5XboxEditor.apk"
    /** Archivo pequeño que se publica junto al APK: {"versionCode": N}. No depende de la API de GitHub. */
    private const val VERSION_JSON = "https://github.com/Yotakon/PES-5-XBOX-Editor-ANDROID/releases/download/ultima-app-editor/version.json"
    private const val PREFS = "actualizaciones"

    data class Release(val versionCode: Int, val date: String)

    /** Lee el versionCode de las notas de la release ("versionCode: 123"). */
    fun parse(json: String): Release? {
        val o = JSONObject(json)
        val code = Regex("versionCode:\\s*(\\d+)").find(o.optString("body"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return Release(code, o.optString("published_at").take(10))
    }

    private fun get(url: String, accept: String): String {
        var u = URL(url)
        // Sigue las redirecciones a mano (la descarga de GitHub redirige a otro servidor)
        repeat(5) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 15000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("User-Agent", "PES5XboxEditor/${BuildConfig.VERSION_NAME} (Android)")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    u = URL(u, c.getHeaderField("Location") ?: throw Exception("redirección sin destino"))
                    return@repeat
                }
                if (code != 200) throw Exception("el servidor respondió $code")
                return c.inputStream.bufferedReader().readText()
            } finally {
                c.disconnect()
            }
        }
        throw Exception("demasiadas redirecciones")
    }

    /** Primero el version.json de la release; si falla, la API de GitHub. Lanza la causa si fallan los dos. */
    private fun fetch(): Release {
        val first = try {
            val o = JSONObject(get(VERSION_JSON, "application/json"))
            return Release(o.getInt("versionCode"), o.optString("date"))
        } catch (e: Exception) {
            e
        }
        try {
            return parse(get(API, "application/vnd.github+json")) ?: throw Exception("la release no indica el versionCode")
        } catch (e: Exception) {
            throw Exception("${first.message}; ${e.message}")
        }
    }

    /**
     * Comprueba si hay una versión nueva. Con [quiet] (al abrir la app) no avisa si no hay novedades
     * ni si falla la conexión, y solo comprueba una vez al día.
     */
    fun check(act: MainActivity, quiet: Boolean) {
        val prefs = act.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (quiet && now - prefs.getLong("ultima", 0) < 24 * 3600 * 1000L) return
        val main = Handler(Looper.getMainLooper())
        Thread {
            var why = ""
            val r = try { fetch() } catch (e: Exception) { why = "${e.javaClass.simpleName}: ${e.message}"; null }
            main.post {
                if (act.isFinishing) return@post
                if (r != null) prefs.edit().putLong("ultima", now).apply()
                val installed = BuildConfig.VERSION_CODE
                when {
                    r == null -> if (!quiet) act.showError("No se pudo comprobar si hay actualizaciones.\n\nDetalle: $why\n\n" +
                        "Puedes descargar la última versión desde:\n$APK")
                    r.versionCode > installed -> AlertDialog.Builder(act)
                        .setTitle("Hay una versión nueva")
                        .setMessage("Versión instalada: ${BuildConfig.VERSION_NAME} ($installed)\n" +
                            "Versión nueva: ${r.versionCode} (${r.date})\n\n" +
                            "Se descargará el APK. Ábrelo al terminar e instálalo: se actualiza sin perder los ajustes ni la tabla de equivalencias.")
                        .setPositiveButton("Descargar") { _, _ -> act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK))) }
                        .setNegativeButton("Ahora no", null)
                        .show()
                    !quiet -> act.showInfo("Actualizaciones", "Tienes la última versión (${BuildConfig.VERSION_NAME}, $installed).")
                }
            }
        }.start()
    }
}
