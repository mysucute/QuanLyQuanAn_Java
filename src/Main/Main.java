package Main;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import QuanLyNhaHang.DAO.MyConnect;
import QuanLyNhaHang.GUI.DangNhapGUI;

public class Main {

    public static void main(String[] args) {
        new MyConnect();

        changLNF("Nimbus");
        DangNhapGUI login = new DangNhapGUI();
        login.showWindow();
    }
    
    public class LookAndFeelUtil {
        public static void setLookAndFeel() {
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void changLNF(String nameLNF) {
    	LookAndFeelUtil.setLookAndFeel();
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if (nameLNF.equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
        }
    }
}
