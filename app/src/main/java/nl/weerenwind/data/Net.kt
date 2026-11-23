package nl.weerenwind.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * De enige netwerkpoort van de app. Elk verzoek gaat hierdoor en wordt lokaal gelogd
 * (privacy-logboek, 7 dagen). Er gaan geen cookies, ID's of account-gegevens mee.
 */
object Net {
    private const val LOG = "netlog.tsv"
    private const val WEEK = 7L * 24 * 3600 * 1000

    fun get(ctx: Context, url: String, purpose: String): String {
        val u = URL(url)
        var code = -1
        var bytes = 0
        try {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.useCaches = false
            c.setRequestProperty("User-Agent", "WeerEnWind/1.0 (Android)")
            c.setRequestProperty("Accept", "application/json")
            code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            bytes = body.length
            c.disconnect()
            if (code !in 200..299) throw IOException("HTTP $code van ${u.host}")
            return body
        } finally {
            log(ctx, u.host, purpose, code, bytes)
        }
    }

    @Synchronized
    private fun log(ctx: Context, host: String, purpose: String, code: Int, bytes: Int) {
        try {
            File(ctx.filesDir, LOG).appendText("${System.currentTimeMillis()}\t$host\t$purpose\t$code\t$bytes\n")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun entries(ctx: Context): List<LogEntry> {
        val f = File(ctx.filesDir, LOG)
        if (!f.exists()) return emptyList()
        val cutoff = System.currentTimeMillis() - WEEK
        val lines = f.readLines().filter { line ->
            line.substringBefore('\t').toLongOrNull()?.let { it >= cutoff } == true
        }
        f.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n"))
        return lines.mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 5) null
            else LogEntry(p[0].toLong(), p[1], p[2], p[3].toIntOrNull() ?: -1, p[4].toIntOrNull() ?: 0)
        }.reversed()
    }
}
