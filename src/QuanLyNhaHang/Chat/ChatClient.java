package QuanLyNhaHang.Chat;

import QuanLyNhaHang.Chat.Message;

import java.io.*;
import java.net.*;

public class ChatClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private String role;

    public boolean connect(String username, String role) {
        this.username = username;
        this.role = role;
        try {
            socket = new Socket("localhost", 5000);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            Message response = (Message) in.readObject();
            if ("ENTER_USERNAME".equals(response.getContent())) {
                out.writeObject(new Message(Message.Type.TEXT, username, role, username));
                out.flush();
                response = (Message) in.readObject();
                if ("SUCCESS".equals(response.getContent().substring(0, 7))) {
                    System.out.println(response.getContent().substring(8));
                    return true;
                } else {
                    System.out.println(response.getContent());
                    return false;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Lỗi kết nối: " + e.getMessage());
            return false;
        }
        return false;
    }

    public void sendMessage(Message message) {
        try {
            if (out != null) {
                out.writeObject(message);
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("Lỗi gửi tin nhắn: " + e.getMessage());
        }
    }

    public Message receiveMessage() {
        try {
            if (in != null) {
                return (Message) in.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Lỗi nhận tin nhắn: " + e.getMessage());
        }
        return null;
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            System.out.println("Lỗi ngắt kết nối: " + e.getMessage());
        }
    }
}