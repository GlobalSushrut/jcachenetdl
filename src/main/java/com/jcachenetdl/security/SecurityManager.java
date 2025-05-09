package com.jcachenetdl.security;

import com.jcachenetdl.common.LedgerEntry;
import com.jcachenetdl.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages security operations for the P2P CDN system.
 */
public class SecurityManager {
    private static final Logger logger = LoggerFactory.getLogger(SecurityManager.class);
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String RSA_ALGORITHM = "RSA";
    
    private final String nodeId;
    private final KeyPair keyPair;
    private final Map<String, PublicKey> peerPublicKeys;
    private final SecretKey aesKey;
    private final byte[] ivBytes;
    private final String authToken;

    /**
     * Creates a new SecurityManager instance.
     * 
     * @param nodeId The ID of the local node
     * @param authToken The authentication token for peer authentication
     */
    public SecurityManager(String nodeId, String authToken) {
        this.nodeId = nodeId;
        this.peerPublicKeys = new HashMap<>();
        this.authToken = authToken;
        
        // Generate RSA key pair
        this.keyPair = generateKeyPair();
        
        // Generate AES key for data encryption
        this.aesKey = generateAESKey();
        
        // Generate IV for AES
        this.ivBytes = generateIV();
        
        logger.info("Security manager initialized for node: {}", nodeId);
    }

    /**
     * Generates an RSA key pair.
     * 
     * @return The generated key pair
     */
    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error generating key pair", e);
            throw new RuntimeException("Failed to initialize security module", e);
        }
    }

    /**
     * Generates an AES key.
     * 
     * @return The generated AES key
     */
    private SecretKey generateAESKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error generating AES key", e);
            throw new RuntimeException("Failed to initialize security module", e);
        }
    }

    /**
     * Generates an initialization vector for AES encryption.
     * 
     * @return The generated IV bytes
     */
    private byte[] generateIV() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * Gets the public key of this node.
     * 
     * @return The public key as a Base64 encoded string
     */
    public String getPublicKeyEncoded() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * Adds a peer's public key.
     * 
     * @param peerId The ID of the peer
     * @param publicKeyEncoded The peer's public key as a Base64 encoded string
     * @return True if the key was added successfully
     */
    public boolean addPeerPublicKey(String peerId, String publicKeyEncoded) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyEncoded);
            PublicKey publicKey = KeyFactory.getInstance(RSA_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            peerPublicKeys.put(peerId, publicKey);
            logger.debug("Added public key for peer: {}", peerId);
            return true;
        } catch (Exception e) {
            logger.error("Error adding peer public key for {}", peerId, e);
            return false;
        }
    }

    /**
     * Signs data using the node's private key.
     * 
     * @param data The data to sign
     * @return The signature as a Base64 encoded string
     */
    public String sign(String data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            logger.error("Error signing data", e);
            return null;
        }
    }

    /**
     * Verifies a signature using a peer's public key.
     * 
     * @param peerId The ID of the peer
     * @param data The original data
     * @param signatureEncoded The signature as a Base64 encoded string
     * @return True if the signature is valid
     */
    public boolean verify(String peerId, String data, String signatureEncoded) {
        try {
            PublicKey publicKey = peerPublicKeys.get(peerId);
            if (publicKey == null) {
                logger.warn("No public key found for peer: {}", peerId);
                return false;
            }
            
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            
            byte[] signatureBytes = Base64.getDecoder().decode(signatureEncoded);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            logger.error("Error verifying signature from peer: {}", peerId, e);
            return false;
        }
    }

    /**
     * Signs a ledger entry.
     * 
     * @param entry The ledger entry to sign
     */
    public void signLedgerEntry(LedgerEntry entry) {
        String dataToSign = entry.getBlockId() + entry.getPreviousHash() + entry.getTimestamp();
        entry.setSignature(sign(dataToSign));
    }

    /**
     * Encrypts data using AES.
     * 
     * @param data The data to encrypt
     * @return The encrypted data as a Base64 encoded string
     */
    public String encryptData(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(ivBytes));
            byte[] encryptedBytes = cipher.doFinal(data);
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            logger.error("Error encrypting data", e);
            return null;
        }
    }

    /**
     * Decrypts data using AES.
     * 
     * @param encryptedDataEncoded The encrypted data as a Base64 encoded string
     * @return The decrypted data
     */
    public byte[] decryptData(String encryptedDataEncoded) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedDataEncoded);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(ivBytes));
            return cipher.doFinal(encryptedBytes);
        } catch (Exception e) {
            logger.error("Error decrypting data", e);
            return null;
        }
    }

    /**
     * Verifies the auth token.
     * 
     * @param token The token to verify
     * @return True if the token is valid
     */
    public boolean verifyAuthToken(String token) {
        return authToken.equals(token);
    }

    /**
     * Gets the authentication token.
     * 
     * @return The auth token
     */
    public String getAuthToken() {
        return authToken;
    }
}
