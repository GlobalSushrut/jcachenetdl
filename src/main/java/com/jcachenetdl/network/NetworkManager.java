package com.jcachenetdl.network;

import com.jcachenetdl.common.PeerInfo;
import com.jcachenetdl.util.LogUtil;
import com.jcachenetdl.util.SerializationUtil;
import org.slf4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;

/**
 * Manages the network communication between peers.
 */
public class NetworkManager {
    private final String peerId;
    private final String host;
    private final int port;
    private final PeerDiscovery peerDiscovery;
    private final ConcurrentMap<String, MessageHandler> messageHandlers;
    private final ExecutorService connectionExecutor;
    private final ExecutorService messageExecutor;
    private ServerSocket serverSocket;
    private boolean running;
    private final Logger logger;
    
    public NetworkManager(String peerId, String host, int port, PeerDiscovery peerDiscovery) {
        this.peerId = peerId;
        this.host = host;
        this.port = port;
        this.peerDiscovery = peerDiscovery;
        this.messageHandlers = new ConcurrentHashMap<>();
        this.connectionExecutor = Executors.newFixedThreadPool(10);
        this.messageExecutor = Executors.newFixedThreadPool(20);
        this.logger = LogUtil.getLogger(NetworkManager.class);
    }
    
    /**
     * Starts the network manager.
     * 
     * @throws IOException If there's an error starting the server socket
     */
    public void start() throws IOException {
        if (running) {
            return;
        }
        
        serverSocket = new ServerSocket(port);
        running = true;
        
        // Start the server socket to accept incoming connections
        connectionExecutor.submit(this::acceptConnections);
        
        logger.info("Network manager started on port {}", port);
    }
    
    /**
     * Stops the network manager.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        // Close the server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }
        
        // Shutdown executors
        connectionExecutor.shutdownNow();
        messageExecutor.shutdownNow();
        
        logger.info("Network manager stopped");
    }
    
    /**
     * Accepts incoming connections.
     */
    private void acceptConnections() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                connectionExecutor.submit(() -> handleConnection(socket));
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting connection", e);
                }
                // If not running, the socket was closed deliberately
            }
        }
    }
    
    /**
     * Handles an incoming connection.
     * 
     * @param socket The socket of the incoming connection
     */
    private void handleConnection(Socket socket) {
        try (
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
        ) {
            // Read the message
            Message message = (Message) in.readObject();
            
            // Process the message in a separate thread
            messageExecutor.submit(() -> {
                try {
                    Message response = processMessage(message);
                    if (response != null) {
                        // Send response
                        out.writeObject(response);
                        out.flush();
                    }
                } catch (Exception e) {
                    logger.error("Error processing message: {}", message, e);
                }
            });
        } catch (Exception e) {
            logger.error("Error handling connection from {}", socket.getInetAddress(), e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logger.error("Error closing socket", e);
            }
        }
    }
    
    /**
     * Processes an incoming message.
     * 
     * @param message The message to process
     * @return The response message, or null if no response is needed
     */
    private Message processMessage(Message message) {
        logger.debug("Received message: {}", message);
        
        // Update peer's last seen timestamp
        if (message.getFrom() != null && !message.getFrom().equals(peerId)) {
            peerDiscovery.markPeerActive(message.getFrom());
        }
        
        // Find handler for the message type
        MessageHandler handler = messageHandlers.get(message.getType());
        if (handler != null) {
            return handler.handleMessage(message);
        } else {
            logger.warn("No handler for message type: {}", message.getType());
            return null;
        }
    }
    
    /**
     * Registers a message handler.
     * 
     * @param messageType The type of message to handle
     * @param handler The handler to register
     */
    public void registerMessageHandler(String messageType, MessageHandler handler) {
        messageHandlers.put(messageType, handler);
    }
    
    /**
     * Sends a message to a peer.
     * 
     * @param peer The peer to send the message to
     * @param message The message to send
     * @return The response message, or null if no response is received
     */
    public Message sendMessage(PeerInfo peer, Message message) {
        message.setFrom(peerId);
        
        try (
            Socket socket = new Socket(peer.getHost(), peer.getPort());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            // Send message
            out.writeObject(message);
            out.flush();
            
            // Check if we expect a response
            if (expectsResponse(message.getType())) {
                // Read response
                return (Message) in.readObject();
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Error sending message to peer {}: {}", peer, e.getMessage());
            peerDiscovery.markPeerInactive(peer.getId());
            return null;
        }
    }
    
    /**
     * Broadcasts a message to all active peers.
     * 
     * @param message The message to broadcast
     */
    public void broadcastMessage(Message message) {
        message.setFrom(peerId);
        List<PeerInfo> activePeers = peerDiscovery.getActivePeers();
        
        for (PeerInfo peer : activePeers) {
            connectionExecutor.submit(() -> {
                try (
                    Socket socket = new Socket(peer.getHost(), peer.getPort());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
                ) {
                    // Send message
                    out.writeObject(message);
                    out.flush();
                } catch (Exception e) {
                    logger.error("Error broadcasting message to peer {}: {}", peer, e.getMessage());
                    peerDiscovery.markPeerInactive(peer.getId());
                }
            });
        }
        
        logger.debug("Broadcasted message type {} to {} peers", message.getType(), activePeers.size());
    }
    
    /**
     * Checks if a message type expects a response.
     * 
     * @param messageType The message type to check
     * @return True if the message type expects a response
     */
    private boolean expectsResponse(String messageType) {
        return messageType.equals(MessageType.JOIN.getValue()) ||
               messageType.equals(MessageType.FILE_REQUEST.getValue()) ||
               messageType.equals(MessageType.LEDGER_SYNC.getValue()) ||
               messageType.equals(MessageType.PING.getValue());
    }
    
    /**
     * Creates a new message.
     * 
     * @param type The message type
     * @return The created message
     */
    public Message createMessage(MessageType type) {
        return new Message(type.getValue(), peerId);
    }
    
    /**
     * Gets the host the network manager is running on.
     * 
     * @return The host name or address
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Gets the port the network manager is running on.
     * 
     * @return The port number
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Interface for message handlers.
     */
    public interface MessageHandler {
        /**
         * Handles a message.
         * 
         * @param message The message to handle
         * @return The response message, or null if no response is needed
         */
        Message handleMessage(Message message);
    }
}
