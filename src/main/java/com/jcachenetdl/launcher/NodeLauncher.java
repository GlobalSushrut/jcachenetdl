package com.jcachenetdl.launcher;

import com.jcachenetdl.common.PeerInfo;
import com.jcachenetdl.ledger.DistributedLedger;
import com.jcachenetdl.network.NetworkManager;
import com.jcachenetdl.network.PeerDiscovery;
import com.jcachenetdl.network.PeerHandler;
import com.jcachenetdl.node.CacheEngine;
import com.jcachenetdl.node.FileServer;
import com.jcachenetdl.util.LogUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Entry point for the P2P CDN + Distributed Ledger application.
 */
public class NodeLauncher {
    private static final int DEFAULT_PORT = 8080;
    
    private String peerId;
    private boolean running;
    private PeerDiscovery peerDiscovery;
    private NetworkManager networkManager;
    private DistributedLedger ledger;
    private CacheEngine cacheEngine;
    private PeerHandler peerHandler;
    private FileServer fileServer;
    
    /**
     * Initializes the node with a specific port.
     * 
     * @param port The port to listen on
     * @throws IOException If there's an error starting the network manager
     */
    public void initialize(int port) throws IOException {
        // Generate a unique peer ID
        this.peerId = UUID.randomUUID().toString().substring(0, 8);
        System.out.println("Initializing node with peer ID: " + peerId);
        
        // Initialize logging
        LogUtil.init();
        
        // Create components
        this.ledger = new DistributedLedger(peerId);
        this.cacheEngine = new CacheEngine(peerId, ledger);
        this.peerDiscovery = new PeerDiscovery(peerId);
        
        // Get hostname and IP for the network manager
        String hostname = InetAddress.getLocalHost().getHostName();
        this.networkManager = new NetworkManager(peerId, hostname, port, peerDiscovery);
        
        // Create handlers
        this.peerHandler = new PeerHandler(peerId, peerDiscovery, networkManager, ledger, cacheEngine);
        this.fileServer = new FileServer(peerId, cacheEngine, peerDiscovery, peerHandler, ledger);
        
        // Start the network manager
        networkManager.start();
        
        System.out.println("Node initialized and listening on port " + port);
        System.out.println("Use 'help' to see available commands");
        
        this.running = true;
    }
    
    /**
     * Starts the CLI loop.
     */
    public void startCLI() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        
        while (running) {
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
            } catch (IOException e) {
                System.err.println("Error reading command: " + e.getMessage());
            }
        }
    }
    
    /**
     * Processes a CLI command.
     * 
     * @param command The command
     * @param args The command arguments
     */
    protected void processCommand(String command, String args) {
        try {
            switch (command) {
                case "help":
                    showHelp();
                    break;
                    
                case "start":
                    // Already started
                    System.out.println("Node is already running.");
                    break;
                    
                case "stop":
                    shutdown();
                    break;
                    
                case "addpeer":
                    addPeer(args);
                    break;
                    
                case "upload":
                    uploadFile(args);
                    break;
                    
                case "fetch":
                    fetchFile(args);
                    break;
                    
                case "stats":
                    showStats();
                    break;
                    
                case "peers":
                    listPeers();
                    break;
                    
                case "ledger":
                    ledgerInfo();
                    break;
                    
                case "exit":
                case "quit":
                    shutdown();
                    running = false;
                    System.out.println("Exiting...");
                    break;
                    
                default:
                    System.out.println("Unknown command: " + command);
                    System.out.println("Type 'help' for a list of commands");
            }
        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Shows help information.
     */
    private void showHelp() {
        System.out.println("Available commands:");
        System.out.println("  help                     - Show this help");
        System.out.println("  addpeer <host:port>      - Add a peer manually");
        System.out.println("  upload <filepath>        - Upload a file to the network");
        System.out.println("  fetch <filehash> [path]  - Fetch a file from the network");
        System.out.println("  stats                    - Show node statistics");
        System.out.println("  peers                    - List connected peers");
        System.out.println("  ledger                   - Show ledger information");
        System.out.println("  stop                     - Stop the node");
        System.out.println("  exit, quit               - Exit the application");
    }
    
    /**
     * Adds a peer manually.
     * 
     * @param args The peer address arguments (host:port)
     * @throws Exception If there's an error adding the peer
     */
    private void addPeer(String args) throws Exception {
        String[] parts = args.split(":");
        if (parts.length != 2) {
            System.out.println("Invalid peer format. Use <host:port>");
            return;
        }
        
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        
        boolean success = peerHandler.joinNetwork(host, port);
        if (success) {
            System.out.println("Successfully connected to peer at " + host + ":" + port);
        } else {
            System.out.println("Failed to connect to peer at " + host + ":" + port);
        }
    }
    
    /**
     * Uploads a file to the network.
     * 
     * @param args The file path arguments
     * @throws Exception If there's an error uploading the file
     */
    private void uploadFile(String args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Please specify a file path");
            return;
        }
        
        File file = new File(args);
        if (!file.exists() || !file.isFile()) {
            System.out.println("File not found or is not a regular file: " + args);
            return;
        }
        
        System.out.println("Uploading file: " + file.getName());
        long startTime = System.currentTimeMillis();
        String fileHash = fileServer.uploadFile(file);
        long endTime = System.currentTimeMillis();
        
        System.out.println("File uploaded successfully");
        System.out.println("File hash: " + fileHash);
        System.out.println("Upload time: " + (endTime - startTime) + " ms");
    }
    
    /**
     * Fetches a file from the network.
     * 
     * @param args The file hash and optional output path arguments
     * @throws Exception If there's an error fetching the file
     */
    private void fetchFile(String args) throws Exception {
        String[] parts = args.split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) {
            System.out.println("Please specify a file hash");
            return;
        }
        
        String fileHash = parts[0];
        String outputPath = parts.length > 1 ? parts[1] : "downloaded_" + fileHash.substring(0, 8) + ".file";
        
        // Get file info
        Map<String, Object> fileInfo = fileServer.getFileInfo(fileHash);
        if (fileInfo == null) {
            System.out.println("File not found in the network: " + fileHash);
            return;
        }
        
        System.out.println("Fetching file: " + fileHash);
        System.out.println("Total chunks: " + fileInfo.get("totalChunks"));
        System.out.println("Estimated size: " + formatSize((long) fileInfo.get("estimatedSize")));
        
        long startTime = System.currentTimeMillis();
        boolean success = fileServer.fetchFile(fileHash, new File(outputPath));
        long endTime = System.currentTimeMillis();
        
        if (success) {
            System.out.println("File fetched successfully");
            System.out.println("Saved to: " + outputPath);
            System.out.println("Fetch time: " + (endTime - startTime) + " ms");
        } else {
            System.out.println("Failed to fetch file");
        }
    }
    
    /**
     * Shows node statistics.
     */
    private void showStats() {
        System.out.println("Node Statistics:");
        System.out.println("Peer ID: " + peerId);
        System.out.println("Peers: " + peerDiscovery.getActivePeerCount() + " active, " + 
                           peerDiscovery.getAllPeers().size() + " total");
        System.out.println("Cache: " + cacheEngine.getCacheSize() + " items");
        System.out.println("Ledger: " + ledger.getChainSize() + " blocks");
    }
    
    /**
     * Lists all known peers.
     */
    private void listPeers() {
        System.out.println("Known peers:");
        for (PeerInfo peer : peerDiscovery.getAllPeers()) {
            System.out.println(" - " + peer.getId() + " at " + peer.getHost() + ":" + peer.getPort() + 
                             (peer.isActive() ? " (active)" : " (inactive)"));
        }
    }
    
    /**
     * Shows ledger information.
     */
    private void ledgerInfo() {
        System.out.println("Ledger Information:");
        System.out.println("Chain size: " + ledger.getChainSize() + " blocks");
        System.out.println("Chain valid: " + ledger.validateChain());
        System.out.println("Current block actions: " + ledger.getCurrentBlock().getActions().size());
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (ledger.getChainSize() > 0) {
            System.out.println("Last block: " + ledger.getLastBlock().getBlockId());
            System.out.println("Last block time: " + dateFormat.format(new Date(ledger.getLastBlock().getTimestamp())));
            System.out.println("Last block actions: " + ledger.getLastBlock().getActions().size());
        }
    }
    
    /**
     * Formats a size in bytes to a human-readable string.
     * 
     * @param size The size in bytes
     * @return A human-readable string
     */
    private String formatSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        
        return new DecimalFormat("#,##0.##").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
    
    /**
     * Shuts down the node.
     */
    public void shutdown() {
        if (ledger != null && ledger.getCurrentBlock().getActions().size() > 0) {
            ledger.sealCurrentBlock();
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
        
        System.out.println("Node shutdown complete");
    }
    
    /**
     * Main entry point.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            int port = DEFAULT_PORT;
            
            // Parse port from command line arguments
            if (args.length > 0) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
                }
            }
            
            // Create and start the node
            NodeLauncher launcher = new NodeLauncher();
            launcher.initialize(port);
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(launcher::shutdown));
            
            // Start the CLI
            launcher.startCLI();
        } catch (Exception e) {
            System.err.println("Error starting node: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
