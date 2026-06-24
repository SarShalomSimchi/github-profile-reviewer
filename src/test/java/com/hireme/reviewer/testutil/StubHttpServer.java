package com.hireme.reviewer.testutil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StubHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.start();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public void handle(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    public static String body(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
