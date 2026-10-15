package server;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import shared.Frame;

public class RelayServer {

    public static void main(String[] args) {
        int port = 8080;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port.");
                return;
            }
        }

        try {
            ServerSocket serverSocket = new ServerSocket(port);

            ExecutorService threadPool = Executors.newFixedThreadPool(50);

            Thread adminThread = new Thread(() -> runAdminConsole(serverSocket, threadPool));
            adminThread.setDaemon(true);
            adminThread.start();

            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();

                    ConnectionThread connectionThread = new ConnectionThread(socket);

                    threadPool.execute(connectionThread);
                } catch (Exception e) {
                    if (serverSocket.isClosed())
                        break;
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runAdminConsole(ServerSocket serverSocket, ExecutorService threadPool) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            if (!scanner.hasNextLine())
                return;

            String line = scanner.nextLine().trim();
            if (line.isEmpty())
                continue;

            String[] parts = line.split(" ");
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "/list":
                        System.out.println("Online users: " + SessionManager.getOnlineUsernames());
                        break;

                    case "/kick":
                        if (parts.length >= 2) {
                            kickUser(parts[1]);
                        } else {
                            System.out.println("Usage: /kick <username>");
                        }
                        break;

                    case "/mute":
                        if (parts.length >= 3) {
                            int duration = Integer.parseInt(parts[2]);
                            SessionManager.muteUser(parts[1], duration);
                            System.out.println("User " + parts[1] + " has been muted for " + duration + " seconds.");
                        } else {
                            System.out.println("Usage: /mute <username> <duration_in_seconds>");
                        }
                        break;

                    case "/shutdown":
                        shutdownServer(serverSocket, threadPool);
                        return;

                    default:
                        System.out.println("Unknown admin command.");
                        break;
                }
            } catch (Exception e) {
                System.out.println("Failed to execute admin command: " + e.getMessage());
            }
        }
    }

    private static void kickUser(String username) {
        ConnectionThread target = SessionManager.getClientThread(username);

        if (target == null) {
            System.out.println("User " + username + " is not online.");
            return;
        }

        target.sendRootCommand(Frame.ROOT_CMD_KICK);
        target.closeConnection();
        SessionManager.removeClient(username, target);

        String announcement = "[LOUNGE] \"ANNOUNCEMENT: " + username + " has been kicked by the administrator.\"";
        byte[] payload = announcement.getBytes(StandardCharsets.UTF_8);

        for (ConnectionThread client : SessionManager.getActiveClients().values()) {
            try {
                Frame.sendFrame(client.getOutputStream(), Frame.TYPE_LOUNGE_MSG, payload);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void shutdownServer(ServerSocket serverSocket, ExecutorService threadPool) {
        String notice = ">> [SYSTEM NOTICE]: Server is shutting down. Disconnecting...";
        byte[] payload = notice.getBytes(StandardCharsets.UTF_8);

        for (ConnectionThread client : SessionManager.getActiveClients().values()) {
            try {
                Frame.sendFrame(client.getOutputStream(), Frame.TYPE_SYSTEM_NOTICE, payload);
            } catch (Exception e) {
                e.printStackTrace();
            }
            client.closeConnection();
        }

        threadPool.shutdown();

        OfflineQueueManager.flushAllToDisk();

        try {
            serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Server has been shut down.");
        System.exit(0);
    }
}
