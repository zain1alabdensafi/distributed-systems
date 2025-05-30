package Node;

import java.util.List;

public class Node1 {
    public static void main(String[] args) {
        int port = 5001;
        String directory = "NodeStorage/Node1";
        List<String> otherNodes = List.of("localhost:5002", "localhost:5003");

        Node node = new Node(port, directory, otherNodes);
        new Thread(node::start).start(); // يبدأ الخادم فقط دون مزامنة دورية
    }
}
/*package Node;

import java.util.List;

public class Node1 {
    public static void main(String[] args) {
        int port = 5001;
        String directory = "NodeStorage/Node1";
        List<String> otherNodes = List.of("localhost:5002", "localhost:5003");

        Node node = new Node(port, directory, otherNodes);
        new Thread(node::start).start();

        new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (true) {
                try {
                    System.out.println("Node1: Starting synchronization...");
                    node.synchronizeWithNodes();
                    Thread.sleep(30 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}


/*package Node;

import java.util.List;

public class DevelopmentNode {
    public static void main(String[] args) {
        int port = 5000;
        String directory = "NodeStorage/DevelopmentNode";
        List<String> otherNodes = List.of("localhost:5001", "localhost:5002");
        String department = "development";

        Node node = new Node(port, directory, otherNodes, department);
        new Thread(node::start).start();

        // Start daily synchronization at midnight
        new Thread(() -> {
            try {
                long now = System.currentTimeMillis();
                long midnight = java.time.LocalDate.now().plusDays(1)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
                long initialDelay = midnight - now;

                Thread.sleep(initialDelay);

                while (true) {
                    System.out.println("Starting daily synchronization...");
                    node.synchronizeWithNodes();
                    Thread.sleep(24 * 60 * 60 * 1000); // 24 hours
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
*/