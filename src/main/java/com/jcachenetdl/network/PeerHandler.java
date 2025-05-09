package com.jcachenetdl.network;

import com.jcachenetdl.common.CacheItem;
import com.jcachenetdl.common.LedgerEntry;
import com.jcachenetdl.common.PeerInfo;
import com.jcachenetdl.ledger.DistributedLedger;
import com.jcachenetdl.node.CacheEngine;
import com.jcachenetdl.util.LogUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles interactions with peers in the network.
 */
public class PeerHandler {
    private final String peerId;
    private final PeerDiscovery peerDiscovery;
    private final NetworkManager networkManager;
    private final DistributedLedger ledger;
    private final CacheEngine cacheEngine;
    private final ExecutorService executor;
    private final Logger logger;
    
    public PeerHandler(String peerId, PeerDiscovery peerDiscovery, NetworkManager networkManager, 
                      DistributedLedger ledger, CacheEngine cacheEngine) {
        this.peerId = peerId;
        this.peerDiscovery = peerDiscovery;
        this.networkManager = networkManager;
        this.ledger = ledger;
        this.cacheEngine = cacheEngine;
        this.executor = Executors.newFixedThreadPool(10);
        this.logger = LogUtil.getLogger(PeerHandler.class);
        
        // Register message handlers
        registerMessageHandlers();
    }
    
    /**
     * Registers handlers for different message types.
     */
    private void registerMessageHandlers() {
        // JOIN handler
        networkManager.registerMessageHandler(MessageType.JOIN.getValue(), message -> {
            String remotePeerId = message.getFrom();
            String host = (String) message.getPayloadValue("host");
            int port = ((Number) message.getPayloadValue("port")).intValue();
            
            PeerInfo peer = new PeerInfo(remotePeerId, host, port);
            boolean isNewPeer = peerDiscovery.addPeer(peer);
            
            // Create response
            Message response = networkManager.createMessage(MessageType.JOIN_RESPONSE);
            response.addPayload("success", true);
            
            // If this is a new peer, share our peer list
            if (isNewPeer) {
                sharePeerList(peer);
                syncLedgerWithPeer(peer);
            }
            
            return response;
        });
        
        // PEER_LIST handler
        networkManager.registerMessageHandler(MessageType.PEER_LIST.getValue(), message -> {
            List<Map<String, Object>> peerMaps = (List<Map<String, Object>>) message.getPayloadValue("peers");
            
            if (peerMaps != null) {
                for (Map<String, Object> peerMap : peerMaps) {
                    String id = (String) peerMap.get("id");
                    String host = (String) peerMap.get("host");
                    int port = ((Number) peerMap.get("port")).intValue();
                    
                    if (!id.equals(peerId)) {  // Don't add ourselves
                        PeerInfo peer = new PeerInfo(id, host, port);
                        peerDiscovery.addPeer(peer);
                    }
                }
                logger.debug("Received peer list with {} peers", peerMaps.size());
            }
            
            return null;  // No response needed
        });
        
        // FILE_REQUEST handler
        networkManager.registerMessageHandler(MessageType.FILE_REQUEST.getValue(), message -> {
            String fileHash = (String) message.getPayloadValue("fileHash");
            int chunkId = ((Number) message.getPayloadValue("chunkId")).intValue();
            
            Message response = networkManager.createMessage(MessageType.FILE_RESPONSE);
            response.addPayload("fileHash", fileHash);
            response.addPayload("chunkId", chunkId);
            
            CacheItem item = cacheEngine.getCacheItem(fileHash, chunkId);
            if (item != null) {
                response.addPayload("success", true);
                response.addPayload("data", item.getData());
                response.addPayload("totalChunks", item.getTotalChunks());
                logger.debug("Serving file chunk: {}, {}", fileHash, chunkId);
            } else {
                response.addPayload("success", false);
                response.addPayload("error", "Chunk not found");
                logger.debug("Requested chunk not found: {}, {}", fileHash, chunkId);
            }
            
            return response;
        });
        
        // LEDGER_SYNC handler
        networkManager.registerMessageHandler(MessageType.LEDGER_SYNC.getValue(), message -> {
            String lastBlockHash = (String) message.getPayloadValue("lastBlockHash");
            
            List<LedgerEntry> blocks = ledger.getBlocksSince(lastBlockHash);
            
            Message response = networkManager.createMessage(MessageType.LEDGER_SYNC_RESPONSE);
            response.addPayload("blocks", blocks);
            response.addPayload("blocksCount", blocks.size());
            
            logger.debug("Sending {} ledger blocks in sync response", blocks.size());
            LogUtil.logLedgerSync(blocks.size());
            
            return response;
        });
        
        // LEDGER_ENTRY handler
        networkManager.registerMessageHandler(MessageType.LEDGER_ENTRY.getValue(), message -> {
            LedgerEntry block = (LedgerEntry) message.getPayloadValue("block");
            
            if (block != null) {
                boolean added = ledger.addBlock(block);
                logger.debug("Received ledger block: {}, added: {}", block.getBlockId(), added);
            }
            
            return null;  // No response needed
        });
        
        // PING handler
        networkManager.registerMessageHandler(MessageType.PING.getValue(), message -> {
            Message response = networkManager.createMessage(MessageType.PONG);
            return response;
        });
    }
    
    /**
     * Shares our list of known peers with a peer.
     * 
     * @param peer The peer to share with
     */
    private void sharePeerList(PeerInfo peer) {
        executor.submit(() -> {
            List<PeerInfo> activePeers = peerDiscovery.getActivePeers();
            List<Map<String, Object>> peerMaps = new ArrayList<>(activePeers.size());
            
            for (PeerInfo p : activePeers) {
                Map<String, Object> peerMap = new HashMap<>();
                peerMap.put("id", p.getId());
                peerMap.put("host", p.getHost());
                peerMap.put("port", p.getPort());
                peerMaps.add(peerMap);
            }
            
            Message message = networkManager.createMessage(MessageType.PEER_LIST);
            message.addPayload("peers", peerMaps);
            
            networkManager.sendMessage(peer, message);
            logger.debug("Shared peer list with {} peers to {}", peerMaps.size(), peer);
        });
    }
    
    /**
     * Syncs the ledger with a peer.
     * 
     * @param peer The peer to sync with
     */
    private void syncLedgerWithPeer(PeerInfo peer) {
        executor.submit(() -> {
            // Get our last block hash
            LedgerEntry lastBlock = ledger.getLastBlock();
            String lastBlockHash = lastBlock != null ? lastBlock.getBlockHash() : "";
            
            Message message = networkManager.createMessage(MessageType.LEDGER_SYNC);
            message.addPayload("lastBlockHash", lastBlockHash);
            
            Message response = networkManager.sendMessage(peer, message);
            if (response != null && response.getType().equals(MessageType.LEDGER_SYNC_RESPONSE.getValue())) {
                List<LedgerEntry> blocks = (List<LedgerEntry>) response.getPayloadValue("blocks");
                int blocksCount = ((Number) response.getPayloadValue("blocksCount")).intValue();
                
                if (blocks != null && !blocks.isEmpty()) {
                    int addedCount = 0;
                    for (LedgerEntry block : blocks) {
                        if (ledger.addBlock(block)) {
                            addedCount++;
                        }
                    }
                    
                    logger.info("Synced {} out of {} ledger blocks from {}", addedCount, blocksCount, peer);
                    LogUtil.logLedgerSync(addedCount);
                }
            }
        });
    }
    
    /**
     * Propagates a ledger entry to all peers.
     * 
     * @param block The ledger entry to propagate
     */
    public void propagateLedgerEntry(LedgerEntry block) {
        executor.submit(() -> {
            Message message = networkManager.createMessage(MessageType.LEDGER_ENTRY);
            message.addPayload("block", block);
            
            networkManager.broadcastMessage(message);
            logger.debug("Propagated ledger block: {}", block.getBlockId());
        });
    }
    
    /**
     * Joins the network by connecting to a known peer.
     * 
     * @param host The host of the known peer
     * @param port The port of the known peer
     * @return True if the join was successful
     */
    public boolean joinNetwork(String host, int port) {
        try {
            // Create a temporary peer info
            PeerInfo bootstrapPeer = new PeerInfo("bootstrap", host, port);
            
            // Create JOIN message
            Message joinMessage = networkManager.createMessage(MessageType.JOIN);
            joinMessage.addPayload("host", networkManager.getHost());
            joinMessage.addPayload("port", networkManager.getPort());
            
            // Send JOIN message
            Message response = networkManager.sendMessage(bootstrapPeer, joinMessage);
            
            if (response != null && response.getType().equals(MessageType.JOIN_RESPONSE.getValue())) {
                boolean success = (Boolean) response.getPayloadValue("success");
                if (success) {
                    // The bootstrap peer will have our info from the joinMessage
                    // Update the bootstrap peer's ID from the response
                    String bootstrapPeerId = response.getFrom();
                    bootstrapPeer.setId(bootstrapPeerId);
                    
                    // Add the bootstrap peer to our list
                    peerDiscovery.addPeer(bootstrapPeer);
                    
                    // The bootstrap peer should also send us a peer list and sync the ledger
                    logger.info("Successfully joined the network through peer: {}", bootstrapPeer);
                    return true;
                }
            }
            
            logger.error("Failed to join the network through peer: {}", bootstrapPeer);
            return false;
        } catch (Exception e) {
            logger.error("Error joining the network: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Requests a file chunk from a peer.
     * 
     * @param peer The peer to request from
     * @param fileHash The hash of the file
     * @param chunkId The chunk ID
     * @return The cache item, or null if the request failed
     */
    public CacheItem requestFileChunk(PeerInfo peer, String fileHash, int chunkId) {
        Message request = networkManager.createMessage(MessageType.FILE_REQUEST);
        request.addPayload("fileHash", fileHash);
        request.addPayload("chunkId", chunkId);
        
        Message response = networkManager.sendMessage(peer, request);
        
        if (response != null && response.getType().equals(MessageType.FILE_RESPONSE.getValue())) {
            boolean success = (Boolean) response.getPayloadValue("success");
            
            if (success) {
                byte[] data = (byte[]) response.getPayloadValue("data");
                int totalChunks = ((Number) response.getPayloadValue("totalChunks")).intValue();
                
                return new CacheItem(fileHash, chunkId, data, response.getFrom(), totalChunks);
            } else {
                String error = (String) response.getPayloadValue("error");
                logger.error("File chunk request failed: {}", error);
            }
        } else {
            logger.error("No valid response received for file chunk request");
            peerDiscovery.markPeerInactive(peer.getId());
        }
        
        return null;
    }
    
    /**
     * Checks if a peer is alive.
     * 
     * @param peer The peer to check
     * @return True if the peer is alive
     */
    public boolean isPeerAlive(PeerInfo peer) {
        Message ping = networkManager.createMessage(MessageType.PING);
        Message response = networkManager.sendMessage(peer, ping);
        
        return response != null && response.getType().equals(MessageType.PONG.getValue());
    }
    
    /**
     * Stops the peer handler.
     */
    public void shutdown() {
        executor.shutdownNow();
        logger.info("Peer handler shutdown");
    }
}
