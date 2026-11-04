package com.openxida.cedarmanager.domain

/** Builds a terminal command that supports both text scripts and ELF files with a .sh name. */
object ShellCommandBuilder {
    fun launch(path: String, arguments: List<String> = emptyList()): String {
        val quotedPath = ShellEscaper.quote(path)
        val quotedArguments = arguments.joinToString(" ", transform = ShellEscaper::quote)
        val invocation = listOf(quotedPath, quotedArguments)
            .filter(String::isNotBlank)
            .joinToString(" ")
        val magic = "$(od -An -tx1 -N4 $quotedPath 2>/dev/null | tr -d ' \\n')"
        return "if [ \"$magic\" = \"7f454c46\" ]; then $invocation; else sh $invocation; fi"
    }
}
