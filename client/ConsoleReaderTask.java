package client;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Scanner;

import shared.Frame;

public class ConsoleReaderTask implements Runnable {

    private final Socket socket;
    private final Scanner scanner;
    private final Random random = new Random();

    public ConsoleReaderTask(Socket socket, Scanner scanner) {
        this.socket = socket;
        this.scanner = scanner;
    }

    @Override
    public void run() {
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            while (!socket.isClosed()) {
                if (!scanner.hasNextLine())
                    break;
                String line = scanner.nextLine().trim();

                if (line.isEmpty())
                    continue;

                if (line.equalsIgnoreCase("exit")) {
                    socket.close();
                    System.exit(0);
                    break;
                }

                if (line.startsWith("@")) {
                    String[] dmParts = line.substring(1).split(" ", 2);
                    if (dmParts.length < 2) {
                        System.out.println("Usage: @<username> <message>");
                        continue;
                    }

                    String recipient = dmParts[0];
                    String messageText = dmParts[1];
                    int messageId = random.nextInt();

                    byte[] payload = Frame.buildDirectMessagePayload(messageId, recipient, messageText);
                    Frame.sendFrame(out, Frame.TYPE_DIRECT_MSG, payload);

                    System.out.println("[Sent DM to " + recipient + " with ID " + messageId + "]");
                } else {
                    Frame.sendFrame(out, Frame.TYPE_LOUNGE_MSG, line.getBytes(StandardCharsets.UTF_8));
                }
            }

        } catch (Exception e) {
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }
}
