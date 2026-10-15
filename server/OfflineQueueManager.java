package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class OfflineQueueManager {

    private static final int MAX_IN_MEMORY_LIMIT = 5;

    private static final String Directory_PATH = "offline_data";

    private static final ConcurrentHashMap<String, List<Object>> memory = new ConcurrentHashMap<>();

    static {
        File dir = new File(Directory_PATH);

        if (!dir.exists())
            dir.mkdirs();
    }

    public static synchronized void enqueueMessage(String username, Object message) {
        File userFile = new File(Directory_PATH, username + ".dat");

        if (userFile.exists()) {
            List<Object> currentMessages = readFromFile(userFile);
            currentMessages.add(message);
            writeToFile(userFile, currentMessages);
            return;
        }

        List<Object> queue = memory.computeIfAbsent(username, k -> new ArrayList<>());
        queue.add(message);

        if (queue.size() > MAX_IN_MEMORY_LIMIT) {
            writeToFile(userFile, queue);
            memory.remove(username);
        }
    }

    public static synchronized void flushAllToDisk() {
        for (String username : memory.keySet()) {
            List<Object> queue = memory.get(username);
            if (queue == null || queue.isEmpty())
                continue;

            File userFile = new File(Directory_PATH, username + ".dat");
            List<Object> combined = userFile.exists() ? readFromFile(userFile) : new ArrayList<>();
            combined.addAll(queue);
            writeToFile(userFile, combined);
        }
        memory.clear();
    }

    private static List<Object> readFromFile(File file) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (List<Object>) ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static void writeToFile(File file, List<Object> messages) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(messages);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized List<Object> loadAndClearOfflineMessages(String username) {
        ArrayList<Object> messages = new ArrayList<>();

        File userFile = new File(Directory_PATH, username + ".dat");

        if (userFile.exists()) {
            messages.addAll(readFromFile(userFile));
            userFile.delete();
        }

        List<Object> ram = memory.remove(username);

        if (ram != null)
            messages.addAll(ram);

        return messages;
    }
}