package com.jcachenetdl.launcher;

import com.jcachenetdl.api.RestApiServer;
import com.jcachenetdl.common.PeerInfo;
import com.jcachenetdl.config.ConfigurationManager;
import com.jcachenetdl.ledger.DistributedLedger;
import com.jcachenetdl.metrics.MetricsManager;
import com.jcachenetdl.network.NettyNetworkManager;
import com.jcachenetdl.network.NetworkManager;
import com.jcachenetdl.network.PeerDiscovery;
import com.jcachenetdl.network.PeerHandler;
import com.jcachenetdl.node.CacheEngine;
import com.jcachenetdl.node.FileServer;
import com.jcachenetdl.security.SecurityManager;
import com.jcachenetdl.util.LogUtil;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production launcher for the JCacheNetDL system.
 * Includes advanced configuration, metrics, and security features.
 */
public class ProductionLauncher {
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Logger logger;
    
    private final ConfigurationManager config;
    private final MetricsManager metrics;
    
    private String peerId;
    private PeerDiscovery peerDiscovery;
    private NetworkManager networkManager;
    private DistributedLedger ledger;
    private CacheEngine cacheEngine;
    private PeerHandler peerHandler;
    private FileServer fileServer;
    private SecurityManager securityManager;
    private RestApiServer apiServer;
    
    /**
     * Creates a new ProductionLauncher instance.
     */
    public ProductionLauncher() {
        // Initialize configuration and logging
        this.config = ConfigurationManager.getInstance();
        LogUtil.init();
        this.logger = LogUtil.getLogger(ProductionLauncher.class);
        this.metrics = MetricsManager.getInstance();
    }
    
    /**
     * Initializes the node.
     * 
     * @throws Exception If there's an error initializing the node
     */
    public void initialize() throws Exception {
        // Generate peer ID or load from config
        this.peerId = config.getString("node.id", UUID.randomUUID().toString().substring(0, 8));
        
        // Initialize security if enabled
        if (config.getBoolean("security.enabled", false)) {
            String authToken = config.getString("security.auth.token", "");
            if (authToken.isEmpty()) {
                // Generate random token if not configured
                authToken = UUID.randomUUID().toString();
                config.setValue("security.auth.token", authToken);
                config.saveConfiguration();
            }
            
            this.securityManager = new SecurityManager(peerId, authToken);
            logger.info("Security enabled with authentication");
        } else {
            logger.info("Security disabled");
        }
        
        // Get hostname and port
        String hostname = InetAddress.getLocalHost().getHostName();
        int networkPort = config.getInt("network.port", 8087);
        logger.info("Checking availability of network port: {}", networkPort);
        if (!isPortAvailable(networkPort)) {
            logger.error("Network port {} is already in use. Please free the port or change it in config.", networkPort);
            throw new RuntimeException("Port conflict on network port " + networkPort);
        }
        
        // Create components
        this.ledger = new DistributedLedger(peerId);
        this.cacheEngine = new CacheEngine(peerId, ledger);
        this.peerDiscovery = new PeerDiscovery(peerId);
        this.networkManager = new NettyNetworkManager(peerId, hostname, networkPort, peerDiscovery);
        
        // Create handlers
        this.peerHandler = new PeerHandler(peerId, peerDiscovery, networkManager, ledger, cacheEngine);
        this.fileServer = new FileServer(peerId, cacheEngine, peerDiscovery, peerHandler, ledger);
        
        // Start network components
        try {
            networkManager.start();
        } catch (Exception e) {
            logger.error("Error starting node: {}", e.getMessage(), e);
            throw e; // Propagate the exception for clarity
        }
        
        // Initialize API server if enabled
        boolean apiEnabled = config.getBoolean("api.enabled", true);
        int apiPort = config.getInt("api.port", 8088);
        if (apiEnabled) {
            logger.info("Checking availability of API port: {}", apiPort);
            if (!isPortAvailable(apiPort)) {
                logger.error("API port {} is already in use. Please free the port or change it in config.", apiPort);
                throw new RuntimeException("Port conflict on API port " + apiPort);
            }
            this.apiServer = new RestApiServer(peerId, apiPort, cacheEngine, peerDiscovery, ledger, securityManager);
            try {
                apiServer.start();
            } catch (Exception e) {
                logger.error("Error starting API server: {}", e.getMessage(), e);
                throw e; // Propagate the exception for clarity
            }
            logger.info("REST API server listening on port {}", apiPort);
        }
        
        // Register JVM shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
        // Initialize metrics
        initializeMetrics();
        
        logger.info("Node initialized with ID: {} listening on port {}", peerId, networkPort);
        if (securityManager != null) {
            logger.info("Security public key: {}", securityManager.getPublicKeyEncoded().substring(0, 20) + "...");
        }
    }
    
    /**
     * Initializes metrics tracking.
     */
    private void initializeMetrics() {
        // Set initial gauges
        metrics.setGauge("cache.size", cacheEngine.getCacheSize());
        metrics.setGauge("peers.count", peerDiscovery.getActivePeerCount());
        metrics.setGauge("ledger.blocks", ledger.getChainSize());
        
        // Start metrics tracking thread
        Thread metricsThread = new Thread(() -> {
            while (running.get()) {
                try {
                    metrics.setGauge("cache.size", cacheEngine.getCacheSize());
                    metrics.setGauge("peers.count", peerDiscovery.getActivePeerCount());
                    metrics.setGauge("ledger.blocks", ledger.getChainSize());
                    metrics.setGauge("memory.used", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
                    metrics.setGauge("memory.free", Runtime.getRuntime().freeMemory());
                    
                    Thread.sleep(10000); // Update every 10 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error updating metrics", e);
                }
            }
        });
        metricsThread.setDaemon(true);
        metricsThread.start();
    }
    
    /**
     * Starts the node in background mode (without CLI).
     */
    public void startInBackground() {
        logger.info("Running in background mode");
        
        // Keep running until shutdown signal
        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Starts the CLI interface.
     */
    public void startCLI() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        
        System.out.println("JCacheNetDL v2.0 - Production Ready");
        System.out.println("Node ID: " + peerId);
        System.out.println("Type 'help' for available commands");
        
        while (running.get()) {
            try {
                System.out.print("> ");
                String line = reader.readLine();
                
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                
                String[] parts = line.trim().split("\\s+", 2);
                String command = parts[0].toLowerCase();
                String args = parts.length > 1 ? parts[1] : "";
                
                processCommand(command, args);
            } catch (Exception e) {
                logger.error("Error in CLI", e);
            }
        }
    }
    
    /**
     * Processes a CLI command.
     * 
     * @param command The command
     * @param args The command arguments
     */
    private void processCommand(String command, String args) {
        try {
            // Delegate to the NodeLauncher command processor
            NodeLauncher launcher = new NodeLauncher();
            launcher.processCommand(command, args);
            
            // Additional production commands
            switch (command) {
                case "security":
                    handleSecurityCommand(args);
                    break;
                    
                case "config":
                    handleConfigCommand(args);
                    break;
                    
                case "metrics":
                    System.out.println("Metrics:");
                    System.out.println("Cache size: " + metrics.getGauge("cache.size"));
                    System.out.println("Active peers: " + metrics.getGauge("peers.count"));
                    System.out.println("Ledger blocks: " + metrics.getGauge("ledger.blocks"));
                    System.out.println("File upload count: " + metrics.getCounter("file.upload.count"));
                    System.out.println("File fetch count: " + metrics.getCounter("file.fetch.count"));
                    System.out.println("Cache hit rate: " + (metrics.getCounter("cache.hit.count") * 100.0 / 
                        Math.max(1, metrics.getCounter("cache.access.count"))) + "%");
                    break;
                    
                case "api":
                    System.out.println("API Status:");
                    if (apiServer != null) {
                        System.out.println("REST API is running on port " + config.getInt("api.port", 8081));
                        if (securityManager != null) {
                            System.out.println("Authentication token: " + securityManager.getAuthToken());
                        }
                    } else {
                        System.out.println("REST API is not enabled");
                    }
                    break;
            }
        } catch (Exception e) {
            logger.error("Error executing command: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handles security-related commands.
     * 
     * @param args Command arguments
     */
    private void handleSecurityCommand(String args) {
        if (securityManager == null) {
            System.out.println("Security is disabled");
            return;
        }
        
        if (args.equals("status")) {
            System.out.println("Security is enabled");
            System.out.println("Authentication token: " + securityManager.getAuthToken());
            System.out.println("Public key: " + securityManager.getPublicKeyEncoded().substring(0, 20) + "...");
        } else if (args.startsWith("peer ")) {
            // Add peer public key
            String[] parts = args.substring(5).split("\\s+", 2);
            if (parts.length == 2) {
                boolean added = securityManager.addPeerPublicKey(parts[0], parts[1]);
                if (added) {
                    System.out.println("Added public key for peer: " + parts[0]);
                } else {
                    System.out.println("Failed to add public key");
                }
            }
        } else {
            System.out.println("Available security commands:");
            System.out.println("  security status - Show security status");
            System.out.println("  security peer <id> <key> - Add a peer's public key");
        }
    }
    
    /**
     * Handles configuration-related commands.
     * 
     * @param args Command arguments
     */
    private void handleConfigCommand(String args) {
        if (args.isEmpty()) {
            System.out.println("Current configuration:");
            System.out.println("network.port = " + config.getInt("network.port", 8080));
            System.out.println("api.port = " + config.getInt("api.port", 8081));
            System.out.println("api.enabled = " + config.getBoolean("api.enabled", true));
            System.out.println("security.enabled = " + config.getBoolean("security.enabled", false));
            System.out.println("cache.max.size.mb = " + config.getInt("cache.max.size.mb", 1024));
            System.out.println("metrics.enabled = " + config.getBoolean("metrics.enabled", true));
        } else if (args.equals("reload")) {
            config.reload();
            System.out.println("Configuration reloaded");
        } else if (args.contains("=")) {
            String[] parts = args.split("=", 2);
            String key = parts[0].trim();
            String value = parts[1].trim();
            
            config.setValue(key, value);
            try {
                config.saveConfiguration();
                System.out.println("Configuration updated: " + key + " = " + value);
            } catch (Exception e) {
                System.out.println("Error saving configuration: " + e.getMessage());
            }
        } else {
            System.out.println("Available config commands:");
            System.out.println("  config - Show current configuration");
            System.out.println("  config reload - Reload configuration from file");
            System.out.println("  config key=value - Set configuration value");
        }
    }
    
    /**
     * Shuts down the node.
     */
    public void shutdown() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        logger.info("Shutting down node");
        
        // Seal current ledger block
        if (ledger != null && ledger.getCurrentBlock().getActions().size() > 0) {
            ledger.sealCurrentBlock();
        }
        
        // Shutdown components
        if (apiServer != null) {
            apiServer.stop();
        }
        
        if (peerHandler != null) {
            peerHandler.shutdown();
        }
        
        if (fileServer != null) {
            fileServer.shutdown();
        }
        
        if (networkManager != null) {
            networkManager.stop();
        }
        
        if (peerDiscovery != null) {
            peerDiscovery.shutdown();
        }
        
        // Final metrics report
        if (metrics != null) {
            metrics.shutdown();
        }
        
        logger.info("Node shutdown complete");
    }
    
    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (SocketException e) {
            return false;
        } catch (IOException e) {
            logger.error("Error checking port availability: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Main entry point.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            ProductionLauncher launcher = new ProductionLauncher();
            launcher.initialize();
            
            if (args.length > 0 && args[0].equals("--background")) {
                launcher.startInBackground();
            } else {
                launcher.startCLI();
            }
        } catch (Exception e) {
            System.err.println("Error starting node: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
