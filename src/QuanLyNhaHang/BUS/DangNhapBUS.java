package QuanLyNhaHang.BUS;

import MyCustom.MyDialog;
import QuanLyNhaHang.DAO.DangNhapDAO;
import QuanLyNhaHang.DTO.PhanQuyen;
import QuanLyNhaHang.DTO.TaiKhoan;

import java.io.*;

public class DangNhapBUS {

    private final static int EMPTY_ERROR = 1;
    public static TaiKhoan taiKhoanLogin = null;

    public TaiKhoan getTaiKhoanDangNhap(String user, String password, boolean selected) {
        user = user.trim();
        password = password.trim();

        if (user.isEmpty() || password.isEmpty()) {
            new MyDialog("Không được để trống thông tin!", MyDialog.ERROR_DIALOG);
            return null;
        }

        TaiKhoan tk = new TaiKhoan();
        tk.setTenDangNhap(user);
        tk.setMatKhau(password);

        DangNhapDAO dangNhapDAO = new DangNhapDAO();
        TaiKhoan account = dangNhapDAO.dangNhap(tk);
        taiKhoanLogin = account;

        if (account == null) {
            new MyDialog("Sai thông tin đăng nhập hoặc tài khoản đã bị khoá!", MyDialog.ERROR_DIALOG);
            return null;
        }

        PhanQuyenBUS phanQuyenBUS = new PhanQuyenBUS();
        phanQuyenBUS.kiemTraQuyen(account.getQuyen());
        xuLyGhiNhoDangNhap(user, password, selected);
        new MyDialog("Đăng nhập thành công!", MyDialog.SUCCESS_DIALOG);
        return account;
    }

    public String getTaiKhoanGhiNho() {
        try (BufferedReader br = new BufferedReader(new FileReader("remember.dat"))) {
            String line = br.readLine();
            return line != null ? line : "";
        } catch (IOException e) {
            System.out.println("Error reading remember.dat: " + e.getMessage());
            return "";
        }
    }

    private void xuLyGhiNhoDangNhap(String user, String password, boolean selected) {
        try (FileWriter fw = new FileWriter("remember.dat")) {
            if (!selected) {
                user = "";
                password = "";
            }
            fw.write(user + " | " + password);
        } catch (IOException e) {
            System.out.println("Error writing to remember.dat: " + e.getMessage());
        }
    }
}