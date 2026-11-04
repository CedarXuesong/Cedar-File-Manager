package com.openxida.cedarmanager.domain

object ShellArguments {
    fun parse(input: String): List<String> {
        val arguments = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var started = false

        input.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                    started = true
                }
                quote != null && char == quote -> quote = null
                quote == '"' && char == '\\' -> escaped = true
                quote != null -> current.append(char)
                char == '\\' -> {
                    escaped = true
                    started = true
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    started = true
                }
                char.isWhitespace() -> if (started) {
                    arguments += current.toString()
                    current.clear()
                    started = false
                }
                else -> {
                    current.append(char)
                    started = true
                }
            }
        }

        require(!escaped) { "参数末尾的反斜杠不完整" }
        require(quote == null) { "参数引号未闭合" }
        if (started) arguments += current.toString()
        return arguments
    }
}
