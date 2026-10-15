package shared;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Frame {
    // ثابت‌های انواع فریم
    public static final byte TYPE_LOUNGE_MSG = 0x01;
    public static final byte TYPE_DIRECT_MSG = 0x02;
    public static final byte TYPE_ACK = 0x03;
    public static final byte TYPE_REGISTER = 0x04;
    public static final byte TYPE_ROOT_CMD = 0x05;
    public static final byte TYPE_SYSTEM_NOTICE = 0x06;
    public static final byte TYPE_LOGIN = 0x07;

    public static final byte ROOT_CMD_KICK = (byte) 0xDF;
    public static final byte ROOT_CMD_MUTE = (byte) 0xAE;

    public static byte[] readNextFrame(Socket socket, DataInputStream in) throws Exception {

        socket.setSoTimeout(0);

        int firstLengthByte = in.read();

        if (firstLengthByte == -1) {
            return null;
        }


        socket.setSoTimeout(500);

        try {
            int secondLengthByte = in.readUnsignedByte();
            int thirdLengthByte = in.readUnsignedByte();
            int fourthLengthByte = in.readUnsignedByte();

            int length =
                    ((firstLengthByte & 0xFF) << 24)
                            | ((secondLengthByte & 0xFF) << 16)
                            | ((thirdLengthByte & 0xFF) << 8)
                            | (fourthLengthByte & 0xFF);

            if (length < 1) {
                throw new IOException("Invalid frame length: " + length);
            }

            byte type = in.readByte();

            byte[] payload = new byte[length - 1];
            in.readFully(payload);

            byte[] frame = new byte[length];
            frame[0] = type;

            System.arraycopy(payload, 0, frame, 1, length - 1);

            return frame;

        } finally {
            socket.setSoTimeout(0);
        }
    }

    public static void sendFrame(DataOutputStream out,
            byte type,
            byte[] payload) throws Exception {

        if (payload == null)
            payload = new byte[0];

        synchronized (out) {
            out.writeInt(payload.length + 1);

            out.writeByte(type);

            if (payload.length > 0)
                out.write(payload);

            out.flush();
        }
    }


    public static byte[] buildDirectMessagePayload(int messageId, String username, String text) throws Exception {
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(byteStream);

        dataStream.writeInt(messageId);
        dataStream.writeByte(usernameBytes.length);
        dataStream.write(usernameBytes);
        dataStream.write(textBytes);

        return byteStream.toByteArray();
    }

    public static class DirectMessageData {
        public int messageId;
        public String username;
        public String text;
    }

    public static DirectMessageData parseDirectMessagePayload(byte[] frame) throws Exception {
        DataInputStream dataStream = new DataInputStream(new ByteArrayInputStream(frame, 1, frame.length - 1));

        DirectMessageData data = new DirectMessageData();
        data.messageId = dataStream.readInt();

        int usernameLength = dataStream.readUnsignedByte();
        byte[] usernameBytes = new byte[usernameLength];
        dataStream.readFully(usernameBytes);
        data.username = new String(usernameBytes, StandardCharsets.UTF_8);

        byte[] remaining = dataStream.readAllBytes();
        data.text = new String(remaining, StandardCharsets.UTF_8);

        return data;
    }
}
