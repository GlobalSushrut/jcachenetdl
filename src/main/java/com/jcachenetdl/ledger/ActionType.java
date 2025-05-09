package com.jcachenetdl.ledger;

/**
 * Enumeration of action types that can be recorded in the ledger.
 */
public enum ActionType {
    CACHE_PUT("CACHE_PUT"),
    CACHE_GET("CACHE_GET"),
    CACHE_HIT("CACHE_HIT");
    
    private final String value;
    
    ActionType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static ActionType fromValue(String value) {
        for (ActionType type : ActionType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown action type: " + value);
    }
}
