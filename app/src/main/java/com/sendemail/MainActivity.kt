package com.sendemail

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val host = EditText(this).apply { hint = "SMTP 服务器，如 smtp.126.com" }
        val port = EditText(this).apply { hint = "端口，465" }
        val user = EditText(this).apply { hint = "邮箱账号" }
        val pass = EditText(this).apply { hint = "授权码" }
        val to   = EditText(this).apply { hint = "收件人" }
        val subj = EditText(this).apply { hint = "主题" }
        val body = EditText(this).apply { hint = "正文" }
        val btn  = Button(this).apply { text = "发送" }
        val log  = TextView(this)

        listOf(host, port, user, pass, to, subj, body, btn, log)
            .forEach { layout.addView(it) }

        btn.setOnClickListener {
            log.text = "发送中..."
            thread {
                try {
                    SmtpClient.send(
                        SmtpClient.Config(
                            host = host.text.toString(),
                            port = port.text.toString().toIntOrNull() ?: 465,
                            user = user.text.toString(),
                            pass = pass.text.toString(),
                        ),
                        from = user.text.toString(),
                        to = to.text.toString(),
                        subject = subj.text.toString(),
                        body = body.text.toString(),
                    )
                    runOnUiThread { log.text = "发送成功" }
                } catch (e: Exception) {
                    runOnUiThread { log.text = "失败: ${e.message}" }
                }
            }
        }

        setContentView(layout)
    }
}
