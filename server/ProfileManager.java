package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class ProfileManager {

    private static final String PROFILES_DIR = "profiles";

    static {
        File dir = new File(PROFILES_DIR);
        if (!dir.exists())
            dir.mkdirs();

    }

    public static class UserProfile implements Serializable {
        private static final long serialVersionUID = 1L;

        private String username;
        private String password;
        private int messagesSent;
        private int messagesReceived;

        public UserProfile(String username, String password) {
            this.username = username;
            this.password = password;
            this.messagesSent = 0;
            this.messagesReceived = 0;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getMessagesSent() {
            return messagesSent;
        }

        public int getMessagesReceived() {
            return messagesReceived;
        }

        public void incrementSent() {
            this.messagesSent++;
        }

        public void incrementReceived() {
            this.messagesReceived++;
        }
    }

    public static synchronized void saveProfile(UserProfile profile) {
        File file = new File(PROFILES_DIR, profile.getUsername() + ".dat");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(profile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized boolean register(String username, String password) {
        if (userExists(username))
            return false;

        UserProfile newProfile = new UserProfile(username, password);
        saveProfile(newProfile);
        return true;
    }

    public static boolean userExists(String username) {
        File file = new File(PROFILES_DIR, username + ".dat");
        return file.exists();
    }

    public static synchronized void incrementSentMessages(String username) {
        UserProfile profile = loadProfile(username);
        if (profile != null) {
            profile.incrementSent();
            saveProfile(profile);
        }
    }

    public static synchronized void incrementReceivedMessages(String username) {
        UserProfile profile = loadProfile(username);
        if (profile != null) {
            profile.incrementReceived();
            saveProfile(profile);
        }
    }

    public static synchronized int changePassword(String username, String oldPassword, String newPassword) {
        UserProfile profile = loadProfile(username);
        if (profile == null) {
            return 1;
        }

        if (!profile.getPassword().equals(oldPassword)) {
            return 1;
        }

        if (newPassword == null || newPassword.trim().isEmpty() || newPassword.equals(oldPassword)) {
            return 2;
        }

        profile.setPassword(newPassword);
        saveProfile(profile);
        return 0;
    }

    public static synchronized UserProfile loadProfile(String username) {
        File file = new File(PROFILES_DIR, username + ".dat");
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));) {
            return (UserProfile) ois.readObject();
        }

        catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static synchronized boolean authenticate(String username, String password) {
        UserProfile profile = loadProfile(username);
        if (profile == null)
            return false;

        return profile.getPassword().equals(password);
    }
}
