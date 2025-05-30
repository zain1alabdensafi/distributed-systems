package Department;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface DepartmentManager extends Remote {
    boolean addDepartment(String departmentName, String token) throws RemoteException;
    boolean removeDepartment(String departmentName, String token) throws RemoteException;
    List<String> listDepartments(String token) throws RemoteException;
}