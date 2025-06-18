package QuanLyNhaHang.DAO;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import QuanLyNhaHang.DTO.TaiKhoan;

public class DangNhapDAO {

    public TaiKhoan dangNhap(TaiKhoan tk) {
        try {
            String sql = "SELECT tk.MaNV, tk.TenDangNhap, tk.MatKhau, tk.Quyen, nv.Ten " +
                         "FROM taikhoan tk " +
                         "JOIN nhanvien nv ON tk.MaNV = nv.MaNV " +
                         "WHERE tk.TenDangNhap=? AND tk.MatKhau=? AND tk.TrangThai=1";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setString(1, tk.getTenDangNhap());
            pre.setString(2, tk.getMatKhau());
            ResultSet rs = pre.executeQuery();
            if (rs.next()) {
                TaiKhoan tkLogin = new TaiKhoan();
                tkLogin.setMaNhanVien(rs.getInt("MaNV"));
                tkLogin.setTenDangNhap(rs.getString("TenDangNhap"));
                tkLogin.setMatKhau(rs.getString("MatKhau"));
                tkLogin.setQuyen(rs.getString("Quyen"));
                tkLogin.setTen(rs.getString("Ten"));
                return tkLogin;
            }
        } catch (SQLException e) {
            System.out.println("Error in DangNhapDAO.dangNhap: " + e.getMessage());
            e.printStackTrace();
        }
        return null; // Return null for failed logins
    }
}