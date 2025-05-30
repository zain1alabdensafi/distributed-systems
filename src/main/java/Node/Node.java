package Node;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.SocketException;
import java.io.EOFException;
import java.io.IOException;

public class Node {
    private final int port;
    private final String baseDirectory;
    private final List<String> otherNodes;
    private final ExecutorService executor;
    private final Map<String, String> departmentFolders;

    public Node(int port, String baseDirectory, List<String> otherNodes) {
        this.port = port;
        this.baseDirectory = baseDirectory;
        this.otherNodes = otherNodes;
        this.executor = Executors.newFixedThreadPool(10);

        this.departmentFolders = Map.of(
                "development", "dev",
                "design", "design",
                "OA", "oa"
        );

        initializeDirectories();
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(baseDirectory));
            for (String folder : departmentFolders.values()) {
                Files.createDirectories(Paths.get(baseDirectory, folder));
            }
        } catch (IOException e) {
            System.err.println("Error creating directories: " + e.getMessage());
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Node started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleRequest(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Node error: " + e.getMessage());
        }
    }

    private void handleRequest(Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(20000);
            // 1. إنشاء تيارات الإدخال/الإخراج أولاً
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

            // 2. قراءة الأمر الأساسي
            String command = (String) in.readObject();
            if (command == null) {
                return;
            }

            // 3. معالجة الأوامر المختلفة
            switch (command) {
                case "UPLOAD", "UPDATE", "SYNC" -> {
                    String filename = (String) in.readObject();
                    String department = (String) in.readObject();
                    byte[] data = (byte[]) in.readObject();

                    String departmentFolder = departmentFolders.getOrDefault(department, department);
                    Path filePath = Paths.get(baseDirectory, departmentFolder, filename);

                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    out.writeBoolean(true);
                    out.flush();

                    if (!command.equals("SYNC")) {
                        syncFile(filename, data, department);
                    }
                }
                case "DOWNLOAD" -> {
                    String filename = (String) in.readObject();
                    String department = (String) in.readObject();

                    String departmentFolder = departmentFolders.getOrDefault(department, department);
                    Path filePath = Paths.get(baseDirectory, departmentFolder, filename);

                    if (Files.exists(filePath)) {
                        out.writeObject(Files.readAllBytes(filePath));
                    } else {
                        out.writeObject(null);
                    }
                    out.flush();
                }
                case "DELETE" -> {
                    String filename = (String) in.readObject();
                    String department = (String) in.readObject();

                    String departmentFolder = departmentFolders.getOrDefault(department, department);
                    Path filePath = Paths.get(baseDirectory, departmentFolder, filename);

                    boolean deleted = Files.deleteIfExists(filePath);
                    out.writeBoolean(deleted);
                    out.flush();

                    if (deleted) {
                        propagateDelete(filename, department);
                    }
                }
                case "LIST" -> {
                    String department = (String) in.readObject();
                    String departmentFolder = departmentFolders.getOrDefault(department, department);
                    Path deptPath = Paths.get(baseDirectory, departmentFolder);

                    List<String> files = new ArrayList<>();
                    if (Files.exists(deptPath)) {
                        try (DirectoryStream<Path> stream = Files.newDirectoryStream(deptPath)) {
                            for (Path entry : stream) {
                                files.add(entry.getFileName().toString());
                            }
                        }
                    }
                    out.writeObject(files);
                    out.flush();
                }
                default -> {
                    out.writeBoolean(false);
                    out.flush();
                }
            }

        } catch (EOFException e) {
            System.out.println("Connection reset by client");
        } catch (Exception e) {
            System.err.println("Error handling request: " + e.getClass().getName() + ": " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }
    // الدوال المساعدة الجديدة
    private void handleListRequest(ObjectOutputStream out, ObjectInputStream in) throws IOException {
        try {
            String department = (String) in.readObject();
            String departmentFolder = departmentFolders.getOrDefault(department, department);
            Path deptPath = Paths.get(baseDirectory, departmentFolder);

            List<String> files = new ArrayList<>();
            if (Files.exists(deptPath)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(deptPath)) {
                    for (Path entry : stream) {
                        files.add(entry.getFileName().toString());
                    }
                }
            }
            out.writeObject(files);
        } catch (Exception e) {
            out.writeObject(Collections.emptyList());
        }
    }

    private void handleUploadRequest(ObjectOutputStream out, ObjectInputStream in, String command) throws IOException {
        try {
            String filename = (String) in.readObject();
            String department = (String) in.readObject();
            byte[] data = (byte[]) in.readObject();

            String departmentFolder = departmentFolders.getOrDefault(department, department);
            Path filePath = Paths.get(baseDirectory, departmentFolder, filename);

            Files.createDirectories(filePath.getParent());
            Files.write(filePath, data);
            out.writeBoolean(true);

            if (!command.equals("SYNC")) {
                syncFile(filename, data, department);
            }
        } catch (Exception e) {
            out.writeBoolean(false);
        }
    }

    private void handleDownloadRequest(ObjectOutputStream out, ObjectInputStream in) throws IOException {
        try {
            String filename = (String) in.readObject();
            String department = (String) in.readObject();

            String departmentFolder = departmentFolders.getOrDefault(department, department);
            Path filePath = Paths.get(baseDirectory, departmentFolder, filename);

            if (Files.exists(filePath)) {
                out.writeObject(Files.readAllBytes(filePath));
            } else {
                out.writeObject(null);
            }
        } catch (Exception e) {
            out.writeObject(null);
        }
    }

    private void handleDeleteRequest(ObjectOutputStream out, ObjectInputStream in) throws IOException {
        try {
            String filename = (String) in.readObject();
            String department = (String) in.readObject();

            String departmentFolder = departmentFolders.getOrDefault(department, department);
            Path filePath = Paths.get(baseDirectory, departmentFolder, filename);

            boolean deleted = Files.deleteIfExists(filePath);
            out.writeBoolean(deleted);

            if (deleted) {
                propagateDelete(filename, department);
            }
        } catch (Exception e) {
            out.writeBoolean(false);
        }
    }

    private void syncFile(String filename, byte[] data, String department) {
        for (String nodeAddress : otherNodes) {
            if (nodeAddress.equals("localhost:" + port)) continue;

            new Thread(() -> {
                try {
                    String[] parts = nodeAddress.split(":");

                    try (Socket socket = new Socket(parts[0], Integer.parseInt(parts[1])); ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        out.writeObject("SYNC");
                        out.writeObject(filename);
                        out.writeObject(department);
                        out.writeObject(data);
                        out.flush();
                    }
                } catch (Exception e) {
                    System.err.println("Sync failed with node " + nodeAddress + ": " + e.getMessage());
                }
            }).start();
        }
    }

    private void propagateDelete(String filename, String department) {
        for (String node : otherNodes) {
            if (node.equals("localhost:" + port)) continue;

            executor.submit(() -> {
                try {
                    String[] parts = node.split(":");
                    try (Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
                         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                         ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                        out.writeObject("DELETE");
                        out.writeObject(filename);
                        out.writeObject(department);
                        in.readBoolean();
                    }
                } catch (Exception e) {
                    System.err.println("Delete propagation failed with node " + node + ": " + e.getMessage());
                }
            });
        }
    }
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: Node <port> <baseDir> <otherNode1> <otherNode2> ...");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String baseDir = args[1];
        List<String> otherNodes = Arrays.asList(args).subList(2, args.length);

        Node node = new Node(port, baseDir, otherNodes);
        node.start();
    }
}

/*
 new Thread(() -> {
            try {
                Thread.sleep(5000); // تأخير قبل البدء بالمزامنة
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (true) {
                try {
                    System.out.println("Starting synchronization...");
                    node.synchronizeWithNodes();
                    Thread.sleep(30 * 1000); // كل 30 ثانية
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
 */