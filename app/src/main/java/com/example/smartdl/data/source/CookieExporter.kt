package com.example.smartdl.data.source

import java.io.File
class CookieExporter {
    fun exportDomainCookies(domain: String, cookieHeader: String?, outFile: File) {
        outFile.outputStream().use { output ->
            output.write("# Netscape HTTP Cookie File\n".toByteArray())
            if (cookieHeader.isNullOrBlank()) return
            val cookies = cookieHeader.split(";").mapNotNull { part ->
                val pair = part.trim().split("=", limit = 2)
                if (pair.size == 2) pair[0] to pair[1] else null
            }
            val expires = 0
            val flag = "FALSE"
            val path = "/"
            cookies.forEach { (name, value) ->
                val line = listOf(
                    domain,
                    flag,
                    path,
                    "FALSE",
                    expires.toString(),
                    name,
                    value
                ).joinToString("\t") + "\n"
                output.write(line.toByteArray())
            }
        }
    }
}
