package Node;

import java.util.List;

public class Node3 {
    public static void main(String[] args) {
        int port = 5003;
        String directory = "NodeStorage/Node3";
        List<String> otherNodes = List.of("localhost:5001", "localhost:5002");

        Node node = new Node(port, directory, otherNodes);
        new Thread(node::start).start(); // يبدأ الخادم فقط دون مزامنة دورية
    }
}
/*package Node;

import java.util.List;

public class Node3 {
    public static void main(String[] args) {
        int port = 5003;
        String directory = "NodeStorage/Node3";
        // عدلنا هنا ليشمل Node1 و Node2
        List<String> otherNodes = List.of("localhost:5001", "localhost:5002");

        Node node = new Node(port, directory, otherNodes);
        new Thread(node::start).start();

        new Thread(() -> {
            try {
                Thread.sleep(5000); // تأخير قبل أول مزامنة
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (true) {
                try {
                    System.out.println("Node3: Starting synchronization...");
                    node.synchronizeWithNodes();
                    Thread.sleep(30 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }
}
*/