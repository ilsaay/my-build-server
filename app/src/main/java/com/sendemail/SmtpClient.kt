package com.sendemail

import java.io.*
import javax.net.ssl.SSLSocketFactory
import java.util.Base64

object SmtpClient {

    data class Config(
        val host: String,
        val port: Int = 465,
        val user: String,
        val pass: String,
    )

    fun send(
        cfg: Config,
        from: String,
        to: String,
        subject: String,
        body: String,
    ) {
        val socket = SSLSocketFactory.getDefault()
            .createSocket(cfg.host, cfg.port) as javax.net.ssl.SSLSocket
        socket.startHandshake()

        val reader = BufferedReader(InputStreamReader(socket.inputStream, "UTF-8"))
        val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, "UTF-8"))

        fun cmd(line: String): String {
            writer.write(line + "\r\n")
            writer.flush()
            return reader.readLine() ?: ""
        }

        fun expect(code: String): String {
            var line = reader.readLine() ?: ""
            if (!line.startsWith(code)) throw IOException("SMTP error: $line")
            return line
        }

        expect("220")
        cmd("EHLO send.email")
        // 读多行 EHLO 响应
        while (true) {
            val l = reader.readLine() ?: break
            if (l.length >= 4 && l[3] == ' ') break
        }

        val auth = Base64.getEncoder()
            .encodeToString("\u0000${cfg.user}\u0000${cfg.pass}".toByteArray())
        cmd("AUTH PLAIN $auth")
        expect("235")

        cmd("MAIL FROM:<$from>")
        expect("250")
        cmd("RCPT TO:<$to>")
        expect("250")
        cmd("DATA")
        expect("354")

        val msg = buildString {
            append("From: $from\r\n")
            append("To: $to\r\n")
            append("Subject: $subject\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("\r\n")
            append(body)
        }
        writer.write(msg + "\r\n.\r\n")
        writer.flush()
        expect("250")

        cmd("QUIT")
        socket.close()
    }
}
