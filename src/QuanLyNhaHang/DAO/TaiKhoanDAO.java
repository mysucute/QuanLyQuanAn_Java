package QuanLyNhaHang.DAO;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import QuanLyNhaHang.BUS.DangNhapBUS;
import QuanLyNhaHang.DTO.TaiKhoan;

public class TaiKhoanDAO {

    // Assume MyConnect is a utility class with a static connection
    private static final String TABLE_TAIKHOAN = "taikhoan";
    private static final String TABLE_NHANVIEN = "nhanvien";

    public boolean themTaiKhoan(int maNV, String tenDangNhap, String quyen) {
        try {
            String sql = "INSERT INTO " + TABLE_TAIKHOAN + " (MaNV, TenDangNhap, MatKhau, Quyen, TrangThai) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setInt(1, maNV);
            pre.setString(2, tenDangNhap);
            pre.setString(3, tenDangNhap); // Use TenDangNhap as MatKhau
            pre.setString(4, quyen);
            pre.setInt(5, 1); // Default TrangThai to 1 (Active)
            return pre.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean kiemTraTrungTenDangNhap(String tenDangNhap) {
        try {
            String sql = "SELECT COUNT(*) FROM " + TABLE_TAIKHOAN + " WHERE TenDangNhap = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setString(1, tenDangNhap);
            ResultSet rs = pre.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getTenDangNhapTheoMa(int maNV) {
        try {
            String sql = "SELECT TenDangNhap FROM " + TABLE_TAIKHOAN + " WHERE MaNV = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setInt(1, maNV);
            ResultSet rs = pre.executeQuery();
            if (rs.next()) {
                return rs.getString("TenDangNhap");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    public String getTenDangNhapTheoMaNVString(String maNV) {
        try {
            String sql = "SELECT TenDangNhap FROM " + TABLE_TAIKHOAN + " WHERE TenDangNhap = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setString(1, maNV);
            ResultSet rs = pre.executeQuery();
            if (rs.next()) {
                return rs.getString("TenDangNhap");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    public boolean datLaiMatKhau(int maNV, String tenDangNhap) {
        try {
            String sql = "UPDATE " + TABLE_TAIKHOAN + " SET MatKhau = ? WHERE MaNV = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setString(1, tenDangNhap);
            pre.setInt(2, maNV);
            return pre.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean datLaiQuyen(int maNV, String quyen) {
        try {
            String sql = "UPDATE " + TABLE_TAIKHOAN + " SET Quyen = ? WHERE MaNV = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setString(1, quyen);
            pre.setInt(2, maNV);
            return pre.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getQuyenTheoMa(int maNV) {
        try {
            String sql = "SELECT Quyen FROM " + TABLE_TAIKHOAN + " WHERE MaNV = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setInt(1, maNV);
            ResultSet rs = pre.executeQuery();
            if (rs.next()) {
                return rs.getString("Quyen");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    public String getQuyenTheoMaNVString(String maNV) {
        try {
            String sql = "SELECT Quyen FROM " + TABLE_TAIKHOAN + " WHERE TenDangNhap = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setString(1, maNV);
            ResultSet rs = pre.executeQuery();
            if (rs.next()) {
                return rs.getString("Quyen");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    public boolean khoaTaiKhoan(int maNV) {
        try {
            String sql = "UPDATE " + TABLE_TAIKHOAN + " SET TrangThai = 0 WHERE MaNV = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setInt(1, maNV);
            return pre.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean moKhoaTaiKhoan(int maNV) {
        try {
            String sql = "UPDATE " + TABLE_TAIKHOAN + " SET TrangThai = 1 WHERE MaNV = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setInt(1, maNV);
            return pre.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean doiMatKhau(String matKhauCu, String matKhauMoi) {
        try {
            String sql = "UPDATE " + TABLE_TAIKHOAN + " SET MatKhau = ? WHERE MaNV = ? AND MatKhau = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setString(1, matKhauMoi);
            pre.setInt(2, DangNhapBUS.taiKhoanLogin.getMaNhanVien());
            pre.setString(3, matKhauCu);
            return pre.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getTrangThai(int maNV) {
        try {
            String sql = "SELECT TrangThai FROM " + TABLE_TAIKHOAN + " WHERE MaNV = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setInt(1, maNV);
            ResultSet rs = pre.executeQuery();
            if (rs.next()) {
                return rs.getInt("TrangThai");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // New method to get all accounts with full name from nhanvien table
    public List<TaiKhoan> getAllTaiKhoan() {
        List<TaiKhoan> list = new ArrayList<>();
        try {
            String sql = "SELECT tk.MaNV, tk.TenDangNhap, tk.MatKhau, tk.Quyen, nv.Ten " +
                        "FROM " + TABLE_TAIKHOAN + " tk " +
                        "JOIN " + TABLE_NHANVIEN + " nv ON tk.MaNV = nv.MaNV " +
                        "WHERE tk.TrangThai = 1";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            ResultSet rs = pre.executeQuery();
            while (rs.next()) {
                TaiKhoan tk = new TaiKhoan(
                    rs.getInt("MaNV"),
                    rs.getString("TenDangNhap"),
                    rs.getString("MatKhau"),
                    rs.getString("Quyen"),
                    rs.getString("Ten")
                );
                list.add(tk);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // New method to get account by MaNV with full name
    public TaiKhoan getTaiKhoanByMaNV(int maNV) {
        try {
            String sql = "SELECT tk.MaNV, tk.TenDangNhap, tk.MatKhau, tk.Quyen, nv.Ten " +
                        "FROM " + TABLE_TAIKHOAN + " tk " +
                        "JOIN " + TABLE_NHANVIEN + " nv ON tk.MaNV = nv.MaNV " +
                        "WHERE tk.MaNV = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setInt(1, maNV);
            ResultSet rs = pre.executeQuery();
            if (rs.next()) {
                return new TaiKhoan(
                    rs.getInt("MaNV"),
                    rs.getString("TenDangNhap"),
                    rs.getString("MatKhau"),
                    rs.getString("Quyen"),
                    rs.getString("Ten")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // New method to get account by string MaNV (e.g., "admin", "nv01")
    public TaiKhoan getTaiKhoanByMaNVString(String maNV) {
        try {
            String sql = "SELECT tk.MaNV, tk.TenDangNhap, tk.MatKhau, tk.Quyen, nv.Ten " +
                        "FROM " + TABLE_TAIKHOAN + " tk " +
                        "JOIN " + TABLE_NHANVIEN + " nv ON tk.MaNV = nv.MaNV " +
                        "WHERE tk.TenDangNhap = ?";
            PreparedStatement pre = MyConnect.conn.prepareStatement(sql);
            pre.setString(1, maNV);
            ResultSet rs = pre.executeQuery();
            if (rs.next()) {
                return new TaiKhoan(
                    rs.getInt("MaNV"),
                    rs.getString("TenDangNhap"),
                    rs.getString("MatKhau"),
                    rs.getString("Quyen"),
                    rs.getString("Ten")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}