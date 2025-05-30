package org.example;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.net.Socket;
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        try {
            Remote remoteObj = new Remote() {};  // كائن Remote وهمي فقط للاختبار
            System.out.println("RMI is working!");

            Socket socket = new Socket("localhost", 8080);
            System.out.println("Socket is working!");
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}