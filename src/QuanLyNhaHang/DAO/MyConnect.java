package QuanLyNhaHang.DAO;

import MyCustom.MyDialog;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class MyConnect {

    public static Connection conn = null;
    private String serverName;
    private String dbName;
    private String userName;
    private String password;

    public MyConnect() {
        docFileText();

        // Update the JDBC URL to include the port if needed (e.g., :3306 for default MySQL port)
        String strConnect = "jdbc:mysql://" + serverName + ":3306/" + dbName + "?useUnicode=true&characterEncoding=utf-8";
        Properties pro = new Properties();
        pro.put("user", userName);
        pro.put("password", password);
        try {
            // Use the updated driver class for MySQL Connector/J 8.0+
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = java.sql.DriverManager.getConnection(strConnect, pro);
        } catch (SQLException ex) {
            new MyDialog("Không kết nối được tới CSDL! Lỗi: " + ex.getMessage(), MyDialog.ERROR_DIALOG);
            ex.printStackTrace(); // Log the stack trace for debugging
            System.exit(0);
        } catch (ClassNotFoundException ex) {
            new MyDialog("Không tìm thấy driver MySQL! Lỗi: " + ex.getMessage(), MyDialog.ERROR_DIALOG);
            ex.printStackTrace();
            System.exit(0);
        }
    }

    private void docFileText() {
        // Initialize variables
        serverName = "";
        dbName = "";
        userName = "";
        password = "";

        try {
            FileInputStream fis = new FileInputStream("ConnectVariable.txt");
            InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
            BufferedReader br = new BufferedReader(isr);

            serverName = br.readLine();
            dbName = br.readLine();
            userName = br.readLine();
            password = br.readLine();

            if (password == null) {
                password = "";
            }

            br.close();
        } catch (Exception e) {
            new MyDialog("Lỗi đọc file ConnectVariable.txt: " + e.getMessage(), MyDialog.ERROR_DIALOG);
            e.printStackTrace();
        }
    }
}