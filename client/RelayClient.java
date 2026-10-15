package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import shared.Frame;

public class RelayClient {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        System.out.println("Connecting to Relay Server...");

        try {
            Socket socket = new Socket(host, port);
            System.out.println("Connected!");

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in);

            boolean authenticated = doLogin(socket, in, out, scanner);

            if (!authenticated) {
                socket.close();
                return;
            }

            Thread listenerThread = new Thread(new ServerListenerTask(socket));
            Thread readerThread = new Thread(new ConsoleReaderTask(socket, scanner));

            listenerThread.start();
            readerThread.start();

        } catch (Exception e) {
            System.err.println("Could not connect to server: " + e.getMessage());
        }
    }

    private static boolean doLogin(Socket socket, DataInputStream in, DataOutputStream out, Scanner scanner)
            throws Exception {

        printAuthMenu();


        while (true) {
            String option = scanner.nextLine().trim();
            while (!option.equals("1") && !option.equals("2")) {
                System.out.println("Invalid option. Please enter 1 for Register or 2 for Login:");
                option = scanner.nextLine().trim();
            }

            boolean isRegister = option.equals("1");
            byte type = isRegister ? Frame.TYPE_REGISTER : Frame.TYPE_LOGIN;

            System.out.println(isRegister ? "[Register]" : "[Login]");
            System.out.print(isRegister ? "Enter desired username: " : "Enter username: ");
            String username = scanner.nextLine().trim();
            System.out.print("Enter password: ");
            String password = scanner.nextLine().trim();

            String payload = username + " " + password;
            Frame.sendFrame(out, type, payload.getBytes(StandardCharsets.UTF_8));

            String response = readFrameText(socket, in);
            System.out.println(response);

            if (response.contains("successful")) {
                return true;
            }

            if (response.contains("Maximum password attempts exceeded")) {
                return false;
            }

            if (response.contains("Incorrect password")) {
                while (true) {
                    System.out.print("Enter password: ");
                    password = scanner.nextLine().trim();

                    Frame.sendFrame(out, Frame.TYPE_LOGIN, (username + " " + password).getBytes(StandardCharsets.UTF_8));

                    response = readFrameText(socket, in);
                    System.out.println(response);

                    if (response.contains("successful")) {
                        return true;
                    }
                    if (response.contains("Maximum password attempts exceeded")) {
                        return false;
                    }
                }
            }

            printAuthMenu();
        }
    }

    private static String readFrameText(Socket socket, DataInputStream in) throws Exception {
        byte[] frame = Frame.readNextFrame(socket, in);
        return new String(frame, 1, frame.length - 1, StandardCharsets.UTF_8);
    }

    private static void printAuthMenu() {
        System.out.println("========================================");
        System.out.println("Welcome to \"Na-Kheyr\" Messenger!");
        System.out.println("1. Register");
        System.out.println("2. Login");
        System.out.print("Choose an option (1 or 2): ");
    }
}
