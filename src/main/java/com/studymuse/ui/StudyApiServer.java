package com.studymuse.ui;

import com.studymuse.agent.AgentResponse;
import com.studymuse.services.StudyException;
import com.studymuse.tools.ToolDescription;
import com.studymuse.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small local HTTP API so other programs (for example an automated agent-testing tool) can drive
 * StudyMuse without a keyboard. It only listens on 127.0.0.1. Start it with: run.bat cli serve 8080
 *
 *   GET  /api/health                                        -> {"status":"ok","llm":"..."}
 *   GET  /api/tools                                         -> the agent's tools
 *   POST /api/agent    {"courseId":"eecs3311","message":"..."} -> answer, outcome and every tool call
 *   POST /api/command  {"command":"ask eecs3311 What is UML?"}  -> the CLI output of one command
 *
 * Like the GUI and the CLI, it only calls the StudyAgentController facade.
 */
public class StudyApiServer {

    private final StudyCLI cli;

    public StudyApiServer(StudyCLI cli) {
        this.cli = cli;
    }

    /** Starts the server and blocks until the process is stopped (Ctrl+C). */
    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/health", ex -> handle(ex, "GET", body -> Map.of("status", "ok",
                "llm", cli.getController().getLlmName())));
        server.createContext("/api/tools", ex -> handle(ex, "GET", body -> {
            List<Object> list = new ArrayList<>();
            for (ToolDescription d : cli.getController().listTools()) {
                list.add(Map.of("name", d.name(), "description", d.description(), "parameters", d.parameters()));
            }
            return Map.of("tools", list);
        }));
        server.createContext("/api/agent", ex -> handle(ex, "POST", body -> {
            String courseId = required(body, "courseId");
            String message = required(body, "message");
            AgentResponse r;
            synchronized (cli) {
                r = cli.getController().chat(courseId, message);
            }
            return r.toJsonMap();
        }));
        server.createContext("/api/command", ex -> handle(ex, "POST", body -> cli.runCaptured(required(body, "command"))));
        server.start();
        System.out.println("StudyMuse HTTP API listening on http://127.0.0.1:" + port + "/api  (Ctrl+C to stop)");
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop(0);
        }
    }

    private interface Handler {
        Map<String, Object> apply(Map<String, Object> body);
    }

    private static String required(Map<String, Object> body, String key) {
        String v = Json.str(body, key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing \"" + key + "\" in the JSON body.");
        }
        return v;
    }

    private static void handle(HttpExchange ex, String method, Handler handler) throws IOException {
        int status = 200;
        Map<String, Object> result;
        try {
            if (!ex.getRequestMethod().equalsIgnoreCase(method)) {
                status = 405;
                result = Map.of("error", "Use " + method + ".");
            } else {
                Map<String, Object> body = new LinkedHashMap<>();
                if (method.equals("POST")) {
                    String text = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    body = text.isBlank() ? body : Json.parseObject(text);
                }
                result = handler.apply(body);
            }
        } catch (IllegalArgumentException | StudyException e) {
            status = 400;
            result = Map.of("error", String.valueOf(e.getMessage()));
        } catch (RuntimeException e) {
            status = 500;
            result = Map.of("error", e.toString());
        }
        byte[] bytes = Json.stringify(result).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
