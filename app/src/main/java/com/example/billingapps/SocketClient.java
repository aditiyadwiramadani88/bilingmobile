package com.example.billingapps;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.engineio.client.transports.Polling;
import io.socket.engineio.client.transports.WebSocket;

import java.net.URISyntaxException;
import java.util.Arrays;

public class SocketClient {

    public static void main(String[] args) {
        System.out.println("🚀 Starting Socket.IO client...");

        // ⚙️ Konfigurasi opsi koneksi
        IO.Options opts = IO.Options.builder()
                .setForceNew(false)
                .setMultiplex(true)
                // Engine.IO (dukung polling dan websocket)
                .setTransports(new String[]{Polling.NAME, WebSocket.NAME})
                .setUpgrade(true)
                .setRememberUpgrade(false)
                .setPath("/socket.io/")
                // Reconnection settings
                .setReconnection(true)
                .setReconnectionAttempts(Integer.MAX_VALUE)
                .setReconnectionDelay(1000)
                .setReconnectionDelayMax(5000)
                .setRandomizationFactor(0.5)
                .setTimeout(20000)
                .build();

        Socket socket;

        try {
            socket = IO.socket("http://localhost:3001", opts);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        // ✅ Listener event Socket.IO
        Socket finalSocket = socket;

        socket.on(Socket.EVENT_CONNECT, args1 -> {
            System.out.println("✅ Connected to server!");
            finalSocket.emit("join_device_room", "device123");
        });

        socket.on("get_lock_status", args1 ->
                System.out.println("🔒 Received lock status event: " + Arrays.toString(args1))
        );

        socket.on("frame_update", args1 ->
                System.out.println("🖼️ Frame update: " + Arrays.toString(args1))
        );

        socket.on(Socket.EVENT_DISCONNECT, args1 ->
                System.out.println("🔴 Disconnected from server!")
        );

        socket.on(Socket.EVENT_CONNECT_ERROR, args1 ->
                System.out.println("⚠️ Connection error: " + Arrays.toString(args1))
        );

        // 🚀 Jalankan koneksi
        try {
            socket.connect();
            System.out.println("🔌 Connecting...");
        } catch (Exception e) {
            System.out.println("🚨 Exception during .connect() call:");
            e.printStackTrace();
        }

        // ⏳ Biarkan program tetap jalan
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
