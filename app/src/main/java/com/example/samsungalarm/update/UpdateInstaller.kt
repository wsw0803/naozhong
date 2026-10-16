package com.example.samsungalarm.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object UpdateInstaller {

    private const val THREADS = 4

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
                val ok = multiThread(cand, out, onProgress)
                if (ok && out.length() > 0) return out
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: IllegalStateException("download failed")
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        c.setRequestProperty("Accept", "*/*")
        return c
    }

    private fun multiThread(url: String, out: File, onProgress: ((Int) -> Unit)?): Boolean {
        val probe = open(url)
        probe.connect()
        val code = probe.responseCode
        if (code !in 200..299) {
            probe.disconnect()
            return false
        }
        val total = probe.contentLength.toLong()
        val acceptRanges = probe.headerFields?.get("Accept-Ranges")?.any { it.contains("bytes") } == true
        probe.disconnect()

        if (total <= 0L) {
            return singleThread(url, out, onProgress, 0L)
        }
        if (!acceptRanges) {
            return singleThread(url, out, onProgress, total)
        }

        RandomAccessFile(out, "rw").use { raf -> raf.setLength(total) }
        val per = total / THREADS
        val readTotal = AtomicLong(0L)
        val lastPct = AtomicInteger(-1)
        val failed = AtomicBoolean(false)
        val latch = CountDownLatch(THREADS)
        for (i in 0 until THREADS) {
            val start = i * per
            val end = if (i == THREADS - 1) total - 1 else (start + per - 1)
            Thread {
                try {
                    part(url, out, start, end, readTotal, total, onProgress, lastPct)
                } catch (e: Exception) {
                    failed.set(true)
                } finally {
                    latch.countDown()
                }
            }.apply { isDaemon = true }.start()
        }
        latch.await()
        return !failed.get()
    }

    private fun part(
        url: String, out: File, start: Long, end: Long,
        readTotal: AtomicLong, total: Long,
        onProgress: ((Int) -> Unit)?, lastPct: AtomicInteger
    ) {
        val conn = open(url)
        conn.setRequestProperty("Range", "bytes=" + start + "-" + end)
        conn.connect()
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("part HTTP " + conn.responseCode)
        }
        RandomAccessFile(out, "rw").use { raf ->
            raf.seek(start)
            conn.inputStream.use { input ->
                val buf = ByteArray(65536)
                var pos = start
                while (pos <= end) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val remain = (end - pos + 1).toInt()
                    val w = if (n > remain) remain else n
                    raf.write(buf, 0, w)
                    pos += w
                    val done = readTotal.addAndGet(w.toLong())
                    if (onProgress != null && total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastPct.get()) {
                            lastPct.set(pct)
                            Handler(Looper.getMainLooper()).post { onProgress(pct) }
                        }
                    }
                    if (w < n) break
                }
            }
        }
        conn.disconnect()
    }

    private fun singleThread(url: String, out: File, onProgress: ((Int) -> Unit)?, total: Long): Boolean {
        val conn = open(url)
        conn.connect()
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return false
        }
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
        return true
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
