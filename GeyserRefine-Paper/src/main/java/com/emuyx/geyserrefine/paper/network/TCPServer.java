package com.emuyx.geyserrefine.paper.network;

import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPServer {
    private final Gson gson = new Gson();
    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<Socket, PrintWriter> clients = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private final int port;
    private final boolean logRetries;

    public TCPServer(int port, boolean logRetries) {
        this.port = port;
        this.logRetries = logRetries;
    }

    public void start() {
        if (running) return;
        try {
            // 只绑定本机回环地址，防止 TCP 端口对外暴露被外部连接骚扰
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
            running = true;
            GeyserRefinePlugin.getInstance().getLogger().info("TCP Server started on 127.0.0.1:" + port);
            executor.execute(() -> {
                while (running && !serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        clients.put(client, new PrintWriter(client.getOutputStream(), true));
                        executor.execute(() -> handleClient(client));
                    } catch (IOException e) {
                        if (running && logRetries) {
                            GeyserRefinePlugin.getInstance().getLogger().warning("TCP accept error: " + e.getMessage());
                        }
                    }
                }
            });
        } catch (IOException e) {
            GeyserRefinePlugin.getInstance().getLogger().warning("Failed to start TCP Server: " + e.getMessage());
        }
    }

    private void handleClient(Socket client) {
        GeyserRefinePlugin.getInstance().getLogger().info("TCP client connected from " + client.getRemoteSocketAddress());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (GeyserRefinePlugin.getInstance().getBedrockCombatConfig().tcpConfig.logRetries) {
                    GeyserRefinePlugin.getInstance().getLogger().info("Received raw TCP: " + line);
                }
                try {
                    TCPMessage msg = gson.fromJson(line, TCPMessage.class);
                    if (msg == null) continue;
                    if (GeyserRefinePlugin.getInstance().getBedrockCombatConfig().tcpConfig.logRetries) {
                        GeyserRefinePlugin.getInstance().getLogger().info("Received TCP message type: " + msg.type);
                    }
                    TCPMessageHandler.handle(msg, client);
                } catch (JsonSyntaxException e) {
                    // 非 JSON 垃圾数据（外部探测/误连）直接忽略，不影响连接
                    GeyserRefinePlugin.getInstance().getLogger().warning(
                            "Ignoring malformed TCP data from " + client.getRemoteSocketAddress() + ": " + line);
                }
            }
        } catch (IOException e) {
            // Client disconnected
        } finally {
            GeyserRefinePlugin.getInstance().getLogger().info("TCP client disconnected");
            clients.remove(client);
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    public void broadcast(TCPMessage message) {
        String json = gson.toJson(message);
        clients.values().forEach(out -> out.println(json));
    }

    /** 是否有已连接的客户端（即 GeyserRefine 扩展是否配对）。 */
    public boolean isConnected() {
        return running && !clients.isEmpty();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        clients.keySet().forEach(socket -> {
            try { socket.close(); } catch (IOException ignored) {}
        });
        executor.shutdownNow();
    }
}