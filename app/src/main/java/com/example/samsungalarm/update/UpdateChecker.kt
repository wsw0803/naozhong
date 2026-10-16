package com.example.samsungalarm.update

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GitHub 最新 Release 信息。 */
data class UpdateInfo(
    val latestVersion: String,
    val releaseName: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val notes: String
)

/**
 * 联网检查 GitHub 最新 Release，并与当前版本比较。
 * 只依赖 JDK 自带的 HttpURLConnection 和 org.json，不额外引入第三方库。
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/wsw0803/naozhong/releases/latest"

    /** 语义化比较：a 是否比 b 新。 */
    fun isNewer(a: String, b: String): Boolean {
        val pa = parse(a)
        val pb = parse(b)
        val n = if (pa.size > pb.size) pa.size else pb.size
        for (i in 0 until n) {
            val x = if (i < pa.size) pa[i] else 0
            val y = if (i < pb.size) pb[i] else 0
            if (x != y) return x > y
        }
        return false
    }

    private fun parse(v: String): List<Int> {
        val cleaned = v.trim().removePrefix("v").removePrefix("V")
        val out = ArrayList<Int>()
        for (part in cleaned.split('.', '-', '+', '_')) {
            val num = part.trim().toIntOrNull()
            if (num != null) out.add(num)
        }
        return out
    }

    /**
     * 后台线程请求 GitHub，结果回到主线程。
     * 回调 Result.success(null) 表示已是最新；否则返回可用的 UpdateInfo。
     */
    fun check(currentVersion: String, onResult: (Result<UpdateInfo?>) -> Unit) {
        val t = Thread {
            val res = runCatching { fetch(currentVersion) }
            Handler(Looper.getMainLooper()).post { onResult(res) }
        }
        t.isDaemon = true
        t.start()
    }

    private fun fetch(currentVersion: String): UpdateInfo? {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "naozhong-android")
        try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("HTTP " + code)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            val tag = obj.optString("tag_name")
            var name = obj.optString("name")
            if (name.isBlank()) name = tag
            val html = obj.optString("html_url")
            val notes = obj.optString("body")
            var apk: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                var i = 0
                while (i < assets.length()) {
                    val a = assets.optJSONObject(i)
                    if (a != null) {
                        val n = a.optString("name")
                        if (n.endsWith(".apk", ignoreCase = true)) {
                            apk = a.optString("browser_download_url")
                            break
                        }
                    }
                    i++
                }
            }
            val info = UpdateInfo(
                latestVersion = tag,
                releaseName = name,
                releaseUrl = html,
                apkUrl = apk,
                notes = notes
            )
            return if (isNewer(tag, currentVersion)) info else null
        } finally {
            conn.disconnect()
        }
    }
}
