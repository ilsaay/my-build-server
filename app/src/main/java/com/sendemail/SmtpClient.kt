package com.sendemail

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object SmtpClient {

    data class Config(
        val host: String,
        val port: Int = 465,
        val user: String,
        val pass: String,
        val timeoutMs: Int = 15000,
    )

    class SmtpException(message: String) : IOException(message)

    fun send(
        cfg: Config,
        from: String,
        to: String,
        subject: String,
        body: String,
    ) {
        if (cfg.host.isBlank()) throw SmtpException("SMTP 服务器不能为空")
        if (cfg.user.isBlank()) throw SmtpException("邮箱账号不能为空")
        if (cfg.pass.isBlank()) throw SmtpException("授权码不能为空")
        if (to.isBlank()) throw SmtpException("收件人不能为空")

        val socket = SSLSocketFactory.getDefault()
            .createSocket() as SSLSocket
        socket.connect(InetSocketAddress(cfg.host, cfg.port), cfg.timeoutMs)
        socket.soTimeout = cfg.timeoutMs
        socket.startHandshake()

        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, "UTF-8"))
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, "UTF-8"))

            fun readLine(): String = reader.readLine() ?: throw SmtpException("连接被关闭")

            fun expect(code: String) {
                val line = readLine()
                if (!line.startsWith(code)) throw SmtpException("SMTP 返回: $line")
            }

            fun sendLine(line: String) {
                writer.write(line + "\r\n")
                writer.flush()
            }

            expect("220")

            sendLine("EHLO send.email")
            // EHLO 是多行响应，最后一行第 4 个字符是空格
            while (true) {
                val l = readLine()
                if (l.length >= 4 && l[3] == ' ') break
            }

            // AUTH PLAIN：\0user\0pass
            val authToken = Base64.getEncoder()
                .encodeToString("\u0000${cfg.user}\u0000${cfg.pass}".toByteArray(Charsets.UTF_8))
            sendLine("AUTH PLAIN $authToken")
            expect("235")

            sendLine("MAIL FROM:<$from>")
            expect("250")

            sendLine("RCPT TO:<$to>")
            expect("250")

            sendLine("DATA")
            expect("354")

            val encodedSubject = "=?UTF-8?B?" +
                Base64.getEncoder().encodeToString(subject.toByteArray(Charsets.UTF_8)) +
                "?="

            val message = buildString {
                append("From: $from\r\n")
                append("To: $to\r\n")
                append("Subject: $encodedSubject\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: 8bit\r\n")
                append("\r\n")
                append(body)
                append("\r\n")
            }

            writer.write(message)
            writer.write(".\r\n")
            writer.flush()
            expect("250")

            sendLine("QUIT")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
