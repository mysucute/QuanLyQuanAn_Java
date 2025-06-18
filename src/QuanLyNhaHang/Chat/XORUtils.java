package QuanLyNhaHang.Chat;

public class XORUtils {
    public static String xorEncrypt(String input, char key) {
        char[] output = new char[input.length()];
        for (int i = 0; i < input.length(); i++) {
            output[i] = (char)(input.charAt(i) ^ key);
        }
        return new String(output);
    }

    public static byte[] xorEncrypt(byte[] input, char key) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (byte)(input[i] ^ (byte) key);
        }
        return output;
    }
}
