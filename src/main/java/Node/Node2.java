package Node;

import java.util.List;

public class Node2 {
    public static void main(String[] args) {
        int port = 5002;
        String directory = "NodeStorage/Node2";
        List<String> otherNodes = List.of("localhost:5001", "localhost:5003");

        Node node = new Node(port, directory, otherNodes);
        new Thread(node::start).start(); // يبدأ الخادم فقط دون مزامنة دورية
    }
}
/*package Node;

import java.util.List;

public class Node2 {
    public static void main(String[] args) {
        int port = 5002;
        String directory = "NodeStorage/Node2";
        List<String> otherNodes = List.of("localhost:5001", "localhost:5003");

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
                    System.out.println("Node2: Starting synchronization...");
                    node.synchronizeWithNodes();
                    Thread.sleep(30 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}*/