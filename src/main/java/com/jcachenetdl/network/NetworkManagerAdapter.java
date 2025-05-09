package com.jcachenetdl.network;

import com.jcachenetdl.common.PeerInfo;
import com.jcachenetdl.util.LogUtil;
import org.slf4j.Logger;

/**
 * Adapter class that provides a compatible interface between the original NetworkManager
 * and the enhanced NettyNetworkManager.
 * This allows existing code to work with either implementation.
 */
public class NetworkManagerAdapter {
    private final String peerId;
    private final String host;
    private final int port;
    private final PeerDiscovery peerDiscovery;
    private final Logger logger;
    
    // Either use the standard implementation or the Netty implementation
    private final NetworkManager standardNetworkManager;
    private final NettyNetworkManager nettyNetworkManager;
    private final boolean useNetty;
    
    /**
     * Creates a new NetworkManagerAdapter that chooses between standard and Netty implementation.
     * 
     * @param peerId The peer ID
     * @param host The host name or address
     * @param port The port to listen on
     * @param peerDiscovery The peer discovery service
     * @param useNetty Whether to use the Netty implementation
     */
    public NetworkManagerAdapter(String peerId, String host, int port, PeerDiscovery peerDiscovery, boolean useNetty) {
        this.peerId = peerId;
        this.host = host;
        this.port = port;
        this.peerDiscovery = peerDiscovery;
        this.useNetty = useNetty;
        this.logger = LogUtil.getLogger(NetworkManagerAdapter.class);
        
        // Initialize the selected implementation
        if (useNetty) {
            this.nettyNetworkManager = new NettyNetworkManager(peerId, host, port, peerDiscovery);
            this.standardNetworkManager = null;
            logger.info("Using Netty-based network implementation");
        } else {
            this.standardNetworkManager = new NetworkManager(peerId, host, port, peerDiscovery);
            this.nettyNetworkManager = null;
            logger.info("Using standard network implementation");
        }
    }
    
    /**
     * Starts the network manager.
     * 
     * @throws Exception If there's an error starting the network manager
     */
    public void start() throws Exception {
        if (useNetty) {
            nettyNetworkManager.start();
        } else {
            standardNetworkManager.start();
        }
    }
    
    /**
     * Stops the network manager.
     */
    public void stop() {
        if (useNetty) {
            nettyNetworkManager.stop();
        } else {
            standardNetworkManager.stop();
        }
    }
    
    /**
     * Registers a message handler.
     * 
     * @param messageType The type of message to handle
     * @param handler The handler to register
     */
    public void registerMessageHandler(String messageType, MessageHandler handler) {
        if (useNetty) {
            nettyNetworkManager.registerMessageHandler(messageType, 
                new NetworkManager.MessageHandler() {
                    @Override
                    public Message handleMessage(Message message) {
                        return handler.handleMessage(message);
                    }
                }
            );
        } else {
            standardNetworkManager.registerMessageHandler(messageType, 
                new NetworkManager.MessageHandler() {
                    @Override
                    public Message handleMessage(Message message) {
                        return handler.handleMessage(message);
                    }
                }
            );
        }
    }
    
    /**
     * Sends a message to a peer.
     * 
     * @param peer The peer to send the message to
     * @param message The message to send
     * @return The response message, or null if no response is received
     */
    public Message sendMessage(PeerInfo peer, Message message) {
        return useNetty ? nettyNetworkManager.sendMessage(peer, message) : 
                         standardNetworkManager.sendMessage(peer, message);
    }
    
    /**
     * Broadcasts a message to all active peers.
     * 
     * @param message The message to broadcast
     */
    public void broadcastMessage(Message message) {
        if (useNetty) {
            nettyNetworkManager.broadcastMessage(message);
        } else {
            standardNetworkManager.broadcastMessage(message);
        }
    }
    
    /**
     * Creates a new message.
     * 
     * @param type The message type
     * @return The created message
     */
    public Message createMessage(MessageType type) {
        return useNetty ? nettyNetworkManager.createMessage(type) : 
                        standardNetworkManager.createMessage(type);
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
     * Message handler interface compatible with both implementations.
     */
    public interface MessageHandler extends NetworkManager.MessageHandler {
        /**
         * Handles a message.
         * 
         * @param message The message to handle
         * @return The response message, or null if no response is needed
         */
        Message handleMessage(Message message);
    }
}
