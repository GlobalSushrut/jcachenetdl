package com.jcachenetdl.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;

/**
 * Utility class for serialization and deserialization operations.
 */
public class SerializationUtil {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Converts an object to JSON string.
     * 
     * @param obj The object to convert
     * @return JSON string representation
     */
    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }
    
    /**
     * Converts a JSON string to an object of the specified type.
     * 
     * @param <T> The type to convert to
     * @param json The JSON string
     * @param classOfT The class of the type
     * @return The object of type T
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }
    
    /**
     * Serializes an object to a file.
     * 
     * @param obj The object to serialize
     * @param file The file to write to
     * @throws IOException If there's an error writing to the file
     */
    public static void serializeToFile(Object obj, File file) throws IOException {
        String json = toJson(obj);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
        }
    }
    
    /**
     * Deserializes an object from a file.
     * 
     * @param <T> The type to deserialize to
     * @param file The file to read from
     * @param classOfT The class of the type
     * @return The deserialized object
     * @throws IOException If there's an error reading the file
     */
    public static <T> T deserializeFromFile(File file, Class<T> classOfT) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, classOfT);
        }
    }
    
    /**
     * Serializes an object to bytes.
     *
     * @param obj The object to serialize
     * @return The serialized bytes
     * @throws IOException If there's an error during serialization
     */
    public static byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            return baos.toByteArray();
        }
    }
    
    /**
     * Deserializes an object from bytes.
     *
     * @param <T> The type to deserialize to
     * @param bytes The bytes to deserialize
     * @return The deserialized object
     * @throws IOException If there's an error during deserialization
     * @throws ClassNotFoundException If the class of the serialized object cannot be found
     */
    public static <T> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }
}
