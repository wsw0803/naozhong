package com.example.samsungalarm.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {

    private val MIRRORS = listOf(
        "https://ghproxy.net/",
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        ""
    )

    private fun candidates(url: String): List<String> {
        if (!url.contains("github.com") && !url.contains("githubusercontent.com")) {
            return listOf(url)
        }
        return MIRRORS.map { it + url }
    }

    fun download(context: Context, url: String, onProgress: ((Int) -> Unit)?, onDone: (Result<File>) -> Unit) {
        val t = Thread {
            val res = runCatching { downloadBest(context, url, onProgress) }
            Handler(Looper.getMainLooper()).post { onDone(res) }
        }
        t.isDaemon = true
        t.start()
    }

    private fun downloadBest(context: Context, url: String, onProgress: ((Int) -> Unit)?): File {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val out = File(dir, "update.apk")
        var lastErr: Exception? = null
        for (cand in candidates(url)) {
            try {
                if (out.exists()) out.delete()
                tryOne(cand, out, onProgress)
                if (out.length() > 0) return out
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: IllegalStateException("download failed")
    }

    private fun tryOne(url: String, out: File, onProgress: ((Int) -> Unit)?) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.setRequestProperty("Accept", "*/*")
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP " + code)
        }
        val total = conn.contentLength.toLong()
        conn.inputStream.use { input ->
            out.outputStream().use { fos ->
                val buf = ByteArray(65536)
                var read = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    fos.write(buf, 0, n)
                    read += n
                    if (total > 0 && onProgress != null) {
                        val pct = (read * 100 / total).toInt()
                        if (pct != last) {
                            last = pct
                            Handler(Looper.getMainLooper()).post { onProgress(pct) }
                        }
                    }
                }
                fos.flush()
            }
        }
        conn.disconnect()
    }

    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(i)
    }
}
