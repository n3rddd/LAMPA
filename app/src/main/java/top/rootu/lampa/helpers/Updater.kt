package top.rootu.lampa.helpers

import android.content.Intent
import android.net.Uri
import android.text.Spanned
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.models.Release
import top.rootu.lampa.models.Releases
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection

object Updater {
    private const val RELEASE_LINK =
        "https://api.github.com/repos/lampa-app/LAMPA/releases"
    private var releases: Releases? = null
    private var newVersion: Release? = null

    fun check(): Boolean {
        try {
            val url = URL(RELEASE_LINK)
            val conn = url.openConnection() as HttpsURLConnection
            conn.connect()
            val body =
                conn.inputStream?.bufferedReader(Charset.defaultCharset())?.readText()
                    ?: return false
            val gson = Gson()
            releases = gson.fromJson(body, Releases::class.java)
            releases?.let {
                it.forEach { rel ->
                    val majorVersionDouble: Double = try {
                        rel.tag_name.replace("v", "").substringBefore(".").toDouble()
                    } catch (npe: NumberFormatException) {
                        0.0
                    }
                    val lastVersionDouble: Double = try {
                        rel.tag_name.replace("v", "").substringAfter(".").toDouble()
                    } catch (npe: NumberFormatException) {
                        0.0
                    }
                    val majorCurrVersionDouble: Double = try {
                        BuildConfig.VERSION_NAME.substringBefore(".").toDouble()
                    } catch (npe: NumberFormatException) {
                        0.0
                    }
                    val currVersionDouble: Double = try {
                        BuildConfig.VERSION_NAME.substringAfter(".").toDouble()
                    } catch (npe: NumberFormatException) {
                        0.0
                    }
                    if (majorVersionDouble >= majorCurrVersionDouble && lastVersionDouble > currVersionDouble) {
                        newVersion = rel
                        return true
                    }
                }
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getVersion(): String {
        if (newVersion == null)
            check()
        return newVersion?.tag_name?.replace("v", "") ?: ""
    }

    fun getOverview(): Spanned {
        var ret = ""

        releases?.forEach { rel ->
            val majorVersionDouble: Double = try {
                rel.tag_name.replace("v", "").substringBefore(".").toDouble()
            } catch (npe: NumberFormatException) {
                0.0
            }
            val lastVersionDouble: Double = try {
                rel.tag_name.replace("v", "").substringAfter(".").toDouble()
            } catch (npe: NumberFormatException) {
                0.0
            }
            val majorCurrVersionDouble: Double = try {
                BuildConfig.VERSION_NAME.substringBefore(".").toDouble()
            } catch (npe: NumberFormatException) {
                0.0
            }
            val currVersionDouble: Double = try {
                BuildConfig.VERSION_NAME.substringAfter(".").toDouble()
            } catch (npe: NumberFormatException) {
                0.0
            }
            if (majorVersionDouble >= majorCurrVersionDouble && lastVersionDouble > currVersionDouble) {
                ret += "<font color='white'><b>${rel.tag_name}</b></font> <br>"
                ret += "<i>${rel.body.replace("\r\n", "<br/>")}</i><br/><br/>"
            } else {
                ret += "${rel.tag_name}<br>"
                ret += "<i>${rel.body.replace("\r\n", "<br/>")}</i><br/><br/>"
            }
        }
        return HtmlCompat.fromHtml(ret.trim(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private val download = Any()

    private fun downloadApk(file: File, onProgress: ((prc: Int) -> Unit)?) {
        synchronized(download) {
            newVersion?.let { rel ->
                if (file.exists())
                    file.delete()
                var link = ""
                for (asset in rel.assets) {
                    link = asset.browser_download_url
                }
                if (link.isNotEmpty()) {
                    val url = URL(link)
                    val conn = url.openConnection() as HttpsURLConnection
                    conn.connect()
                    conn.inputStream.use { input ->
                        FileOutputStream(file).use { fileOut ->
                            val contentLength = conn.contentLength
                            if (onProgress == null)
                                input?.copyTo(fileOut)
                            else {
                                val buffer = ByteArray(65535)
                                val length = contentLength + 1
                                var offset: Long = 0
                                while (true) {
                                    val read = input?.read(buffer) ?: 0
                                    offset += read
                                    val prc = (offset * 100 / length).toInt()
                                    onProgress(prc)
                                    if (read <= 0)
                                        break
                                    fileOut.write(buffer, 0, read)
                                }
                                fileOut.flush()
                            }
                            fileOut.flush()
                            fileOut.close()
                        }
                    }
                    conn.disconnect()
                }
            }
        }
    }

    fun installNewVersion(onProgress: ((prc: Int) -> Unit)?) {
        val ctx = App.context
        if (newVersion == null && !check())
            return

        newVersion?.let {
            val destination = File(
                ctx.getExternalFilesDir(null),
                "LAMPA.apk"
            ).apply {
                mkdirs()
                deleteOnExit()
            }

            downloadApk(destination, onProgress)

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                val uri = Uri.fromFile(destination)
                val install = Intent(Intent.ACTION_VIEW)
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                install.setDataAndType(uri, "application/vnd.android.package-archive")
                if (install.resolveActivity(ctx.packageManager) != null)
                    App.context.startActivity(install)
                else
                    App.toast(R.string.error_app_not_found)
            } else {
                val fileUri =
                    FileProvider.getUriForFile(
                        ctx,
                        BuildConfig.APPLICATION_ID + ".update_provider",
                        destination
                    )
                val install = Intent(Intent.ACTION_VIEW, fileUri)
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (install.resolveActivity(ctx.packageManager) != null)
                    ctx.startActivity(install)
                else
                    App.toast(R.string.error_app_not_found)
            }
        }
    }
}