package Node;

import java.util.List;

public class NodeStarter {
    public static void main(String[] args) {
        // تشغيل 3 عقد تلقائياً
        new Thread(() -> {
            Node node1 = new Node(5001, "NodeStorage/Node1",
                    List.of("localhost:5002", "localhost:5003"));
            node1.start();
        }).start();

        new Thread(() -> {
            Node node2 = new Node(5002, "NodeStorage/Node2",
                    List.of("localhost:5001", "localhost:5003"));
            node2.start();
        }).start();

        new Thread(() -> {
            Node node3 = new Node(5003, "NodeStorage/Node3",
                    List.of("localhost:5001", "localhost:5002"));
            node3.start();
        }).start();

        System.out.println("All 3 nodes started successfully");
    }
}