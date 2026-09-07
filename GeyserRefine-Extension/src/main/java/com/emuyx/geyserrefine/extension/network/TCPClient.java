package com.emuyx.geyserrefine.extension.network;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.geysermc.geyser.api.extension.Extension;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TCPClient {
    private final Gson gson = new Gson();
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;
    private final String host;
    private final int port;
    private final boolean logRetries;
    private ScheduledExecutorService retryExecutor;
    private Extension extension;
    private int retryCount = 0;

    public TCPClient(String host, int port, boolean logRetries) {
        this.host = host;
        this.port = port;
        this.logRetries = logRetries;
    }

    public void startWithRetry(Extension extension) {
        this.extension = extension;
        retryExecutor = Executors.newSingleThreadScheduledExecutor();
        extension.logger().info("Starting TCP client, target " + host + ":" + port);
        retryExecutor.scheduleWithFixedDelay(() -> {
            if (!connected) {
                retryCount++;
                if (logRetries && extension != null) {
                    extension.logger().info("TCP connection attempt #" + retryCount + " to " + host + ":" + port + "...");
                }
                connect();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void connect() {
        try {
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (IOException ignored) {}
            }
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;
            retryCount = 0;
            if (extension != null) {
                extension.logger().info("TCP client successfully connected to " + host + ":" + port);
            }
            new Thread(this::listen, "TCPClient-Listener").start();
        } catch (IOException e) {
            connected = false;
            if (logRetries && extension != null) {
                extension.logger().warning("TCP connection failed: " + e.getMessage());
            }
        }
    }

    private void listen() {
        if (extension != null) {
            extension.logger().info("TCP listener thread started");
        }
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (extension != null && logRetries) {
                    extension.logger().info("Received raw TCP: " + line);
                }
                try {
                    TCPMessage msg = gson.fromJson(line, TCPMessage.class);
                    if (msg == null) continue;
                    if (extension != null && logRetries) {
                        extension.logger().info("Received TCP message: " + msg.type);
                    }
                    MessageHandler.handle(msg);
                } catch (JsonSyntaxException e) {
                    if (extension != null) {
                        extension.logger().warning("Ignoring malformed TCP data: " + line);
                    }
                }
            }
        } catch (IOException e) {
            if (connected) {
                connected = false;
                if (extension != null) {
                    extension.logger().warning("TCP connection lost: " + e.getMessage() + " (will retry)");
                }
            }
        } finally {
            if (connected) {
                connected = false;
                if (extension != null) {
                    extension.logger().info("TCP connection closed");
                }
            }
        }
    }

    public void sendMessage(TCPMessage msg) {
        if (extension != null && logRetries) {
            extension.logger().info("Attempting to send TCP message: " + msg.type + " to " + host + ":" + port);
        }
        if (!connected) {
            if (logRetries && extension != null) {
                extension.logger().info("TCP not connected, attempting immediate reconnect...");
            }
            connect();
            if (!connected) {
                if (logRetries && extension != null) {
                    extension.logger().warning("Failed to send message: not connected");
                }
                return;
            }
        }
        String json = gson.toJson(msg);
        out.println(json);
        if (extension != null && logRetries) {
            extension.logger().info("Sent TCP message: " + msg.type + " (json: " + json + ")");
        }
    }

    public void disconnect() {
        if (retryExecutor != null) {
            retryExecutor.shutdownNow();
            retryExecutor = null;
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        connected = false;
        if (extension != null) {
            extension.logger().info("TCP client disconnected");
        }
    }

    public boolean isConnected() {
        return connected;
    }
}