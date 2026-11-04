package com.openxida.cedarmanager.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CommandResult(
    val code: Int,
    val out: List<String>,
    val err: List<String>,
) {
    val success: Boolean get() = code == 0
    val text: String get() = (out + err).joinToString("\n").trim()
}

class RootShell {
    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    suspend fun run(command: String): CommandResult = withContext(Dispatchers.IO) {
        val result = Shell.cmd(command).exec()
        CommandResult(result.code, result.out, result.err)
    }

    suspend fun runOrThrow(command: String): CommandResult {
        val result = run(command)
        if (!result.success) error(result.text.ifBlank { "Root 命令失败，退出码 ${result.code}" })
        return result
    }
}
