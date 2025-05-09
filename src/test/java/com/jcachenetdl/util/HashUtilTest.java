package com.jcachenetdl.util;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class HashUtilTest {
    
    @Test
    public void testSha256WithString() {
        String input = "Hello, JCacheNetDL!";
        String hash = HashUtil.sha256(input);
        
        // The hash should be 64 characters (SHA-256 hex representation)
        assertEquals(64, hash.length());
        
        // The same input should always produce the same hash
        String hash2 = HashUtil.sha256(input);
        assertEquals(hash, hash2);
        
        // Different inputs should produce different hashes
        String differentInput = "Hello, Different!";
        String differentHash = HashUtil.sha256(differentInput);
        assertNotEquals(hash, differentHash);
    }
    
    @Test
    public void testSha256WithByteArray() {
        byte[] input = "Hello, JCacheNetDL!".getBytes();
        String hash = HashUtil.sha256(input);
        
        // The hash should be 64 characters (SHA-256 hex representation)
        assertEquals(64, hash.length());
        
        // The same input should always produce the same hash
        String hash2 = HashUtil.sha256(input);
        assertEquals(hash, hash2);
        
        // Different inputs should produce different hashes
        byte[] differentInput = "Hello, Different!".getBytes();
        String differentHash = HashUtil.sha256(differentInput);
        assertNotEquals(hash, differentHash);
    }
    
    @Test
    public void testSha256File() throws IOException {
        // Create a temporary file
        File tempFile = File.createTempFile("test", ".tmp");
        tempFile.deleteOnExit();
        
        // Write some data to the file
        byte[] data = new byte[1024];
        new Random().nextBytes(data);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(data);
        }
        
        // Hash the file
        String hash = HashUtil.sha256File(tempFile);
        
        // The hash should be 64 characters (SHA-256 hex representation)
        assertEquals(64, hash.length());
        
        // The same file should always produce the same hash
        String hash2 = HashUtil.sha256File(tempFile);
        assertEquals(hash, hash2);
        
        // Different files should produce different hashes
        File differentFile = File.createTempFile("test2", ".tmp");
        differentFile.deleteOnExit();
        byte[] differentData = new byte[1024];
        new Random().nextBytes(differentData);
        try (FileOutputStream fos = new FileOutputStream(differentFile)) {
            fos.write(differentData);
        }
        
        String differentHash = HashUtil.sha256File(differentFile);
        assertNotEquals(hash, differentHash);
    }
}
