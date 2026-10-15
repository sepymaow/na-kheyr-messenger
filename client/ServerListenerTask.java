package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import shared.Frame;

public class ServerListenerTask implements Runnable {

    private final Socket socket;

    public ServerListenerTask(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            while (!socket.isClosed()) {
                byte[] frame = Frame.readNextFrame(socket, in);
                byte type = frame[0];

                switch (type) {
                    case Frame.TYPE_SYSTEM_NOTICE:
                    case Frame.TYPE_LOUNGE_MSG:
                        String content = new String(frame, 1, frame.length - 1, StandardCharsets.UTF_8);
                        System.out.println(content);
                        break;

                    case Frame.TYPE_DIRECT_MSG:
                        Frame.DirectMessageData dmData = Frame.parseDirectMessagePayload(frame);
                        System.out.println(dmData.text);

                        byte[] ackPayload = new byte[] {
                                (byte) (dmData.messageId >>> 24),
                                (byte) (dmData.messageId >>> 16),
                                (byte) (dmData.messageId >>> 8),
                                (byte) (dmData.messageId)
                        };
                        Frame.sendFrame(out, Frame.TYPE_ACK, ackPayload);
                        break;

                    case Frame.TYPE_ROOT_CMD:
                        byte cmdByte = frame[1];
                        if (cmdByte == Frame.ROOT_CMD_KICK) {
                            System.out.println(">> [SYSTEM NOTICE]: You have been kicked by the administrator.");
                            System.exit(0);
                        }
                        break;

                    default:
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("\n[SYSTEM]: Disconnected from server.");
            System.exit(0);
        }
    }
}
