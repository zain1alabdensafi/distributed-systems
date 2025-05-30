package Node;

import java.io.IOException;
import java.net.ServerSocket;

public class PortChecker {

    public static boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true; // البورت متاح
        } catch (IOException e) {
            return false; // البورت مشغول
        }
    }

    public static void main(String[] args) {
        int[] portsToCheck = {5000, 5001, 5002, 5500, 5501, 5502};

        for (int port : portsToCheck) {
            if (isPortAvailable(port)) {
                System.out.println("Port " + port + " is available.");
            } else {
                System.out.println("Port " + port + " is NOT available.");
            }
        }
    }
}
