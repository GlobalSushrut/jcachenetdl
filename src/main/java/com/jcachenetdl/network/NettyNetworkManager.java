package com.jcachenetdl.network;

import com.jcachenetdl.common.PeerInfo;
import com.jcachenetdl.util.LogUtil;
import com.jcachenetdl.util.SerializationUtil;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * High-performance network manager implementation using Netty.
 */
public class NettyNetworkManager extends NetworkManager {
    private final String peerId;
    private final String host;
    private final int port;
    private final PeerDiscovery peerDiscovery;
    private final Map<String, MessageHandler> messageHandlers;
    private final Logger logger;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final AtomicBoolean running;
    
    // Define a simple nested class for Peer to avoid external dependencies
    public static class Peer {
        private String host;
        private int port;
        private boolean active = true;

        public Peer(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    // Add simple swarm management for P2P efficiency
    private Map<String, Peer> peerSwarm = new ConcurrentHashMap<>();
    
    /**
     * Creates a new NettyNetworkManager instance.
     * 
     * @param peerId The ID of the local node
     * @param host The host name or address
     * @param port The port to listen on
     * @param peerDiscovery The peer discovery service
     */
    public NettyNetworkManager(String peerId, String host, int port, PeerDiscovery peerDiscovery) {
        super(peerId, host, port, peerDiscovery);
        this.peerId = peerId;
        this.host = host;
        this.port = port;
        this.peerDiscovery = peerDiscovery;
        this.messageHandlers = new ConcurrentHashMap<>();
        this.logger = LogUtil.getLogger(NettyNetworkManager.class);
        this.running = new AtomicBoolean(false);
    }
    
    /**
     * Starts the network manager.
     * 
     * @throws InterruptedException If the server fails to start
     */
    @Override
    public void start() throws IOException {
        if (running.get()) {
            return;
        }
        
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                // Timeout handlers
                                new ReadTimeoutHandler(30),
                                new WriteTimeoutHandler(30),
                                // Message decoder
                                new MessageDecoder(),
                                // Message encoder
                                new MessageEncoder(),
                                // Business logic handler
                                new ServerHandler()
                        );
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        
        try {
            ChannelFuture future = bootstrap.bind(host, port).sync();
            serverChannel = future.channel();
            running.set(true);
            
            logger.info("Netty network manager started on {}:{}", host, port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while starting Netty server", e);
        }
    }
    
    /**
     * Stops the network manager.
     */
    @Override
    public void stop() {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        
        logger.info("Netty network manager stopped");
    }
    
    /**
     * Registers a message handler.
     * 
     * @param messageType The type of message to handle
     * @param handler The handler to register
     */
    @Override
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
    @Override
    public Message sendMessage(PeerInfo peer, Message message) {
        if (!running.get()) {
            logger.warn("Attempted to send message while network manager is stopped");
            return null;
        }
        
        message.setFrom(peerId);
        
        try {
            final CompletableFuture<Message> responseFuture = new CompletableFuture<>();
            
            EventLoopGroup group = new NioEventLoopGroup();
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(
                                        // Timeout handlers
                                        new ReadTimeoutHandler(30),
                                        new WriteTimeoutHandler(30),
                                        // Message decoder
                                        new MessageDecoder(),
                                        // Message encoder
                                        new MessageEncoder(),
                                        // Client handler
                                        new ClientHandler(message, responseFuture)
                                );
                            }
                        });
                
                // Start the client
                ChannelFuture future = bootstrap.connect(peer.getHost(), peer.getPort()).sync();
                
                // Wait for the response or timeout
                Message response = responseFuture.get(30, TimeUnit.SECONDS);
                
                // Close the connection
                future.channel().close().sync();
                
                // Update peer's last seen timestamp
                peerDiscovery.markPeerActive(peer.getId());
                
                return response;
            } finally {
                group.shutdownGracefully();
            }
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
    @Override
    public void broadcastMessage(Message message) {
        if (!running.get()) {
            logger.warn("Attempted to broadcast message while network manager is stopped");
            return;
        }
        
        message.setFrom(peerId);
        List<PeerInfo> activePeers = peerDiscovery.getActivePeers();
        
        for (PeerInfo peer : activePeers) {
            workerGroup.execute(() -> {
                try {
                    EventLoopGroup group = new NioEventLoopGroup();
                    try {
                        Bootstrap bootstrap = new Bootstrap();
                        bootstrap.group(group)
                                .channel(NioSocketChannel.class)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                                .handler(new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(SocketChannel ch) {
                                        ch.pipeline().addLast(
                                                // Message encoder
                                                new MessageEncoder(),
                                                // Client handler for broadcast (no response expected)
                                                new ChannelInboundHandlerAdapter() {
                                                    @Override
                                                    public void channelActive(ChannelHandlerContext ctx) {
                                                        ctx.writeAndFlush(message).addListener(ChannelFutureListener.CLOSE);
                                                    }
                                                }
                                        );
                                    }
                                });
                        
                        // Connect and send
                        bootstrap.connect(peer.getHost(), peer.getPort()).sync()
                                .channel().closeFuture().sync();
                    } finally {
                        group.shutdownGracefully();
                    }
                } catch (Exception e) {
                    logger.error("Error broadcasting message to peer {}: {}", peer, e.getMessage());
                    peerDiscovery.markPeerInactive(peer.getId());
                }
            });
        }
        
        logger.debug("Broadcasted message type {} to {} peers", message.getType(), activePeers.size());
    }
    
    /**
     * Creates a new message.
     * 
     * @param type The message type
     * @return The created message
     */
    @Override
    public Message createMessage(MessageType type) {
        return new Message(type.getValue(), peerId);
    }
    
    /**
     * Gets the host the network manager is running on.
     * 
     * @return The host name or address
     */
    @Override
    public String getHost() {
        return host;
    }
    
    /**
     * Gets the port the network manager is running on.
     * 
     * @return The port number
     */
    @Override
    public int getPort() {
        return port;
    }
    
    /**
     * Checks if the network manager is running.
     * 
     * @return True if the network manager is running
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Adds a peer to the swarm.
     * 
     * @param peerId The ID of the peer to add
     * @param host The host of the peer
     * @param port The port of the peer
     */
    public void addPeerToSwarm(String peerId, String host, int port) {
        Peer peer = new Peer(host, port);
        peerSwarm.put(peerId, peer);
        // TODO: Implement periodic cleanup of inactive peers
    }

    /**
     * Gets the list of active peers in the swarm.
     * 
     * @return The list of active peers
     */
    public List<Peer> getActivePeers() {
        return new ArrayList<>(peerSwarm.values().stream().filter(Peer::isActive).collect(Collectors.toList()));
    }
    
    /**
     * Handler for incoming server connections.
     */
    private class ServerHandler extends SimpleChannelInboundHandler<Message> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Message message) {
            // Update peer's last seen timestamp
            if (message.getFrom() != null && !message.getFrom().equals(peerId)) {
                peerDiscovery.markPeerActive(message.getFrom());
            }
            
            // Process the message
            MessageHandler handler = messageHandlers.get(message.getType());
            if (handler != null) {
                try {
                    Message response = handler.handleMessage(message);
                    if (response != null) {
                        // Send response
                        ctx.writeAndFlush(response);
                    }
                } catch (Exception e) {
                    logger.error("Error processing message: {}", message, e);
                }
            } else {
                logger.warn("No handler for message type: {}", message.getType());
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("Server exception", cause);
            ctx.close();
        }
    }
    
    /**
     * Handler for outgoing client connections.
     */
    private class ClientHandler extends SimpleChannelInboundHandler<Message> {
        private final Message requestMessage;
        private final CompletableFuture<Message> responseFuture;
        
        public ClientHandler(Message requestMessage, CompletableFuture<Message> responseFuture) {
            this.requestMessage = requestMessage;
            this.responseFuture = responseFuture;
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(requestMessage);
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Message response) {
            responseFuture.complete(response);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            responseFuture.completeExceptionally(cause);
            ctx.close();
        }
    }
    
    /**
     * Encoder to convert Message objects to bytes for network transmission.
     */
    private class MessageEncoder extends MessageToByteEncoder<Message> {
        @Override
        protected void encode(ChannelHandlerContext ctx, Message message, ByteBuf out) throws Exception {
            byte[] data = SerializationUtil.serialize(message);
            out.writeInt(data.length);
            out.writeBytes(data);
        }
    }
    
    /**
     * Decoder to convert bytes from network to Message objects.
     */
    private class MessageDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            // Wait until the length prefix is available
            if (in.readableBytes() < 4) {
                return;
            }
            
            // Mark the current position
            in.markReaderIndex();
            
            // Read the length field
            int length = in.readInt();
            
            // Make sure if there's enough bytes in the buffer
            if (in.readableBytes() < length) {
                in.resetReaderIndex();
                return;
            }
            
            // Read the data
            byte[] data = new byte[length];
            in.readBytes(data);
            
            // Deserialize the message
            Message message = SerializationUtil.deserialize(data);
            
            // Add the message to the output list for next handler
            out.add(message);
        }
    }
}
