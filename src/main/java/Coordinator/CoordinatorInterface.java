package Coordinator;

import Department.DepartmentManager;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface CoordinatorInterface extends Remote, DepartmentManager {
    boolean registerUser(String username, String password, String role, String department) throws RemoteException;
    String loginUser(String username, String password) throws RemoteException;
    byte[] requestFile(String filename, String department, String token) throws RemoteException;
    boolean uploadFile(String filename, byte[] data, String department, String token) throws RemoteException;
    boolean updateFile(String filename, byte[] data, String department, String token) throws RemoteException;
    boolean deleteFile(String filename, String department, String token) throws RemoteException;
    List<String> listFiles(String department, String token) throws RemoteException;
    boolean syncFile(String filename, byte[] data, String department) throws RemoteException;
}