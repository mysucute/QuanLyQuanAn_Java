package QuanLyNhaHang.Chat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Steganography {

    public static byte[] hideMessage(byte[] imageBytes, String message, String password) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage image = ImageIO.read(bais);
        if (image == null) throw new IOException("Không thể đọc ảnh!");

        // Mã hóa thông điệp bằng XOR với ký tự đầu tiên của mật khẩu
        String encryptedMessage = XORUtils.xorEncrypt(message, password.charAt(0));
        byte[] messageBytes = encryptedMessage.getBytes(StandardCharsets.UTF_8);
        int messageLength = messageBytes.length;
        byte[] lengthBytes = intToBytes(messageLength);

        int imageCapacity = (image.getWidth() * image.getHeight() * 3 - 32) / 8; // Dành 32 bit cho độ dài
        if (messageLength > imageCapacity) {
            throw new IllegalArgumentException("Ảnh không đủ dung lượng để giấu thông điệp!");
        }

        // Ghi độ dài thông điệp (32 bit đầu tiên)
        int bitIndex = 0;
        for (int i = 0; i < 32; i++) {
            int x = (i % image.getWidth());
            int y = (i / image.getWidth());
            int rgb = image.getRGB(x, y);
            int bit = (lengthBytes[i / 8] >> (7 - (i % 8))) & 1;
            rgb = (rgb & 0xFFFFFFFE) | bit; // Chỉ thay đổi bit thấp nhất
            image.setRGB(x, y, rgb);
            bitIndex++;
        }

        // Ghi thông điệp đã mã hóa
        for (int i = 0; i < messageBytes.length * 8; i++) {
            int x = ((bitIndex + i) % image.getWidth());
            int y = ((bitIndex + i) / image.getWidth());
            int rgb = image.getRGB(x, y);
            int bit = (messageBytes[i / 8] >> (7 - (i % 8))) & 1;
            rgb = (rgb & 0xFFFFFFFE) | bit;
            image.setRGB(x, y, rgb);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos); // Giữ định dạng PNG
        return baos.toByteArray();
    }

    public static String extractMessage(byte[] imageBytes, String password) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage image = ImageIO.read(bais);
        if (image == null) return null;

        // Trích xuất độ dài thông điệp (32 bit đầu tiên)
        int bitIndex = 0;
        byte[] lengthBytes = new byte[4];
        for (int i = 0; i < 32; i++) {
            int x = (i % image.getWidth());
            int y = (i / image.getWidth());
            int rgb = image.getRGB(x, y);
            int bit = rgb & 1;
            lengthBytes[i / 8] |= (bit << (7 - (i % 8)));
            bitIndex++;
        }

        int messageLength = bytesToInt(lengthBytes);
        if (messageLength <= 0 || messageLength > (image.getWidth() * image.getHeight() * 3 - 32) / 8) {
            return null;
        }

        // Trích xuất thông điệp
        byte[] messageBytes = new byte[messageLength];
        for (int i = 0; i < messageLength * 8; i++) {
            int x = ((bitIndex + i) % image.getWidth());
            int y = ((bitIndex + i) / image.getWidth());
            int rgb = image.getRGB(x, y);
            int bit = rgb & 1;
            messageBytes[i / 8] |= (bit << (7 - (i % 8)));
        }

        String encryptedMessage = new String(messageBytes, StandardCharsets.UTF_8);
        return XORUtils.xorEncrypt(encryptedMessage, password.charAt(0));
    }

    private static byte[] intToBytes(int value) {
        return new byte[] {
            (byte) (value >> 24),
            (byte) (value >> 16),
            (byte) (value >> 8),
            (byte) value
        };
    }

    private static int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               (bytes[3] & 0xFF);
    }
}