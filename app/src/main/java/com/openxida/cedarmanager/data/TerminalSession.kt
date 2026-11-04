package com.openxida.cedarmanager.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.openxida.cedarmanager.domain.ShellEscaper
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit

data class TerminalSessionState(
    val title: String,
    val workingDirectory: String,
    val root: Boolean,
    val running: Boolean,
    val exitStatus: Int? = null,
)

class TerminalSessionController(private val context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mutableState = MutableStateFlow(
        TerminalSessionState("Shell", "/", false, false),
    )
    val state: StateFlow<TerminalSessionState> = mutableState.asStateFlow()
    var onScreenUpdated: ((TerminalSession) -> Unit)? = null

    private var session: TerminalSession? = null
    private val pendingCommands = ArrayDeque<String>()

    private val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            if (session === changedSession) onScreenUpdated?.invoke(changedSession)
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            if (session === changedSession) {
                mutableState.value = mutableState.value.copy(
                    title = changedSession.title?.takeIf(String::isNotBlank) ?: defaultTitle(),
                )
            }
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            if (session === finishedSession) {
                mutableState.value = mutableState.value.copy(
                    running = false,
                    exitStatus = finishedSession.exitStatus,
                )
            }
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
        }

        override fun onPasteTextFromClipboard(session: TerminalSession) {
            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return
            write(text)
        }

        override fun onBell(session: TerminalSession) {
            if (context.checkSelfPermission(android.Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) return
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35)
                }
            } catch (_: SecurityException) {
                // A revoked permission should never terminate the terminal session.
            }
        }

        override fun onColorsChanged(session: TerminalSession) = Unit
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String, message: String) { Log.e(tag, message) }
        override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
        override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
        override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
        override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
        override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "", e) }
    }

    fun start(root: Boolean, workingDirectory: String, initialCommand: String? = null): TerminalSession {
        stopCurrentSession()
        pendingCommands.clear()

        val directory = runCatching { File(workingDirectory).canonicalPath }.getOrDefault(workingDirectory)
        val launchDirectory = if (root) "/" else directory
        val shell = if (root) "/system/bin/su" else "/system/bin/sh"
        val args = if (root) rootShellArgs() else arrayOf("sh", "-i")
        val home = if (root) "/" else context.filesDir.absolutePath
        val tmpDirectory = if (root) "/data/local/tmp" else context.cacheDir.absolutePath
        val path = "${System.getenv("PATH").orEmpty()}:/system/bin:/system/xbin:/vendor/bin:/product/bin" +
            if (root) ":/data/adb/ksu/bin" else ""
        val env = System.getenv().toMutableMap().apply {
            put("TERM", "xterm-256color")
            put("COLORTERM", "truecolor")
            put("LANG", "C.UTF-8")
            put("LC_ALL", "C.UTF-8")
            put("HOME", home)
            put("TMPDIR", tmpDirectory)
            put("PATH", path)
            put("SHELL", "/system/bin/sh")
            if (root) {
                put("USER", "root")
                put("LOGNAME", "root")
            }
        }.map { (key, value) -> "$key=$value" }.toTypedArray()
        val next = TerminalSession(shell, launchDirectory, args, env, 5000, client)
        session = next
        mutableState.value = TerminalSessionState(defaultTitle(root), directory, root, true)

        if (root && directory != "/") pendingCommands.add("cd ${ShellEscaper.quote(directory)}")
        initialCommand?.takeIf(String::isNotBlank)?.let(pendingCommands::add)
        return next
    }

    fun onEmulatorReady(readySession: TerminalSession) {
        if (session !== readySession) return
        while (pendingCommands.isNotEmpty()) {
            write("${pendingCommands.removeFirst()}\r")
        }
    }

    fun write(text: String) {
        val current = session ?: return
        val bytes = text.toByteArray(Charsets.UTF_8)
        current.write(bytes, 0, bytes.size)
    }

    fun sendControl(code: Int) {
        val current = session ?: return
        val bytes = byteArrayOf(code.toByte())
        current.write(bytes, 0, bytes.size)
    }

    fun clear() {
        session?.reset()
    }

    fun close() {
        stopCurrentSession()
        session = null
        pendingCommands.clear()
    }

    private fun stopCurrentSession() {
        val current = session ?: return
        if (mutableState.value.root) {
            // The app uid cannot signal a Root-owned process; end it through its PTY.
            current.write(byteArrayOf(3), 0, 1)
            Handler(Looper.getMainLooper()).postDelayed({
                val exit = "exit\r".toByteArray(Charsets.UTF_8)
                current.write(exit, 0, exit.size)
            }, 50L)
        } else {
            current.finishIfRunning()
        }
    }

    private fun defaultTitle(root: Boolean = mutableState.value.root): String = if (root) "Root Shell" else "Shell"

    private fun rootShellArgs(): Array<String> {
        val process = runCatching {
            ProcessBuilder("/system/bin/su", "-h").redirectErrorStream(true).start()
        }.getOrNull() ?: return arrayOf("su")
        return try {
            val help = if (process.waitFor(300, TimeUnit.MILLISECONDS)) {
                process.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
            when {
                help.contains("--target") -> arrayOf("su", "--target", "1")
                help.contains("--mount-master") -> arrayOf("su", "--mount-master")
                else -> arrayOf("su")
            }
        } catch (_: Exception) {
            arrayOf("su")
        } finally {
            process.destroy()
        }
    }
}
