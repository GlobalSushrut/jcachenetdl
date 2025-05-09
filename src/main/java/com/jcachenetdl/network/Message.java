package com.jcachenetdl.network;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a message exchanged between peers in the network.
 */
public class Message implements Serializable {
    private String type;
    private String from;
    private Map<String, Object> payload;
    
    public Message(String type, String from) {
        this.type = type;
        this.from = from;
        this.payload = new HashMap<>();
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String from) {
        this.from = from;
    }
    
    public Map<String, Object> getPayload() {
        return payload;
    }
    
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
    
    public void addPayload(String key, Object value) {
        this.payload.put(key, value);
    }
    
    public Object getPayloadValue(String key) {
        return this.payload.get(key);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(type, message.type) &&
               Objects.equals(from, message.from) &&
               Objects.equals(payload, message.payload);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, from, payload);
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "type='" + type + '\'' +
                ", from='" + from + '\'' +
                ", payload=" + payload +
                '}';
    }
}
