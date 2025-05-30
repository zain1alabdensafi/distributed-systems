package Client;

import Coordinator.CoordinatorInterface;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;
import java.net.Socket;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.io.IOException;

public class Client {
    private static String token = null;
    private static CoordinatorInterface coordinator;
    private static String currentUser = null;
    private static String currentDepartment = null;

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            coordinator = (CoordinatorInterface) registry.lookup("CoordinatorService");
            Scanner scanner = new Scanner(System.in);

            System.out.println("=== Secure File Sharing System ===");

            while (true) {
                if (token == null) {
                    handleAuthentication(scanner);
                } else {
                    showMainMenu(scanner);
                }
            }
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    private static void handleAuthentication(Scanner scanner) throws Exception {
        System.out.println("\n1. Login");
        System.out.println("2. Register");
        System.out.println("3. Exit");
        System.out.print("Choose option: ");

        int choice = Integer.parseInt(scanner.nextLine());

        switch (choice) {
            case 1 -> {
                System.out.print("Username: ");
                String username = scanner.nextLine();
                System.out.print("Password: ");
                String password = scanner.nextLine();

                token = coordinator.loginUser(username, password);
                if (token != null) {
                    currentUser = username;
                    System.out.println("Login successful!");
                } else {
                    System.out.println("Invalid credentials!");
                }
            }
            case 2 -> {
                System.out.print("Username: ");
                String username = scanner.nextLine();
                System.out.print("Password: ");
                String password = scanner.nextLine();
                System.out.print("Department (development/design/OA): ");
                String department = scanner.nextLine();

                boolean registered = coordinator.registerUser(username, password, "employee", department);
                if (registered) {
                    System.out.println("Registration successful!");
                } else {
                    System.out.println("Registration failed!");
                }
            }
            case 3 -> System.exit(0);
            default -> System.out.println("Invalid option!");
        }
    }

    private static void showMainMenu(Scanner scanner) throws Exception {
        boolean isAdmin = currentUser != null && currentUser.equals("admin");

        while (true) {
            System.out.println("\nMain Menu:");
            System.out.println("1. Upload File");
            System.out.println("2. Download File");
        System.out.println("3. Update File");
        System.out.println("4. Delete File");
        System.out.println("5. List Files");
        System.out.println("6. Logout");
            if (isAdmin) {
                System.out.println("7. Admin Panel");
            }
            System.out.println("8. Logout");
            System.out.print("Choose option: ");
        System.out.print("Choose option: ");

        int choice = Integer.parseInt(scanner.nextLine());

        switch (choice) {
            case 1 -> uploadFile(scanner);
            case 2 -> downloadFile(scanner);
            case 3 -> updateFile(scanner);
            case 4 -> deleteFile(scanner);
            case 5 -> listFiles(scanner);
            case 7 -> {
                if (isAdmin) {
                    showAdminMenu(scanner);
                }
            }
            case 8 -> {
                token = null;
                currentUser = null;
                return;
            }
            default -> System.out.println("Invalid option!");
        }
    }
    }

    private static void uploadFile(Scanner scanner) {
        try {
            System.out.print("Enter department: ");
            String department = scanner.nextLine();
            System.out.print("Enter filename: ");
            String filename = scanner.nextLine();
            System.out.print("Enter file content: ");
            String content = scanner.nextLine();

            // إنشاء اتصال Socket مع timeout
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("localhost", 5001), 5000); // اتصال مع العقدة مباشرة

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                // إرسال البيانات
                out.writeObject("UPLOAD");
                out.writeObject(filename);
                out.writeObject(department);
                out.writeObject(content.getBytes());
                out.flush();  // إفراز المخزن المؤقت

                // استقبال الرد
                boolean success = in.readBoolean();
                System.out.println(success ? "File uploaded successfully!" : "Upload failed!");
            }
        } catch (IOException e) {
            System.err.println("Network error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void downloadFile(Scanner scanner) throws Exception {
        System.out.print("Enter department: ");
        String department = scanner.nextLine();
        System.out.print("Enter filename: ");
        String filename = scanner.nextLine();

        byte[] data = coordinator.requestFile(filename, department, token);
        if (data != null) {
            System.out.println("File content:\n" + new String(data));
        } else {
            System.out.println("File not found or access denied!");
        }
    }

    private static void updateFile(Scanner scanner) throws Exception {
        System.out.print("Enter department: ");
        String department = scanner.nextLine();
        System.out.print("Enter filename: ");
        String filename = scanner.nextLine();
        System.out.print("Enter new content: ");
        String content = scanner.nextLine();

        boolean success = coordinator.updateFile(filename, content.getBytes(), department, token);
        System.out.println(success ? "File updated successfully!" : "Update failed!");
    }

    private static void deleteFile(Scanner scanner) throws Exception {
        System.out.print("Enter department: ");
        String department = scanner.nextLine();
        System.out.print("Enter filename: ");
        String filename = scanner.nextLine();

        boolean success = coordinator.deleteFile(filename, department, token);
        System.out.println(success ? "File deleted successfully!" : "Delete failed!");
    }

    private static void listFiles(Scanner scanner) throws Exception {
        System.out.print("Enter department: ");
        String department = scanner.nextLine();

        List<String> files = coordinator.listFiles(department, token);
        if (files.isEmpty()) {
            System.out.println("No files found or access denied!");
        } else {
            System.out.println("Files in " + department + " department:");
            files.forEach(System.out::println);
        }
    }
    private static void showAdminMenu(Scanner scanner) throws Exception {
        System.out.println("\nAdmin Menu:");
        System.out.println("1. Add Department");
        System.out.println("2. Remove Department");
        System.out.println("3. List Departments");
        System.out.println("4. Back to Main Menu");
        System.out.print("Choose option: ");

        int choice = Integer.parseInt(scanner.nextLine());

        switch (choice) {
            case 1 -> addDepartment(scanner);
            case 2 -> removeDepartment(scanner);
            case 3 -> listDepartments(scanner);
            case 4 -> {} // العودة للقائمة الرئيسية
            default -> System.out.println("Invalid option!");
        }
    }

    private static void addDepartment(Scanner scanner) throws Exception {
        System.out.print("Enter new department name: ");
        String deptName = scanner.nextLine();

        boolean success = coordinator.addDepartment(deptName, token);
        System.out.println(success ? "Department added successfully!" : "Failed to add department!");
    }

    private static void removeDepartment(Scanner scanner) throws Exception {
        System.out.print("Enter department to remove: ");
        String deptName = scanner.nextLine();

        boolean success = coordinator.removeDepartment(deptName, token);
        System.out.println(success ? "Department removed successfully!" : "Failed to remove department!");
    }

    private static void listDepartments(Scanner scanner) throws Exception {
        List<String> departments = coordinator.listDepartments(token);
        if (departments.isEmpty()) {
            System.out.println("No departments found!");
        } else {
            System.out.println("Available departments:");
            departments.forEach(System.out::println);
        }
    }

    // نعدل showMainMenu لإظهار قائمة المدير إذا كان مستخدماً مديراً

        }