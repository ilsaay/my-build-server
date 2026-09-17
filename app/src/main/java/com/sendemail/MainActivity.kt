package com.sendemail

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var to: EditText
    private lateinit var subject: EditText
    private lateinit var body: EditText
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val saved = Prefs.load(this)

        host = EditText(this).apply { hint = "SMTP 服务器"; setText(saved.host) }
        port = EditText(this).apply { hint = "端口"; setText(saved.port.toString()) }
        user = EditText(this).apply { hint = "邮箱账号"; setText(saved.user) }
        pass = EditText(this).apply { hint = "授权码"; setText(saved.pass) }
        to = EditText(this).apply { hint = "收件人" }
        subject = EditText(this).apply { hint = "主题" }
        body = EditText(this).apply { hint = "正文"; minLines = 4 }
        log = TextView(this).apply { text = "就绪" }

        val saveBtn = Button(this).apply { text = "保存账号" }
        val sendBtn = Button(this).apply { text = "发送" }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        listOf(host, port, user, pass, to, subject, body, saveBtn, sendBtn, log)
            .forEach { layout.addView(it) }

        saveBtn.setOnClickListener {
            Prefs.save(this, Prefs.Account(
                host.text.toString().trim(),
                port.text.toString().toIntOrNull() ?: 465,
                user.text.toString().trim(),
                pass.text.toString(),
            ))
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }

        sendBtn.setOnClickListener {
            log.text = "发送中..."
            thread {
                val result = try {
                    SmtpClient.send(
                        SmtpClient.Config(
                            host = host.text.toString().trim(),
                            port = port.text.toString().toIntOrNull() ?: 465,
                            user = user.text.toString().trim(),
                            pass = pass.text.toString(),
                        ),
                        from = user.text.toString().trim(),
                        to = to.text.toString().trim(),
                        subject = subject.text.toString(),
                        body = body.text.toString(),
                    )
                    "发送成功"
                } catch (e: Exception) {
                    "失败: ${e.message}"
                }
                runOnUiThread { log.text = result }
            }
        }

        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
    }
}
