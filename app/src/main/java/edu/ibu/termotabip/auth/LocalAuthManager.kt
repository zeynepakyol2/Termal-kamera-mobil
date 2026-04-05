package edu.ibu.termotabip.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest

private const val TAG   = "LocalAuthManager"
private const val PREF  = "termotabip_auth"
private const val KEY_USERS    = "users"
private const val KEY_LOGGED   = "logged_in_user"

data class User(
    val username: String,
    val fullName: String,
    val role: String = "Hemşire"  // Hemşire, Doktor, Teknisyen
)

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Yerel SharedPreferences tabanlı auth sistemi.
 * Şifreler SHA-256 ile hashlenir.
 * Sunum amaçlı — production'da güvenli bir backend kullanın.
 */
object LocalAuthManager {

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ── Kayıt ol ──────────────────────────────────────────────────────────
    fun register(
        context: Context,
        username: String,
        fullName: String,
        password: String,
        role: String = "Hemşire"
    ): AuthResult {
        val u = username.trim().lowercase()
        if (u.length < 3) return AuthResult.Error("Kullanıcı adı en az 3 karakter olmalı")
        if (password.length < 4) return AuthResult.Error("Şifre en az 4 karakter olmalı")
        if (fullName.isBlank()) return AuthResult.Error("Ad soyad boş olamaz")

        val users = loadUsers(context)
        if (users.has(u)) return AuthResult.Error("Bu kullanıcı adı zaten alınmış")

        val obj = JSONObject().apply {
            put("fullName", fullName.trim())
            put("password", hash(password))
            put("role", role)
        }
        users.put(u, obj)
        saveUsers(context, users)
        Log.i(TAG, "Kayıt: $u ($role)")
        return AuthResult.Success(User(u, fullName.trim(), role))
    }

    // ── Giriş yap ─────────────────────────────────────────────────────────
    fun login(context: Context, username: String, password: String): AuthResult {
        val u     = username.trim().lowercase()
        val users = loadUsers(context)

        if (!users.has(u)) return AuthResult.Error("Kullanıcı bulunamadı")

        val obj      = users.getJSONObject(u)
        val stored   = obj.getString("password")
        if (stored != hash(password)) return AuthResult.Error("Şifre hatalı")

        val user = User(
            username = u,
            fullName = obj.getString("fullName"),
            role     = obj.optString("role", "Hemşire")
        )
        // Oturumu kaydet
        prefs(context).edit().putString(KEY_LOGGED, u).apply()
        Log.i(TAG, "Giriş: $u")
        return AuthResult.Success(user)
    }

    // ── Çıkış yap ─────────────────────────────────────────────────────────
    fun logout(context: Context) {
        prefs(context).edit().remove(KEY_LOGGED).apply()
        Log.i(TAG, "Çıkış yapıldı")
    }

    // ── Oturum kontrolü ───────────────────────────────────────────────────
    fun currentUser(context: Context): User? {
        val u     = prefs(context).getString(KEY_LOGGED, null) ?: return null
        val users = loadUsers(context)
        if (!users.has(u)) return null
        val obj = users.getJSONObject(u)
        return User(u, obj.getString("fullName"), obj.optString("role", "Hemşire"))
    }

    fun isLoggedIn(context: Context) = currentUser(context) != null

    // ── Yardımcılar ───────────────────────────────────────────────────────
    private fun loadUsers(context: Context): JSONObject {
        val str = prefs(context).getString(KEY_USERS, "{}") ?: "{}"
        return try { JSONObject(str) } catch (_: Exception) { JSONObject() }
    }

    private fun saveUsers(context: Context, users: JSONObject) {
        prefs(context).edit().putString(KEY_USERS, users.toString()).apply()
    }

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    // Demo kullanıcı oluştur (ilk çalıştırmada)
    fun ensureDemoUser(context: Context) {
        val users = loadUsers(context)
        if (!users.has("demo")) {
            register(context, "demo", "Demo Kullanıcı", "1234", "Hemşire")
            Log.i(TAG, "Demo kullanıcı oluşturuldu — kullanıcı: demo, şifre: 1234")
        }
    }
}
