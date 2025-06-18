package QuanLyNhaHang.BUS;

import MyCustom.MyDialog;
import QuanLyNhaHang.DAO.TaiKhoanDAO;
import QuanLyNhaHang.DTO.TaiKhoan;
import java.util.List;

public class TaiKhoanBUS {

    private TaiKhoanDAO taiKhoanDAO = new TaiKhoanDAO();
    private static TaiKhoan currentUser = null; // Track current logged-in user

    // Login method to set currentUser
    public boolean login(String tenDangNhap, String matKhau) {
        TaiKhoan user = taiKhoanDAO.getTaiKhoanByMaNVString(tenDangNhap);
        if (user != null && user.getMatKhau().equals(matKhau) && taiKhoanDAO.getTrangThai(user.getMaNhanVien()) == 1) {
            currentUser = user;
            // Update PhanQuyenBUS.quyenTK for permission checks
            PhanQuyenBUS phanQuyenBUS = new PhanQuyenBUS();
            phanQuyenBUS.kiemTraQuyen(user.getQuyen());
            return true;
        }
        return false;
    }

    // Get current logged-in user
    public TaiKhoan getCurrentUser() {
        return currentUser;
    }

    // Logout method to clear currentUser
    public void logout() {
        currentUser = null;
        PhanQuyenBUS.quyenTK = null; // Clear permission data
    }

    // Existing methods (unchanged)
    public String getTenDangNhapTheoMa(String maNV) {
        try {
            int ma = Integer.parseInt(maNV);
            return taiKhoanDAO.getTenDangNhapTheoMa(ma);
        } catch (NumberFormatException e) {
            return taiKhoanDAO.getTenDangNhapTheoMaNVString(maNV);
        }
    }

    public String getQuyenTheoMa(String maNV) {
        try {
            int ma = Integer.parseInt(maNV);
            return taiKhoanDAO.getQuyenTheoMa(ma);
        } catch (NumberFormatException e) {
            return taiKhoanDAO.getQuyenTheoMaNVString(maNV);
        }
    }

    public void datLaiMatKhau(String maNV, String tenDangNhap) {
        try {
            int ma = Integer.parseInt(maNV);
            boolean flag = taiKhoanDAO.datLaiMatKhau(ma, tenDangNhap);
            if (flag) {
                new MyDialog("Đặt lại thành công! Mật khẩu mới là: " + tenDangNhap, MyDialog.SUCCESS_DIALOG);
            } else {
                new MyDialog("Đặt lại thất bại!", MyDialog.ERROR_DIALOG);
            }
        } catch (NumberFormatException e) {
            new MyDialog("Mã nhân viên không hợp lệ!", MyDialog.ERROR_DIALOG);
        }
    }

    public void datLaiQuyen(String maNV, String quyen) {
        try {
            int ma = Integer.parseInt(maNV);
            boolean flag = taiKhoanDAO.datLaiQuyen(ma, quyen);
            if (flag) {
                new MyDialog("Đặt lại thành công!", MyDialog.SUCCESS_DIALOG);
            } else {
                new MyDialog("Đặt lại thất bại!", MyDialog.ERROR_DIALOG);
            }
        } catch (NumberFormatException e) {
            new MyDialog("Mã nhân viên không hợp lệ!", MyDialog.ERROR_DIALOG);
        }
    }

    public boolean kiemTraTrungTenDangNhap(String tenDangNhap) {
        return taiKhoanDAO.kiemTraTrungTenDangNhap(tenDangNhap);
    }

    public boolean themTaiKhoan(String maNV, String tenDangNhap, String quyen) {
        try {
            int ma = Integer.parseInt(maNV);
            if (tenDangNhap.trim().equals("")) {
                new MyDialog("Không được để trống Tên đăng nhập!", MyDialog.ERROR_DIALOG);
                return false;
            }
            if (kiemTraTrungTenDangNhap(tenDangNhap)) {
                MyDialog dlg = new MyDialog("Tên đăng nhập bị trùng! Có thể tài khoản bị khoá, thực hiện mở khoá?", MyDialog.WARNING_DIALOG);
                if (dlg.getAction() == MyDialog.OK_OPTION) {
                    moKhoaTaiKhoan(maNV);
                    return true;
                }
                return false;
            }
            boolean flag = taiKhoanDAO.themTaiKhoan(ma, tenDangNhap, quyen);
            if (flag) {
                new MyDialog("Cấp tài khoản thành công! Mật khẩu là " + tenDangNhap, MyDialog.SUCCESS_DIALOG);
            } else {
                new MyDialog("Cấp tài khoản thất bại! Tài khoản đã tồn tại", MyDialog.ERROR_DIALOG);
            }
            return flag;
        } catch (NumberFormatException e) {
            new MyDialog("Mã nhân viên không hợp lệ!", MyDialog.ERROR_DIALOG);
            return false;
        }
    }

    public void khoaTaiKhoan(String maNV) {
        try {
            int ma = Integer.parseInt(maNV);
            boolean flag = taiKhoanDAO.khoaTaiKhoan(ma);
            if (flag) {
                new MyDialog("Khoá tài khoản thành công!", MyDialog.SUCCESS_DIALOG);
            } else {
                new MyDialog("Khoá tài khoản thất bại!", MyDialog.ERROR_DIALOG);
            }
        } catch (NumberFormatException e) {
            new MyDialog("Mã nhân viên không hợp lệ!", MyDialog.ERROR_DIALOG);
        }
    }

    public void moKhoaTaiKhoan(String maNV) {
        try {
            int ma = Integer.parseInt(maNV);
            boolean flag = taiKhoanDAO.moKhoaTaiKhoan(ma);
            if (flag) {
                new MyDialog("Mở khoá tài khoản thành công!", MyDialog.SUCCESS_DIALOG);
            } else {
                new MyDialog("Mở khoá tài khoản thất bại!", MyDialog.ERROR_DIALOG);
            }
        } catch (NumberFormatException e) {
            new MyDialog("Mã nhân viên không hợp lệ!", MyDialog.ERROR_DIALOG);
        }
    }

    public boolean doiMatKhau(String matKhauCu, String matKhauMoi, String nhapLaiMatKhau) {
        if (!matKhauMoi.equals(nhapLaiMatKhau)) {
            new MyDialog("Mật khẩu mới không khớp!", MyDialog.ERROR_DIALOG);
            return false;
        }
        boolean flag = taiKhoanDAO.doiMatKhau(matKhauCu, matKhauMoi);
        if (flag) {
            new MyDialog("Đổi thành công!", MyDialog.SUCCESS_DIALOG);
        } else {
            new MyDialog("Mật khẩu cũ nhập sai!", MyDialog.ERROR_DIALOG);
        }
        return flag;
    }

    public int getTrangThai(String maNV) {
        try {
            int ma = Integer.parseInt(maNV);
            return taiKhoanDAO.getTrangThai(ma);
        } catch (NumberFormatException e) {
            new MyDialog("Mã nhân viên không hợp lệ!", MyDialog.ERROR_DIALOG);
            return -1;
        }
    }

    public List<TaiKhoan> getAllTaiKhoan() {
        List<TaiKhoan> list = taiKhoanDAO.getAllTaiKhoan();
        System.out.println("Số lượng tài khoản từ DAO: " + (list != null ? list.size() : 0));
        if (list != null) {
            for (TaiKhoan tk : list) {
                System.out.println("MaNV: " + tk.getMaNhanVien() + ", Ten: " + tk.getTen() + ", Quyen: " + tk.getQuyen());
            }
        } else {
            System.out.println("Danh sách tài khoản null!");
        }
        return list;
    }

    public TaiKhoan getTaiKhoanByMaNV(String maNV) {
        try {
            int ma = Integer.parseInt(maNV);
            return taiKhoanDAO.getTaiKhoanByMaNV(ma);
        } catch (NumberFormatException e) {
            return taiKhoanDAO.getTaiKhoanByMaNVString(maNV);
        }
    }
}