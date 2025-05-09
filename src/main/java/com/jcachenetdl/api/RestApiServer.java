package com.jcachenetdl.api;

import com.google.gson.Gson;
import com.jcachenetdl.config.ConfigurationManager;
import com.jcachenetdl.metrics.MetricsManager;
import com.jcachenetdl.node.CacheEngine;
import com.jcachenetdl.network.PeerDiscovery;
import com.jcachenetdl.ledger.DistributedLedger;
import com.jcachenetdl.security.SecurityManager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight REST API server for node management and monitoring.
 */
public class RestApiServer {
    private static final Logger logger = LoggerFactory.getLogger(RestApiServer.class);
    private final HttpServer server;
    private final Gson gson;
    private final String peerId;
    private final CacheEngine cacheEngine;
    private final PeerDiscovery peerDiscovery;
    private final DistributedLedger ledger;
    private final SecurityManager securityManager;
    private final ConfigurationManager config;
    private final MetricsManager metrics;
    
    // Add basic rate limiting with atomic integers for thread-safety
    private AtomicInteger requestCount = new AtomicInteger(0);
    private long lastResetTime = System.currentTimeMillis();
    private static final int RATE_LIMIT = 5; // Lowered to 5 for testing purposes
    private static final long RESET_INTERVAL_MS = 60000; // 1 minute

    /**
     * Creates a new REST API server.
     * 
     * @param peerId The peer ID
     * @param port The port to listen on
     * @param cacheEngine The cache engine
     * @param peerDiscovery The peer discovery service
     * @param ledger The distributed ledger
     * @param securityManager The security manager (can be null if security is disabled)
     * @throws IOException If there's an error starting the server
     */
    public RestApiServer(String peerId, int port, CacheEngine cacheEngine, 
                         PeerDiscovery peerDiscovery, DistributedLedger ledger,
                         SecurityManager securityManager) throws IOException {
        this.peerId = peerId;
        this.cacheEngine = cacheEngine;
        this.peerDiscovery = peerDiscovery;
        this.ledger = ledger;
        this.securityManager = securityManager;
        this.gson = new Gson();
        this.config = ConfigurationManager.getInstance();
        this.metrics = MetricsManager.getInstance();
        
        // Create HTTP server
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));
        
        // Register endpoints
        registerEndpoints();
        
        logger.info("REST API server initialized on port {}", port);
    }
    
    /**
     * Registers all API endpoints.
     */
    private void registerEndpoints() {
        // Status endpoint
        server.createContext("/api/status", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (isRateLimited()) {
                    logger.warn("Rate limited response sent for /api/status");
                    String response = "Too many requests. Please try again later.";
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(429, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }
                if (!authenticate(exchange)) {
                    return;
                }
                
                Map<String, Object> status = new HashMap<>();
                status.put("peerId", peerId);
                status.put("status", "online");
                status.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
                status.put("peerCount", peerDiscovery.getActivePeerCount());
                status.put("cacheItemCount", cacheEngine.getCacheSize());
                status.put("ledgerBlockCount", ledger.getChainSize());
                status.put("securityEnabled", securityManager != null);
                
                sendJsonResponse(exchange, 200, status);
            }
        });
        
        // Metrics endpoint
        server.createContext("/api/metrics", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (isRateLimited()) {
                    String response = "Too many requests. Please try again later.";
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(429, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }
                if (!authenticate(exchange)) {
                    return;
                }
                
                Map<String, Object> allMetrics = metrics.getAllMetrics();
                sendJsonResponse(exchange, 200, allMetrics);
            }
        });
        
        // Peers endpoint
        server.createContext("/api/peers", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (isRateLimited()) {
                    String response = "Too many requests. Please try again later.";
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(429, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }
                if (!authenticate(exchange)) {
                    return;
                }
                
                sendJsonResponse(exchange, 200, peerDiscovery.getAllPeers());
            }
        });
        
        // Ledger info endpoint
        server.createContext("/api/ledger", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (isRateLimited()) {
                    String response = "Too many requests. Please try again later.";
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(429, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }
                if (!authenticate(exchange)) {
                    return;
                }
                
                Map<String, Object> ledgerInfo = new HashMap<>();
                ledgerInfo.put("blockCount", ledger.getChainSize());
                ledgerInfo.put("currentBlockActionCount", ledger.getCurrentBlock().getActions().size());
                ledgerInfo.put("isValid", ledger.validateChain());
                if (ledger.getChainSize() > 0) {
                    ledgerInfo.put("lastBlockId", ledger.getLastBlock().getBlockId());
                    ledgerInfo.put("lastBlockTime", ledger.getLastBlock().getTimestamp());
                }
                
                sendJsonResponse(exchange, 200, ledgerInfo);
            }
        });
        
        // Configuration endpoint - GET
        server.createContext("/api/config", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (isRateLimited()) {
                    String response = "Too many requests. Please try again later.";
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(429, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }
                if (!authenticate(exchange)) {
                    return;
                }
                
                // For security reasons, we don't expose all config values
                Map<String, Object> safeConfig = new HashMap<>();
                safeConfig.put("network.port", config.getInt("network.port", 8080));
                safeConfig.put("cache.max.size.mb", config.getInt("cache.max.size.mb", 1024));
                safeConfig.put("cache.chunk.size.kb", config.getInt("cache.chunk.size.kb", 1024));
                safeConfig.put("ledger.max.actions.per.block", config.getInt("ledger.max.actions.per.block", 100));
                safeConfig.put("metrics.enabled", config.getBoolean("metrics.enabled", true));
                safeConfig.put("security.enabled", config.getBoolean("security.enabled", false));
                
                sendJsonResponse(exchange, 200, safeConfig);
            }
        });
        
        // Cache add endpoint
        server.createContext("/api/cache/add", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (isRateLimited()) {
                    String response = "Too many requests. Please try again later.";
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(429, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }
                if (!authenticate(exchange)) {
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    // Read request body
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Type type = new TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> data = gson.fromJson(body, type);
                    String key = data.get("key");
                    String value = data.get("value");
                    if (key != null && value != null) {
                        cacheEngine.addItem(key, value);
                        sendJsonResponse(exchange, 200, Map.of("status", "added"));
                    } else {
                        sendErrorResponse(exchange, 400, "Missing key or value");
                    }
                } else {
                    sendErrorResponse(exchange, 405, "Method not allowed");
                }
            }
        });
        
        // Cache list endpoint
        server.createContext("/api/cache/list", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (isRateLimited()) {
                    String response = "Too many requests. Please try again later.";
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(429, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }
                if (!authenticate(exchange)) {
                    return;
                }
                if ("GET".equals(exchange.getRequestMethod())) {
                    Map<String, String> cacheItems = cacheEngine.listItems();
                    sendJsonResponse(exchange, 200, cacheItems);
                } else {
                    sendErrorResponse(exchange, 405, "Method not allowed");
                }
            }
        });
    }
    
    private boolean isRateLimited() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTime > RESET_INTERVAL_MS) {
            requestCount.set(0);
            lastResetTime = currentTime;
            logger.info("Reset request count to 0 at time: {}", currentTime);
        }
        int count = requestCount.incrementAndGet();
        if (count > RATE_LIMIT) {
            requestCount.decrementAndGet();
            logger.warn("Rate limit exceeded, request denied. Current count: {}, Limit: {}", count, RATE_LIMIT);
            return true;
        }
        logger.debug("Request allowed. Current count: {}, Time: {}", count, currentTime);
        return false;
    }
    
    /**
     * Authenticates an API request.
     * 
     * @param exchange The HTTP exchange
     * @return True if authentication was successful or not required
     * @throws IOException If there's an error writing the response
     */
    private boolean authenticate(HttpExchange exchange) throws IOException {
        // If security is disabled, allow all requests
        if (securityManager == null || !config.getBoolean("security.enabled", false)) {
            return true;
        }
        
        // Check for authentication token
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Authentication required");
            return false;
        }
        
        String token = authHeader.substring("Bearer ".length());
        if (!securityManager.verifyAuthToken(token)) {
            sendErrorResponse(exchange, 401, "Invalid authentication token");
            return false;
        }
        
        return true;
    }
    
    /**
     * Sends a JSON response.
     * 
     * @param exchange The HTTP exchange
     * @param statusCode The response status code
     * @param data The data to send
     * @throws IOException If there's an error sending the response
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    /**
     * Sends an error response.
     * 
     * @param exchange The HTTP exchange
     * @param statusCode The error status code
     * @param message The error message
     * @throws IOException If there's an error sending the response
     */
    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        sendJsonResponse(exchange, statusCode, error);
    }
    
    /**
     * Starts the REST API server.
     */
    public void start() {
        server.start();
        logger.info("REST API server started");
    }
    
    /**
     * Stops the REST API server.
     */
    public void stop() {
        server.stop(0);
        logger.info("REST API server stopped");
    }
}
