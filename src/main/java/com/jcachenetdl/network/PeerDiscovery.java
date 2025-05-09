package com.jcachenetdl.network;

import com.jcachenetdl.common.PeerInfo;
import com.jcachenetdl.util.LogUtil;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages peer discovery and maintains a list of known peers.
 */
public class PeerDiscovery {
    private final String peerId;
    private final Map<String, PeerInfo> peers;
    private final ScheduledExecutorService scheduler;
    private final Logger logger;
    
    // Time intervals in milliseconds
    private static final long PEER_CLEANUP_INTERVAL = 60_000; // 60 seconds
    private static final long PEER_TIMEOUT = 300_000; // 5 minutes

    public PeerDiscovery(String peerId) {
        this.peerId = peerId;
        this.peers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.logger = LogUtil.getLogger(PeerDiscovery.class);
        
        // Schedule regular cleanup of inactive peers
        scheduler.scheduleAtFixedRate(this::cleanupInactivePeers, 
                PEER_CLEANUP_INTERVAL, PEER_CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Adds a peer to the discovery list.
     * 
     * @param peer The peer to add
     * @return True if the peer was added (new peer), false if updated (existing peer)
     */
    public boolean addPeer(PeerInfo peer) {
        if (peer.getId().equals(peerId)) {
            logger.debug("Ignoring self as peer: {}", peer.getId());
            return false;
        }
        
        boolean isNewPeer = !peers.containsKey(peer.getId());
        peers.put(peer.getId(), peer);
        
        if (isNewPeer) {
            logger.info("Added new peer: {}", peer);
            LogUtil.logPeerJoin(peer.getId());
        } else {
            logger.debug("Updated existing peer: {}", peer);
            peers.get(peer.getId()).updateLastSeen();
        }
        
        return isNewPeer;
    }
    
    /**
     * Gets a peer by its ID.
     * 
     * @param peerId The peer ID
     * @return The peer info, or null if not found
     */
    public PeerInfo getPeer(String peerId) {
        return peers.get(peerId);
    }
    
    /**
     * Removes a peer from the discovery list.
     * 
     * @param peerId The peer ID to remove
     * @return True if the peer was removed, false if not found
     */
    public boolean removePeer(String peerId) {
        PeerInfo removedPeer = peers.remove(peerId);
        if (removedPeer != null) {
            logger.info("Removed peer: {}", removedPeer);
            return true;
        }
        return false;
    }
    
    /**
     * Gets all known active peers.
     * 
     * @return The list of active peer info
     */
    public List<PeerInfo> getActivePeers() {
        return peers.values().stream()
                .filter(PeerInfo::isActive)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all known peers.
     * 
     * @return The list of all peer info
     */
    public List<PeerInfo> getAllPeers() {
        return new ArrayList<>(peers.values());
    }
    
    /**
     * Marks a peer as active and updates its last seen timestamp.
     * 
     * @param peerId The peer ID
     */
    public void markPeerActive(String peerId) {
        PeerInfo peer = peers.get(peerId);
        if (peer != null) {
            peer.setActive(true);
            peer.updateLastSeen();
        }
    }
    
    /**
     * Marks a peer as inactive.
     * 
     * @param peerId The peer ID
     */
    public void markPeerInactive(String peerId) {
        PeerInfo peer = peers.get(peerId);
        if (peer != null) {
            peer.setActive(false);
            logger.info("Marked peer as inactive: {}", peer);
        }
    }
    
    /**
     * Removes peers that haven't been seen for a while.
     */
    private void cleanupInactivePeers() {
        long now = System.currentTimeMillis();
        List<String> peersToRemove = new ArrayList<>();
        
        for (PeerInfo peer : peers.values()) {
            if (now - peer.getLastSeen() > PEER_TIMEOUT) {
                peersToRemove.add(peer.getId());
            }
        }
        
        for (String peerId : peersToRemove) {
            removePeer(peerId);
        }
        
        if (!peersToRemove.isEmpty()) {
            logger.info("Removed {} inactive peers", peersToRemove.size());
        }
    }
    
    /**
     * Gets the number of active peers.
     * 
     * @return The count of active peers
     */
    public int getActivePeerCount() {
        return (int) peers.values().stream()
                .filter(PeerInfo::isActive)
                .count();
    }
    
    /**
     * Stops the peer discovery service.
     */
    public void shutdown() {
        scheduler.shutdownNow();
        logger.info("Peer discovery service shutdown");
    }
}
