package Coordinator;

import Department.DepartmentManager;
import java.io.*;
import java.net.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Coordinator extends UnicastRemoteObject implements CoordinatorInterface {
    // البيانات الأساسية
    private final Set<String> departments = new HashSet<>(Arrays.asList("development", "design", "OA"));
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private final List<String> activeNodes = new CopyOnWriteArrayList<>();
    private final Set<String> failedNodes = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> nodeLoads = new ConcurrentHashMap<>();
    private static final int NODE_TIMEOUT = 3000;
    private static final int HEALTH_CHECK_INTERVAL = 10000;

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

    // Constructor
    public Coordinator() throws RemoteException {
        super();
        // Initialize nodes
        activeNodes.addAll(Arrays.asList(
                "localhost:5001",
                "localhost:5002",
                "localhost:5003"
        ));

        // Initialize node loads
        activeNodes.forEach(node -> nodeLoads.put(node, new AtomicInteger(0)));

        // Initialize default users
        users.put("admin", new User("admin123", "manager", "ALL"));
        users.put("dev1", new User("dev123", "employee", "development"));
        users.put("design1", new User("design123", "employee", "design"));

        startHealthCheckThread();
    }

    // ==================== إدارة العقد ====================
    private String selectLeastLoadedNode() {
        return activeNodes.stream()
                .min(Comparator.comparingInt(node -> nodeLoads.get(node).get()))
                .orElseThrow(() -> new RuntimeException("No active nodes available"));
    }

    private void checkFailedNodes() {
        failedNodes.removeIf(node -> {
            if (isNodeAlive(node)) {
                activeNodes.add(node);
                nodeLoads.putIfAbsent(node, new AtomicInteger(0));
                System.out.println("[Health] Node " + node + " is back online");
                return true;
            }
            return false;
        });
    }

    private void startHealthCheckThread() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                checkFailedNodes();
                activeNodes.removeIf(node -> !isNodeAlive(node));
                System.out.println("[Health] Active nodes: " + activeNodes);
            } catch (Exception e) {
                System.err.println("[Health Error] " + e.getMessage());
            }
        }, HEALTH_CHECK_INTERVAL, HEALTH_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private boolean isNodeAlive(String node) {
        try {
            String[] parts = node.split(":");
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), NODE_TIMEOUT);
            socket.close();
            return true;
        } catch (IOException e) {
            failedNodes.add(node);
            nodeLoads.remove(node);
            System.err.println("[Failure] Node " + node + " is down");
            return false;
        }
    }

    // ==================== الوظائف الأساسية ====================
    @Override
    public boolean registerUser(String username, String password, String role, String department) throws RemoteException {
        if (users.containsKey(username)) return false;
        users.put(username, new User(password, role, department));
        return true;
    }

    @Override
    public String loginUser(String username, String password) throws RemoteException {
        User user = users.get(username);
        if (user != null && user.password.equals(password)) {
            String token = UUID.randomUUID().toString();
            tokens.put(token, username);
            System.out.println("[Login] User '" + username + "' logged in. Token: " + token);
            return token;
        }
        System.out.println("[Login Failed] Invalid login attempt for user: " + username);
        return null;
    }


    @Override
    public byte[] requestFile(String filename, String department, String token) throws RemoteException {
        if (!hasPermission(token, department)) return null;

        String node = selectLeastLoadedNode();
        nodeLoads.get(node).incrementAndGet();
        try {
            return requestFromNode(node, "DOWNLOAD", filename, department);
        } catch (IOException e) {
            e.printStackTrace(); // أو سجل الخطأ باستخدام Logger
            return null;
        } finally {
            nodeLoads.get(node).decrementAndGet();
        }
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
                List<String> files = requestFileListFromNode(node, department);
                if (files != null && !files.isEmpty()) return files;
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

    // ==================== إدارة الأقسام ====================
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

    // ==================== الدوال المساعدة ====================
    private boolean trySendToNodes(String command, String filename, byte[] data, String department) {
        boolean success = false;
        for (String node : activeNodes) {
            nodeLoads.get(node).incrementAndGet();
            try {
                success |= sendToNode(node, command, filename, data, department);
            } catch (Exception e) {
                System.err.println("Error sending to node " + node + ": " + e.getMessage());
            } finally {
                nodeLoads.get(node).decrementAndGet();
            }
        }
        return success;
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

    private boolean isAdmin(String token) {
        String username = tokens.get(token);
        User user = users.get(username);
        return user != null && "manager".equals(user.role);
    }

    private boolean isValidToken(String token) {
        return token != null && tokens.containsKey(token);
    }

    private boolean hasPermission(String token, String department) {
        String username = tokens.get(token);
        if (username == null) return false;

        User user = users.get(username);
        return user != null &&
                (user.department.equals(department) ||
                        user.department.equals("ALL") ||
                        user.role.equals("manager"));
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
    // ==================== Main Method ====================
    public static void main(String[] args) {
        try {
            Coordinator coordinator = new Coordinator();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("CoordinatorService", coordinator);
            System.out.println("Coordinator service started successfully");
        } catch (Exception e) {
            System.err.println("Coordinator startup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}