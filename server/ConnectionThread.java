package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import shared.Frame;

public class ConnectionThread implements Runnable {
    private final Socket socket;
    private String username;
    private boolean loggedIn = false;
    private DataOutputStream out;

    public ConnectionThread(Socket socket) {
        this.socket = socket;
    }

    public DataOutputStream getOutputStream() {
        return out;
    }

    public String getUsername() {
        return username;
    }

    public void sendRootCommand(byte cmdByte) {
        try {
            Frame.sendFrame(out, Frame.TYPE_ROOT_CMD, new byte[] { cmdByte });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream outStream = new DataOutputStream(socket.getOutputStream())) {

            this.out = outStream;

            checkLoggedIn(in, out);

            if (loggedIn) {
                SessionManager.addClient(username, this);

                deliverOfflineMessages();

                while (!socket.isClosed()) {
                    byte[] frame = Frame.readNextFrame(socket, in);

                    if (frame == null) {
                        break;
                    }

                    byte type = frame[0];

                    switch (type) {
                        case Frame.TYPE_LOUNGE_MSG:
                            handleLoungeMessage(frame);
                            break;
                        case Frame.TYPE_DIRECT_MSG:
                            handleDirectMessage(frame);
                            break;
                        case Frame.TYPE_ACK:
                            handleAck(frame);
                            break;
                        default:
                            break;
                    }
                }
            }

        } catch (SocketTimeoutException e) {
            System.err.println("Request Timed out.");
        } catch (Exception e) {
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        if (username != null) {
            SessionManager.removeClient(username, this);
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }


    private void checkLoggedIn(DataInputStream in, DataOutputStream out) throws Exception {
        int failedAttempts = 0;

        while (!loggedIn) {
            byte[] frame = Frame.readNextFrame(socket, in);

            if (frame == null) {
                return;
            }

            byte type = frame[0];

            String payloadStr = new String(frame, 1, frame.length - 1, StandardCharsets.UTF_8);
            String[] parts = payloadStr.split(" ", 2);

            if (parts.length < 2) {
                continue;
            }

            String inputUsername = parts[0];
            String inputPassword = parts[1];

            if (type == Frame.TYPE_REGISTER) {
                if (ProfileManager.userExists(inputUsername)) {
                    String notice = ">> [SYSTEM NOTICE]: Username is already taken. Please choose another username.";
                    Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));

                } else {
                    ProfileManager.register(inputUsername, inputPassword);
                    this.username = inputUsername;
                    this.loggedIn = true;
                    String notice = ">> [SYSTEM NOTICE]: Registration successful! Welcome to \"Na-Kheyr\" Messenger.";
                    Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));
                }
            } else if (type == Frame.TYPE_LOGIN) {
                if (!ProfileManager.userExists(inputUsername)) {
                    String notice = ">> [SYSTEM NOTICE]: Account not found. Please register first or check your spelling.";
                    Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));

                } else {
                    ProfileManager.UserProfile profile = ProfileManager.loadProfile(inputUsername);
                    if (profile != null && profile.getPassword().equals(inputPassword)) {
                        if (SessionManager.isOnline(inputUsername)) {
                            String notice = ">> [SYSTEM NOTICE]: This account is already logged in.";
                            Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));

                            continue;
                        }
                        this.username = inputUsername;
                        this.loggedIn = true;
                        String notice = ">> [SYSTEM NOTICE]: Login successful! Welcome back, " + this.username + ".";
                        Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));
                    } else {
                        failedAttempts++;
                        int remaining = 3 - failedAttempts;
                        if (remaining > 0) {
                            String notice = ">> [SYSTEM NOTICE]: Incorrect password. Attempts remaining: " + remaining;
                            Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));
                        } else {
                            String notice = ">> [SYSTEM NOTICE]: Disconnected from server: Maximum password attempts exceeded.";
                            Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));
                            socket.close();
                            return;
                        }
                    }
                }
            }
        }
    }

    private void deliverOfflineMessages() {
        List<Object> offlineMsgs = OfflineQueueManager.loadAndClearOfflineMessages(username);
        for (Object msg : offlineMsgs) {
            try {
                if (msg instanceof byte[]) {
                    Frame.sendFrame(out, Frame.TYPE_DIRECT_MSG, (byte[]) msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleLoungeMessage(byte[] frame) throws Exception {
        String text = new String(frame, 1, frame.length - 1, StandardCharsets.UTF_8).trim();

        if (text.startsWith("/")) {
            handleUserCommand(text);
            return;
        }

        if (SessionManager.isMuted(username)) {
            String errorMsg = ">> [SYSTEM NOTICE]: You are currently muted and cannot send messages.";
            Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, errorMsg.getBytes(StandardCharsets.UTF_8));
            return;
        }

        String formattedMessage = "[LOUNGE] " + username + ": " + text;
        byte[] payload = formattedMessage.getBytes(StandardCharsets.UTF_8);

        for (ConnectionThread client : SessionManager.getActiveClients().values()) {
            try {
                Frame.sendFrame(client.getOutputStream(), Frame.TYPE_LOUNGE_MSG, payload);
                if (!client.getUsername().equals(username)) {
                    ProfileManager.incrementReceivedMessages(client.getUsername());
                }
            } catch (Exception e) {
                SessionManager.removeClient(client.getUsername(), client);
                client.closeConnection();
            }
        }

        ProfileManager.incrementSentMessages(username);
    }

    private void handleUserCommand(String commandText) throws Exception {
        String[] parts = commandText.split("\\s+", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "/profile":
                ProfileManager.UserProfile profile = ProfileManager.loadProfile(username);

                StringBuilder stars = new StringBuilder();
                for (int i = 0; i < profile.getPassword().length(); i++) {
                    stars.append("*");
                }

                String info = "=== User Profile ===\n" +
                        "Username: " + profile.getUsername() + "\n" +
                        "Password: [HIDDEN] " + stars + "\n" +
                        "Messages Sent: " + profile.getMessagesSent() + "\n" +
                        "Messages Received: " + profile.getMessagesReceived() + "\n" +
                        "====================";
                Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, info.getBytes(StandardCharsets.UTF_8));
                break;

            case "/change_password":
                if (parts.length < 3) {
                    String notice = ">> [SYSTEM NOTICE]: Usage: /change_password <old_password> <new_password>";
                    Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));
                    return;
                }
                int result = ProfileManager.changePassword(username, parts[1], parts[2]);
                if (result == 0) {
                    String notice = ">> [SYSTEM NOTICE]: Password updated successfully.";
                    Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));
                } else if (result == 1) {
                    String notice = ">> [SYSTEM NOTICE]: Failed to update password. Current password does not match.";
                    Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));
                } else {
                    String notice = ">> [SYSTEM NOTICE]: Failed to update password. New password cannot be empty or identical to the old password.";
                    Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));
                }
                break;

            default:
                String notice = ">> [SYSTEM NOTICE]: Unknown command.";
                Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, notice.getBytes(StandardCharsets.UTF_8));
                break;
        }
    }

    private void handleDirectMessage(byte[] frame) throws Exception {
        if (SessionManager.isMuted(username)) {
            String errorMsg = ">> [SYSTEM NOTICE]: You are currently muted and cannot send messages.";
            Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, errorMsg.getBytes(StandardCharsets.UTF_8));
            return;
        }

        Frame.DirectMessageData data = Frame.parseDirectMessagePayload(frame);
        int messageId = data.messageId;
        String recipient = data.username;
        String messageText = data.text;

        if (!ProfileManager.userExists(recipient)) {
            String errorNotice = ">> [SYSTEM NOTICE]: Target user " + recipient + " is offline or does not exist.";
            Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, errorNotice.getBytes(StandardCharsets.UTF_8));
            return;
        }

        String formattedMessage = "[DM] " + username + ": " + messageText;
        byte[] deliveryPayload = Frame.buildDirectMessagePayload(messageId, username, formattedMessage);

        ConnectionThread recipientThread = SessionManager.getClientThread(recipient);

        if (recipientThread != null) {
            SessionManager.registerPendingAck(messageId, () -> {
                SessionManager.removeClient(recipient, recipientThread);
                recipientThread.closeConnection();
                OfflineQueueManager.enqueueMessage(recipient, deliveryPayload);
            });

            boolean sent;
            try {
                Frame.sendFrame(recipientThread.getOutputStream(), Frame.TYPE_DIRECT_MSG, deliveryPayload);
                sent = true;
            } catch (Exception e) {
                sent = false;
            }

            if (sent) {
                ProfileManager.incrementReceivedMessages(recipient);
            } else {
                SessionManager.confirmAck(messageId);
                SessionManager.removeClient(recipient, recipientThread);
                recipientThread.closeConnection();
                String offlineNotice = ">> [SYSTEM NOTICE]: User " + recipient
                        + " is offline. Message enqueued for offline delivery.";
                Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, offlineNotice.getBytes(StandardCharsets.UTF_8));
                OfflineQueueManager.enqueueMessage(recipient, deliveryPayload);
            }
        } else {
            String offlineNotice = ">> [SYSTEM NOTICE]: User " + recipient
                    + " is offline. Message enqueued for offline delivery.";
            Frame.sendFrame(out, Frame.TYPE_SYSTEM_NOTICE, offlineNotice.getBytes(StandardCharsets.UTF_8));
            OfflineQueueManager.enqueueMessage(recipient, deliveryPayload);
        }

        ProfileManager.incrementSentMessages(username);
    }

    private void handleAck(byte[] frame) {
        int messageId = ((frame[1] & 0xFF) << 24) | ((frame[2] & 0xFF) << 16)
                | ((frame[3] & 0xFF) << 8) | (frame[4] & 0xFF);
        SessionManager.confirmAck(messageId);
    }
}
