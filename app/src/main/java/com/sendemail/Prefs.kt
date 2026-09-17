package com.sendemail

import android.content.Context

object Prefs {
    private const val NAME = "send_email_prefs"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_USER = "user"
    private const val KEY_PASS = "pass"

    data class Account(val host: String, val port: Int, val user: String, val pass: String)

    fun save(ctx: Context, a: Account) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_HOST, a.host)
            .putInt(KEY_PORT, a.port)
            .putString(KEY_USER, a.user)
            .putString(KEY_PASS, a.pass)
            .apply()
    }

    fun load(ctx: Context): Account {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return Account(
            host = sp.getString(KEY_HOST, "smtp.126.com") ?: "smtp.126.com",
            port = sp.getInt(KEY_PORT, 465),
            user = sp.getString(KEY_USER, "") ?: "",
            pass = sp.getString(KEY_PASS, "") ?: "",
        )
    }
}
