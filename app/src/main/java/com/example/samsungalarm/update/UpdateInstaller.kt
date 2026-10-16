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

    fun download(context: Context, url: String, onProgress: ((Int) -> Unit)?, onDone: (Result<File>) -> Unit) {
        val t = Thread {
            val res = runCatching {
                val dir = File(context.cacheDir, "update").apply { mkdirs() }
                val out = File(dir, "update.apk")
                if (out.exists()) out.delete()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "naozhong-android")
                conn.connect()
                val total = conn.contentLength.toLong()
                conn.inputStream.use { input ->
                    out.outputStream().use { fos ->
                        val buf = ByteArray(8192)
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
                out
            }
            Handler(Looper.getMainLooper()).post { onDone(res) }
        }
        t.isDaemon = true
        t.start()
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
