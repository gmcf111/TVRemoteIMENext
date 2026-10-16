package com.tvremoteime.next.control

object ShellCommands {
    /** POSIX shell 单参数引用：用户文字中的引号、$、反引号不参与 shell 求值。 */
    fun quote(value: String): String {
        require('\u0000' !in value) { "NUL is not supported by shell arguments" }
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    fun text(value: String): String = "input text " + quote(value.replace(" ", "%s"))
}
