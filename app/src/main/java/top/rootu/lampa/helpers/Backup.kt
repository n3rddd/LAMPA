package top.rootu.lampa.helpers

import android.content.Context
import android.os.Build
import android.os.Environment
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.appPrefs
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

object Backup {

    val DIR: File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).toString() + "/LAMPA/")
        else
            File(Environment.getExternalStorageDirectory().toString() + "/NUM/").absoluteFile

    fun Context.saveSettings(): Boolean {
        //val prefsFile = File(this.filesDir, "../shared_prefs/" + this.packageName + "_preferences.xml")
        val prefsFile = File(this.filesDir, "../shared_prefs/${Prefs.APP_PREFERENCES}.xml")
        var buf = ""
        try {
            buf = prefsFile.readText()
        } catch (e: Exception) {
            e.message?.let { App.toast(it) }
            e.printStackTrace()
        }
        if (buf.isNotBlank()) return writeFile("${Prefs.APP_PREFERENCES}.backup", buf)
        return false
    }

    fun Context.saveStorage(): Boolean {
        val prefsFile = File(this.filesDir, "../shared_prefs/${Prefs.STORAGE_PREFERENCES}.xml")
        var buf = ""
        try {
            buf = prefsFile.readText()
        } catch (e: Exception) {
            e.message?.let { App.toast(it) }
            e.printStackTrace()
        }
        if (buf.isNotBlank()) return writeFile("${Prefs.STORAGE_PREFERENCES}.backup", buf)
        return false
    }

    fun Context.loadFromBackup(which: String = Prefs.APP_PREFERENCES): Boolean {
        val buf = loadFile("$which.backup")
        if (buf.isBlank())
            return false

        //val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val pref = this.appPrefs
        val edit = pref.edit()
        try {
            val docFactory = DocumentBuilderFactory.newInstance()
            val docBuilder = docFactory.newDocumentBuilder()
            val src = InputSource(StringReader(buf))
            val doc = docBuilder.parse(src)
            val root = doc.documentElement
            var child = root.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val element = child as Element
                    val type = element.nodeName
                    val name = element.getAttribute("name")
                    when (type) {
                        "int" -> {
                            val value = element.getAttribute("value")
                            if (value.isNotBlank()) edit.putInt(name, value.toInt())
                        }

                        "long" -> {
                            val value = element.getAttribute("value")
                            if (value.isNotBlank()) edit.putLong(name, value.toLong())
                        }

                        "float" -> {
                            val value = element.getAttribute("value")
                            if (value.isNotBlank()) edit.putFloat(name, value.toFloat())
                        }

                        "string" -> {
                            val value = element.textContent
                            if (value.isNotBlank()) edit.putString(name, value)
                        }

                        "boolean" -> {
                            val value = element.getAttribute("value")
                            edit.putBoolean(name, value == "true")
                        }

                        "list", "set" -> {
                            val vl = mutableListOf<String>()
                            var ch = element.firstChild
                            while (ch != null) {
                                if (ch.nodeType == Node.ELEMENT_NODE) {
                                    val e = ch as Element
                                    vl.add(e.textContent)
                                }
                                ch = ch.nextSibling
                            }
                            if (vl.isNotEmpty()) edit.putStringSet(name, vl.toSet())
                        }
                    }
                }
                child = child.nextSibling
            }
            edit.apply()
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun writeFile(fileName: String, buf: String): Boolean {
        val dir = DIR

        if (!dir.exists())
            dir.mkdirs()
        return try {
            File(dir, fileName).writeText(buf)
            true
        } catch (e: Exception) {
            e.message?.let {
                App.toast(it)
            }
            e.printStackTrace()
            false
        }
    }

    private fun loadFile(fileName: String): String {
        val dir = DIR

        if (!dir.exists())
            return ""

        val file = File(dir, fileName)
        return try {
            file.readText()
        } catch (e: Exception) {
            e.message?.let {
                App.toast(it)
            }
            e.printStackTrace()
            ""
        }
    }
}