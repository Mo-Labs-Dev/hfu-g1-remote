package com.example.g1remote

import android.content.Context

/**
 * Address of the G1 computer (Jetson). Saved on the tablet, changeable in the
 * operator screen, so a new Wi-Fi never needs a new app build.
 *   G1-Robot Wi-Fi (Jetson hotspot): 10.42.0.1  (never changes)
 *   School Wi-Fi: whatever `ip -4 -br addr show wlP1p1s0` shows
 */
object G1Host {
    const val HOTSPOT_IP = "10.42.0.1"
    /** Jetson on CSA-H Wi-Fi (default). */
    const val DEFAULT_IP = "10.150.68.160"
    private const val PREFS = "g1_host"
    private const val KEY = "ip_v3"   // v3: ignore addresses saved by older builds

    @Volatile
    var ip: String = DEFAULT_IP
        private set

    fun init(context: Context) {
        ip = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, DEFAULT_IP) ?: DEFAULT_IP
    }

    /** Returns false if the text is not a valid IPv4 address. */
    fun save(context: Context, newIp: String): Boolean {
        val clean = newIp.trim()
        val ok = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(clean)
            ?.groupValues?.drop(1)?.all { it.toInt() in 0..255 } == true
        if (!ok) return false
        ip = clean
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, clean).apply()
        return true
    }

    fun url(port: Int): String = "http://$ip:$port"
}
