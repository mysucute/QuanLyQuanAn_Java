package QuanLyNhaHang.Chat;

import QuanLyNhaHang.Chat.Message;
import QuanLyNhaHang.Chat.Message.Type;

import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 5000;
    private static ServerSocket serverSocket;
    private static Map<String, ObjectOutputStream> clients = new ConcurrentHashMap<>();
    private static Map<String, List<String>> groups = new ConcurrentHashMap<>();
    private static Map<String, String> groupNames = new ConcurrentHashMap<>();
    private static CopyOnWriteArrayList<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        System.out.println("Server đang khởi động...");
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server đã khởi động trên cổng " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client mới kết nối: " + clientSocket);
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandlers.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("Lỗi khi khởi động server: " + e.getMessage());
        } finally {
            shutdownServer();
        }
    }

    private static void broadcastMessage(Message msg, ClientHandler sender) throws IOException {
        for (ClientHandler client : clientHandlers) {
            if (client != sender && clients.containsKey(client.getUsername())) {
                synchronized (client.getOut()) {
                    client.getOut().writeObject(msg);
                    client.getOut().flush();
                }
            }
        }
        saveMessage("all", msg.getSender(), msg.getContent()); // Lưu tin nhắn chung
    }

    private static void sendPrivateMessage(String targetUsername, Message msg, ClientHandler sender) throws IOException {
        System.out.println("Sending private msg to " + targetUsername + ": " + msg.getContent());
        ObjectOutputStream writer = clients.get(targetUsername);
        if (writer != null) {
            synchronized (writer) {
                writer.writeObject(msg);
                writer.flush();
            }
            saveMessage(targetUsername, msg.getSender(), msg.getContent());
        }
    }

    private static void saveMessage(String receiver, String sender, String content) {
        if (content != null) { // Chỉ lưu tin nhắn văn bản
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("chat_history.txt", true))) {
                writer.write(sender + " -> " + receiver + ": " + content + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void sendUserList(ClientHandler client) throws IOException {
        StringBuilder userList = new StringBuilder("USER_LIST:");
        for (ClientHandler handler : clientHandlers) {
            if (handler.getUsername() != null && !handler.getUsername().isEmpty()) {
                userList.append(handler.getUsername()).append(",");
            }
        }
        if (userList.length() > 9) {
            userList.setLength(userList.length() - 1); // Remove trailing comma
            System.out.println("Gửi danh sách người dùng: " + userList.toString());
        } else {
            System.out.println("Không có người dùng nào để gửi!");
        }
        Message userListMsg = new Message(Type.TEXT, "SYSTEM", "system", userList.toString());
        client.getOut().writeObject(userListMsg);
        client.getOut().flush();
    }

    private static void shutdownServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler handler : clientHandlers) {
                handler.shutdownConnection();
            }
        } catch (IOException e) {
            System.out.println("Lỗi khi tắt server: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String username;
        private String role; // admin/employee

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                System.out.println("Lỗi kết nối client: " + e.getMessage());
            }
        }

        public String getUsername() {
            return username;
        }

        public ObjectOutputStream getOut() {
            return out;
        }

        public void run() {
            try {
                // Yêu cầu username và role
                out.writeObject(new Message(Type.TEXT, "SYSTEM", "system", "ENTER_USERNAME"));
                out.flush();
                Message usernameMsg = (Message) in.readObject();
                username = usernameMsg.getContent();
                role = usernameMsg.getRole();
                if (username == null || username.trim().isEmpty() || role == null || (!role.equals("admin") && !role.equals("employee"))) {
                    out.writeObject(new Message(Type.TEXT, "SYSTEM", "system", "ERROR: Username hoặc role không hợp lệ"));
                    shutdownConnection();
                    return;
                }

                synchronized (clients) {
                    clients.put(username, out);
                }
                out.writeObject(new Message(Type.TEXT, "SYSTEM", "system", "SUCCESS: Chào mừng " + username));
                out.flush();

                sendUserList(this);

                Message joinMsg = new Message(Type.TEXT, "SYSTEM", "system", username + " đã tham gia");
                broadcastMessage(joinMsg, this);

                loadChatHistory(this);

                Message msg;
                while ((msg = (Message) in.readObject()) != null) {
                    msg.setRole(role);
                    System.out.println("Nhận tin nhắn từ " + username + ": " + msg.getContent());
                    switch (msg.getType()) {
                        case CREATE_GROUP:
                            if (role.equals("admin")) {
                                createGroup(msg);
                            } else {
                                out.writeObject(new Message(Type.TEXT, "SYSTEM", "system", "ERROR: Chỉ admin có thể tạo nhóm"));
                            }
                            break;
                        case GROUP_MESSAGE:
                            forwardGroupMessage(msg);
                            break;
                        case TEXT:
                        case IMAGE:
                        case FILE:
                        case AUDIO:
                        case ICON:
                            if (msg.getContent() != null && msg.getContent().startsWith("@")) {
                                int colonIndex = msg.getContent().indexOf(':');
                                if (colonIndex > 0) {
                                    String targetUser = msg.getContent().substring(1, colonIndex).trim();
                                    String content = msg.getContent().substring(colonIndex + 1).trim();
                                    Message privateMsg = new Message(msg.getType(), username, role, content, msg.getData(), msg.getFileName(), msg.isEncrypted());
                                    privateMsg.setGroupId(targetUser); // Sử dụng groupId để định tuyến
                                    privateMsg.setTimestamp(LocalTime.now());
                                    sendPrivateMessage(targetUser, privateMsg, this);
                                    // Echo back to sender
                                    out.writeObject(new Message(Type.TEXT, "SYSTEM", "system", "SENT: Bạn (riêng cho " + targetUser + "): " + content));
                                    out.flush();
                                } else {
                                    broadcastMessage(msg, this);
                                    out.writeObject(new Message(Type.TEXT, "SYSTEM", "system", "SENT: Bạn: " + (msg.getContent() != null ? msg.getContent() : "[Media]")));
                                }
                            } else {
                                broadcastMessage(msg, this);
                                out.writeObject(new Message(Type.TEXT, "SYSTEM", "system", "SENT: Bạn: " + (msg.getContent() != null ? msg.getContent() : "[Media]")));
                            }
                            break;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Lỗi khi xử lý client " + username + ": " + e.getMessage());
            } finally {
                shutdownConnection();
            }
        }

        private void createGroup(Message msg) throws IOException {
            String groupInfo = msg.getContent();
            String[] parts = groupInfo.split(",", 2);
            if (parts.length < 2) {
                System.out.println("Invalid group creation request: " + groupInfo);
                return;
            }

            String groupName = parts[0];
            String[] members = parts[1].split(",");
            List<String> memberList = new ArrayList<>();
            for (String member : members) {
                memberList.add(member.trim());
            }

            String groupId = "group_" + System.currentTimeMillis();
            groups.put(groupId, memberList);
            groupNames.put(groupId, groupName);

            Message groupCreatedMsg = new Message(Type.CREATE_GROUP, msg.getSender(), role, groupName + "," + String.join(",", memberList));
            groupCreatedMsg.setGroupId(groupId);

            for (String member : memberList) {
                ObjectOutputStream memberOut = clients.get(member);
                if (memberOut != null) {
                    memberOut.writeObject(groupCreatedMsg);
                    memberOut.flush();
                }
            }
            System.out.println("Group created: " + groupName + " (ID: " + groupId + ") with members: " + memberList);
        }

        private void forwardGroupMessage(Message msg) throws IOException {
            String groupId = msg.getGroupId();
            List<String> members = groups.get(groupId);

            if (members == null) {
                System.out.println("Group not found: " + groupId);
                return;
            }

            Message groupMsg = new Message(Message.Type.GROUP_MESSAGE, msg.getSender(), msg.getRole(), msg.getContent(), msg.getData(), msg.getFileName(), msg.isEncrypted());
            groupMsg.setGroupId(groupId);
            groupMsg.setOriginalType(msg.getType()); // Set originalType
            groupMsg.setTimestamp(LocalTime.now());

            for (String member : members) {
                if (!member.equals(msg.getSender())) {
                    ObjectOutputStream memberOut = clients.get(member);
                    if (memberOut != null) {
                        memberOut.writeObject(groupMsg);
                        memberOut.flush();
                    }
                }
            }
            System.out.println("Group message forwarded to group " + groupId + " from " + msg.getSender());
        }

        private void loadChatHistory(ClientHandler client) {
            try (BufferedReader reader = new BufferedReader(new FileReader("chat_history.txt"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(username)) {
                        Message historyMsg = new Message(Type.TEXT, "SYSTEM", "system", line);
                        client.getOut().writeObject(historyMsg);
                        client.getOut().flush();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void shutdownConnection() {
            try {
                synchronized (clients) {
                    if (username != null) {
                        clients.remove(username);
                        Message leaveMsg = new Message(Type.TEXT, "SYSTEM", "system", username + " đã rời");
                        broadcastMessage(leaveMsg, this);
                    }
                }
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
                clientHandlers.remove(this);
            } catch (IOException e) {
                System.out.println("Lỗi khi đóng kết nối: " + e.getMessage());
            }
        }
    }
}