package Coordinator;

import java.io.*;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Coordinator extends UnicastRemoteObject implements CoordinatorInterface {
    private final Set<String> departments = new HashSet<>(Arrays.asList("development", "design", "OA"));

    private final Map<String, User> users;
    private final Map<String, String> tokens;
    private final List<String> activeNodes;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private static final int NODE_TIMEOUT = 3000;

    static class User {
        String password;
        String role;
        String department;

        public User(String password, String role, String department) {
            this.password = password;
            this.role = role;
            this.department = department;
        }
    }

    protected Coordinator() throws RemoteException {
        super();
        users = new HashMap<>();
        tokens = new HashMap<>();
        activeNodes = new ArrayList<>(Arrays.asList(
                "localhost:5001",
                "localhost:5002",
                "localhost:5003"
        ));

        // Initialize admin and sample users
        users.put("admin", new User("admin123", "manager", "ALL"));
        users.put("dev1", new User("dev123", "employee", "development"));
        users.put("design1", new User("design123", "employee", "design"));
    }

    // Implementation of all interface methods...
    // [يحتوي على جميع الدوال المذكورة في الإجابة السابقة]

    @Override
    public boolean addDepartment(String departmentName, String token) throws RemoteException {
        if (!isAdmin(token)) return false;
        return departments.add(departmentName.toLowerCase());
    }

    @Override
    public boolean removeDepartment(String departmentName, String token) throws RemoteException {
        if (!isAdmin(token)) return false;
        return departments.remove(departmentName.toLowerCase());
    }

    @Override
    public List<String> listDepartments(String token) throws RemoteException {
        if (!isValidToken(token)) return Collections.emptyList();
        return new ArrayList<>(departments);
    }
    private boolean isValidToken(String token) {
        return token != null && tokens.containsKey(token);
    }

    private boolean isAdmin(String token) {
        String username = getUsernameFromToken(token);
        User user = users.get(username);
        return user != null && "manager".equals(user.role);
    }


    @Override
    public synchronized boolean registerUser(String username, String password, String role, String department) throws RemoteException {
        if (users.containsKey(username)) {
            return false;
        }
        users.put(username, new User(password, role, department));
        return true;
    }

    @Override
    public synchronized String loginUser(String username, String password) throws RemoteException {
        User user = users.get(username);
        if (user != null && user.password.equals(password)) {
            String token = UUID.randomUUID().toString();
            tokens.put(token, username);
            return token;
        }
        return null;
    }

    private String getUsernameFromToken(String token) {
        return tokens.get(token);
    }

    private boolean hasPermission(String token, String department) {
        String username = getUsernameFromToken(token);
        if (username == null) return false;

        User user = users.get(username);
        return user != null &&
                (user.department.equals(department) ||
                        user.department.equals("ALL") ||
                        user.role.equals("manager"));
    }

    private String selectNode() {
        if (activeNodes.isEmpty()) {
            throw new RuntimeException("No active nodes available");
        }

        int index = currentIndex.getAndIncrement() % activeNodes.size();
        return activeNodes.get(index);
    }

    private boolean isNodeAlive(String node) {
        try {
            String[] parts = node.split(":");
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(parts[0], Integer.parseInt(parts[1])), NODE_TIMEOUT);
            socket.close();
            return true;
        } catch (IOException e) {
            activeNodes.remove(node);
            System.err.println("Node " + node + " is down. Removing from active nodes.");
            return false;
        }
    }

    private byte[] tryRequestFromNodes(String command, String filename, String department) {
        for (String node : new ArrayList<>(activeNodes)) {
            if (!isNodeAlive(node)) continue;

            try {
                byte[] data = requestFromNode(node, command, filename, department);
                if (data != null) return data;
            } catch (Exception e) {
                System.err.println("Error requesting from node " + node + ": " + e.getMessage());
            }
        }
        return null;
    }

    private boolean trySendToNodes(String command, String filename, byte[] data, String department) {
        boolean success = false;
        for (String node : new ArrayList<>(activeNodes)) {
            if (!isNodeAlive(node)) continue;

            try {
                success |= sendToNode(node, command, filename, data, department);
            } catch (Exception e) {
                System.err.println("Error sending to node " + node + ": " + e.getMessage());
            }
        }
        return success;
    }

    @Override
    public byte[] requestFile(String filename, String department, String token) throws RemoteException {
        if (!hasPermission(token, department)) return null;
        return tryRequestFromNodes("DOWNLOAD", filename, department);
    }

    @Override
    public boolean uploadFile(String filename, byte[] data, String department, String token) throws RemoteException {
        if (!hasPermission(token, department)) return false;
        return trySendToNodes("UPLOAD", filename, data, department);
    }

    @Override
    public boolean updateFile(String filename, byte[] data, String department, String token) throws RemoteException {
        if (!hasPermission(token, department)) return false;
        return trySendToNodes("UPDATE", filename, data, department);
    }

    @Override
    public boolean deleteFile(String filename, String department, String token) throws RemoteException {
        if (!hasPermission(token, department)) return false;
        return trySendToNodes("DELETE", filename, null, department);
    }

    @Override
    public List<String> listFiles(String department, String token) throws RemoteException {
        if (!hasPermission(token, department)) return Collections.emptyList();

        for (String node : activeNodes) {
            try {
                return requestFileListFromNode(node, department);
            } catch (Exception e) {
                System.err.println("Error listing files from node " + node + ": " + e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean syncFile(String filename, byte[] data, String department) throws RemoteException {
        return trySendToNodes("SYNC", filename, data, department);
    }

    private byte[] requestFromNode(String node, String command, String filename, String department) throws IOException {
        String[] parts = node.split(":");
        try (Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(command);
            out.writeObject(filename);
            out.writeObject(department);
            return (byte[]) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    private boolean sendToNode(String node, String command, String filename, byte[] data, String department) throws IOException {
        String[] parts = node.split(":");
        try (Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(command);
            out.writeObject(filename);
            out.writeObject(department);
            if (data != null) out.writeObject(data);

            return in.readBoolean();
        }
    }

    private List<String> requestFileListFromNode(String node, String department) throws IOException {
        String[] parts = node.split(":");
        try (Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("LIST");
            out.writeObject(department);
            return (List<String>) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    public static void main(String[] args) {
        try {
            Coordinator coordinator = new Coordinator();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("CoordinatorService", coordinator);
            System.out.println("Coordinator RMI Server is running...");

            // Health check thread
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(10000);
                        coordinator.activeNodes.removeIf(node -> !coordinator.isNodeAlive(node));
                        System.out.println("Active nodes: " + coordinator.activeNodes);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
