package com.example.billingapps.services

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit


class SimpleSocketManager(
    private val serverUrl: String,
    private val deviceId: String? = null
) {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val listeners = ConcurrentHashMap<String, MutableList<(Any?) -> Unit>>()
    private var connected = false
    private val emitQueue = CopyOnWriteArrayList<Pair<String, Any?>>()
    private var reconnecting = false

    private fun log(tag: String, message: String) = println("[$tag] $message")

    fun connect() {
        if (connected || reconnecting) return

        val request = Request.Builder()
            .url("$serverUrl/socket.io/?EIO=3&transport=websocket")
            .build()

        log("SocketManager", "🚀 Connecting to $serverUrl ...")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                log("SocketManager", "✅ Connected to server")
                connected = true
                reconnecting = false
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(ws, text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                log("SocketManager", "⚠️ Closing: $code / $reason")
                connected = false
                if (code != 1000) attemptReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                log("SocketManager", "❌ Failure: ${t.message}")
                connected = false
                attemptReconnect()
            }
        })
    }

    private fun handleMessage(ws: WebSocket, text: String) {
        when {
            text.startsWith("0") -> {
                log("SocketManager", "⚙️ Handshake complete → opening namespace...")
                ws.send("40")
            }

            text.startsWith("40") -> {
                connected = true
                log("SocketManager", "✅ Namespace opened!")
                deviceId?.let {
                    emit("join_device_room", it)
                    log("SocketManager", "📡 Joined room: $it")
                }
                flushEmitQueue()
            }

            text == "2" -> {
                ws.send("3")
                log("SocketManager", "💓 Ping → Pong sent")
            }

            text.startsWith("3") -> {
                log("SocketManager", "💓 Pong received (OK)")
            }

            text.startsWith("2probe") -> {
                ws.send("3probe")
                log("SocketManager", "💡 Engine.IO probe completed")
            }

            text.startsWith("42") -> {
                try {
                    val payload = text.substring(2)
                    val json = JSONArray(payload)
                    val event = json.getString(0)
                    val data = if (json.length() > 1) json.get(1) else null
                    log("SocketManager", "📨 Event: $event | Data: $data")
                    listeners[event]?.forEach { it.invoke(data) }
                } catch (e: Exception) {
                    log("SocketManager", "⚠️ Parse error: ${e.message} | Raw: $text")
                }
            }

            else -> log("SocketManager", "📥 RAW: $text")
        }
    }

    fun emit(event: String, data: Any?) {
        val jsonData = when (data) {
            is String -> "\"$data\""
            is Number, is Boolean -> data.toString()
            is Map<*, *> -> JSONObject(data).toString()
            else -> "null"
        }

        val message = "42[\"$event\",$jsonData]"
        if (connected) {
            webSocket?.send(message)
            log("SocketManager", "📤 Emit → $message")
        } else {
            emitQueue.add(event to data)
            log("SocketManager", "⚠️ Queued emit (not connected): $event")
        }
    }

    private fun flushEmitQueue() {
        if (emitQueue.isNotEmpty()) {
            log("SocketManager", "📦 Flushing ${emitQueue.size} queued emits...")
            emitQueue.forEach { (event, data) -> emit(event, data) }
            emitQueue.clear()
        }
    }

    fun on(event: String, callback: (Any?) -> Unit) {
        listeners.computeIfAbsent(event) { mutableListOf() }.add(callback)
        log("SocketManager", "👂 Subscribed to event: $event")
    }

    fun disconnect() {
        webSocket?.close(1000, "Manual disconnect")
        connected = false
        log("SocketManager", "🔌 Disconnected")
    }

    private fun attemptReconnect() {
        if (reconnecting) return
        reconnecting = true
        log("SocketManager", "🔁 Attempting reconnect in 5s ...")
        Thread {
            Thread.sleep(5000)
            reconnecting = false
            connect()
        }.start()
    }

    fun isConnected(): Boolean = connected
}