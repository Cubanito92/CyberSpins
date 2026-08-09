package com.example.ui

import android.content.Context

/**
 * Persists the user's streaming server configuration (host, port, mount, password,
 * protocol, etc.) to local SharedPreferences so it survives app restarts. The app
 * ships with NO fixed/hardcoded server: whatever the user types in "Servidor" and
 * saves here is what gets loaded on the next launch and used to connect.
 */
object StreamConfigStore {
    private const val PREFS_NAME = "stream_config_prefs"

    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_PORT = "port"
    private const val KEY_MOUNT = "mount_point"
    private const val KEY_PASSWORD = "password"
    private const val KEY_STATION_NAME = "station_name"
    private const val KEY_GENRE = "genre"
    private const val KEY_BITRATE = "bitrate_kbps"
    private const val KEY_PROTOCOL = "protocol"

    fun load(context: Context): StreamConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = StreamConfig()

        return StreamConfig(
            serverUrl = prefs.getString(KEY_SERVER_URL, defaults.serverUrl) ?: defaults.serverUrl,
            port = prefs.getString(KEY_PORT, defaults.port) ?: defaults.port,
            mountPoint = prefs.getString(KEY_MOUNT, defaults.mountPoint) ?: defaults.mountPoint,
            password = prefs.getString(KEY_PASSWORD, defaults.password) ?: defaults.password,
            stationName = prefs.getString(KEY_STATION_NAME, defaults.stationName) ?: defaults.stationName,
            genre = prefs.getString(KEY_GENRE, defaults.genre) ?: defaults.genre,
            bitrateKbps = prefs.getInt(KEY_BITRATE, defaults.bitrateKbps),
            protocol = prefs.getString(KEY_PROTOCOL, defaults.protocol) ?: defaults.protocol
        )
    }

    fun save(context: Context, config: StreamConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SERVER_URL, config.serverUrl)
            .putString(KEY_PORT, config.port)
            .putString(KEY_MOUNT, config.mountPoint)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_STATION_NAME, config.stationName)
            .putString(KEY_GENRE, config.genre)
            .putInt(KEY_BITRATE, config.bitrateKbps)
            .putString(KEY_PROTOCOL, config.protocol)
            .apply()
    }
}
