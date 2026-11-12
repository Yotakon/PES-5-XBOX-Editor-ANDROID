package com.yotakon.pes5editor

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizaciones: compara el versionCode publicado en GitHub con el instalado, descarga el APK
 * dentro de la app y abre el instalador de Android. Todas las versiones van firmadas con la misma
 * clave, así que se instalan encima sin perder ajustes.
 */
object Updates {
    private const val BASE = "https://github.com/Yotakon/PES-5-XBOX-Editor-ANDROID/releases/download/ultima-app-editor/"
    private const val APK = BASE + "PES5XboxEditor.apk"
    private const val VERSION_JSON = BASE + "version.json"
    private const val API = "https://api.github.com/repos/Yotakon/PES-5-XBOX-Editor-ANDROID/releases/tags/ultima-app-editor"
    private const val PREFS = "actualizaciones"

    data class Release(val versionCode: Int, val date: String)

    /** Lee el versionCode de las notas de la release ("versionCode: 123"). */
    fun parse(json: String): Release? {
        val o = JSONObject(json)
        val code = Regex("versionCode:\\s*(\\d+)").find(o.optString("body"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return Release(code, o.optString("published_at").take(10))
    }

    /** Abre una conexión siguiendo las redirecciones (la descarga de GitHub redirige a otro servidor). */
    private fun open(url: String, accept: String): HttpURLConnection {
        var u = URL(url)
        repeat(6) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 20000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("User-Agent", "PES5XboxEditor/${BuildConfig.VERSION_NAME} (Android)")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                u = URL(u, loc ?: throw Exception("redirección sin destino"))
            } else if (code == 200) {
                return c
            } else {
                c.disconnect()
                throw Exception("el servidor respondió $code")
            }
        }
        throw Exception("demasiadas redirecciones")
    }

    private fun text(url: String, accept: String): String {
        val c = open(url, accept)
        try {
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun fetch(): Release {
        val first = try {
            val o = JSONObject(text(VERSION_JSON, "application/json"))
            return Release(o.getInt("versionCode"), o.optString("date"))
        } catch (e: Exception) {
            e
        }
        try {
            return parse(text(API, "application/vnd.github+json")) ?: throw Exception("la release no indica la versión")
        } catch (e: Exception) {
            throw Exception("${first.message}; ${e.message}")
        }
    }

    /**
     * Comprueba si hay una versión nueva. Con [quiet] (al abrir la app) solo avisa si la hay y comprueba
     * como mucho una vez al día. Si no hay versión nueva, dice que es la última versión.
     */
    fun check(act: MainActivity, quiet: Boolean) {
        val prefs = act.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (quiet && now - prefs.getLong("ultima", 0) < 24 * 3600 * 1000L) return
        val main = Handler(Looper.getMainLooper())
        Thread {
            var why: String? = null
            val r = try { fetch() } catch (e: Exception) { why = e.message; null }
            main.post {
                if (act.isFinishing) return@post
                if (r != null) prefs.edit().putLong("ultima", now).apply()
                val installed = BuildConfig.VERSION_CODE
                if (r != null && r.versionCode > installed) {
                    AlertDialog.Builder(act)
                        .setTitle("Hay una versión nueva")
                        .setMessage("Versión instalada: ${BuildConfig.VERSION_NAME} ($installed)\n" +
                            "Versión nueva: ${r.versionCode} (${r.date})\n\n" +
                            "Se descarga y se instala encima, sin perder los ajustes ni la tabla de equivalencias.")
                        .setPositiveButton("Actualizar") { _, _ -> download(act) }
                        .setNegativeButton("Ahora no", null)
                        .show()
                } else if (!quiet) {
                    val note = if (r == null) "\n\n(No se pudo conectar con GitHub para confirmarlo: $why)" else ""
                    act.showInfo("Actualizaciones", "Es la última versión: ${BuildConfig.VERSION_NAME} ($installed).$note")
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun download(act: MainActivity) {
        val progress = ProgressDialog(act).apply {
            setMessage("Descargando la actualización…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            max = 100
            setCancelable(false)
            show()
        }
        val main = Handler(Looper.getMainLooper())
        val out = File(File(act.cacheDir, "actualizacion").apply { mkdirs() }, "PES5XboxEditor.apk")
        Thread {
            val err = try {
                val c = open(APK, "application/octet-stream")
                try {
                    val total = c.contentLength.toLong()
                    c.inputStream.use { inp ->
                        out.outputStream().use { o ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                done += n
                                if (total > 0) main.post { progress.progress = (done * 100 / total).toInt() }
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            main.post {
                progress.dismiss()
                if (err != null) act.showError("No se pudo descargar la actualización: $err\n\nTambién puedes bajarla desde:\n$APK")
                else install(act, out)
            }
        }.start()
    }

    private fun install(act: MainActivity, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(act)
                .setTitle("Permiso necesario")
                .setMessage("Android pide permitir que esta app instale actualizaciones. Actívalo en la pantalla " +
                    "que se abre, vuelve atrás y pulsa \"Instalar\".")
                .setPositiveButton("Abrir ajustes") { _, _ ->
                    act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")))
                }
                .setNeutralButton("Instalar") { _, _ -> install(act, apk) }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(act, "${act.packageName}.archivos", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        act.startActivity(i)
    }
}
