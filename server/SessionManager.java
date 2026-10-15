package server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SessionManager {

    private static final ConcurrentHashMap<String, ConnectionThread> activeClients = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Long> muteExpiration = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService ackScheduler = Executors.newScheduledThreadPool(10);
    private static final ConcurrentHashMap<Integer, ScheduledFuture<?>> pendingAcks = new ConcurrentHashMap<>();


    public static void registerPendingAck(int messageId, Runnable onTimeout) {
        ScheduledFuture<?> future = ackScheduler.schedule(onTimeout, 5, TimeUnit.SECONDS);
        pendingAcks.put(messageId, future);
    }

    public static void confirmAck(int messageId) {
        ScheduledFuture<?> future = pendingAcks.remove(messageId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public static void addClient(String username, ConnectionThread clientThread) {
        activeClients.put(username, clientThread);
    }


    public static void removeClient(String username) {
        if (username == null)
            return;

        activeClients.remove(username);
    }

    public static void removeClient(String username, ConnectionThread thread) {
        if (username == null || thread == null)
            return;

        activeClients.remove(username, thread);
    }

    public static boolean isOnline(String username) {
        return username != null && activeClients.containsKey(username);
    }


    public static void muteUser(String username, int durationInSeconds) {
        long expireTime = System.currentTimeMillis() + durationInSeconds * 1000;

        muteExpiration.put(username, expireTime);
    }

    public static boolean isMuted(String username) {
        if (username == null)
            return false;

        Long expireTime = muteExpiration.get(username);

        if (expireTime == null)
            return false;

        if (System.currentTimeMillis() >= expireTime) {
            muteExpiration.remove(username);
            return false;
        }

        return true;

    }

    public static ConnectionThread getClientThread(String username) {
        if (activeClients.containsKey(username))
            return activeClients.get(username);
        return null;
    }

    public static Set<String> getOnlineUsernames() {
        return activeClients.keySet();
    }

    public static ConcurrentHashMap<String, ConnectionThread> getActiveClients() {
        return activeClients;
    }
}