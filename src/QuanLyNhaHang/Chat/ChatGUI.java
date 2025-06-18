package QuanLyNhaHang.Chat;

import QuanLyNhaHang.Chat.Message;
import QuanLyNhaHang.Chat.Message.Type;
import QuanLyNhaHang.Chat.Steganography;
import QuanLyNhaHang.Chat.XORUtils;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import javax.sound.sampled.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;

// Assume this class provides user data from the taikhoan table
import QuanLyNhaHang.BUS.TaiKhoanBUS;
import QuanLyNhaHang.DTO.TaiKhoan;

public class ChatGUI extends JFrame {
    private ChatClient chatClient;
    private JTextPane chatPane;
    private JTextField inputField;
    private JButton sendBtn, mediaBtn, iconBtn, createGroupBtn, recordBtn;
    private JComboBox<String> userComboBox;
    private DefaultListModel<String> conversationListModel;
    private JList<String> conversationList;
    private String currentUser; // MaNV as string
    private String role;
    private Clip currentClip;
    private final char XOR_KEY = 'T';
    private final Map<String, byte[]> encryptedImageMap = new ConcurrentHashMap<>();
    private final Map<String, Message> pendingMessages = new ConcurrentHashMap<>();
    private Map<String, List<Message>> conversations = new ConcurrentHashMap<>();
    private Map<String, String> conversationNames = new ConcurrentHashMap<>();
    private Map<String, String> displayNameToId = new ConcurrentHashMap<>();
    private String currentConversation;
    private volatile boolean isRecording = false;
    private AudioFormat audioFormat;
    private TargetDataLine targetDataLine;
    private Thread recordingThread;
    private TaiKhoanBUS taiKhoanBUS;

    public ChatGUI(String username, String role) {
        this.currentUser = username;
        this.role = role;
        this.taiKhoanBUS = new TaiKhoanBUS();
        this.chatClient = new ChatClient();
        if (!chatClient.connect(username, role)) {
            JOptionPane.showMessageDialog(this, "Kết nối thất bại!");
            dispose();
            return;
        }

        setTitle("Chat - " + username + " (" + role + ")");
        setSize(600, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        buildUI();

        new Thread(() -> {
            while (true) {
                try {
                    Message msg = chatClient.receiveMessage();
                    if (msg != null) {
                        System.out.println("Nhận tin nhắn: " + (msg.getContent() != null ? msg.getContent() : "[Media]") + " từ " + msg.getSender());
                        SwingUtilities.invokeLater(() -> handleMessage(msg));
                    }
                } catch (Exception e) {
                    System.out.println("Lỗi nhận tin nhắn: " + e.getMessage());
                    break;
                }
            }
        }).start();

        loadConversationList();
        loadAvailableUsers();
    }

    private void buildUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout());
        userComboBox = new JComboBox<>();
        userComboBox.addActionListener(e -> {
            String selectedUserName = (String) userComboBox.getSelectedItem();
            if (selectedUserName != null && !selectedUserName.isEmpty()) {
                String userId = displayNameToId.get(selectedUserName);
                if (userId != null && !userId.equals(currentUser)) {
                    currentConversation = userId;
                    conversationList.setSelectedValue(selectedUserName, true);
                    displayConversation(userId);
                }
            }
        });
        topPanel.add(new JLabel("Chọn người nhận: "));
        topPanel.add(userComboBox);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        conversationListModel = new DefaultListModel<>();
        conversationList = new JList<>(conversationListModel);
        conversationList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedConversation = conversationList.getSelectedValue();
                if (selectedConversation != null) {
                    String userId = displayNameToId.get(selectedConversation);
                    if (userId != null && !userId.equals(currentUser)) {
                        currentConversation = userId;
                        userComboBox.setSelectedItem(selectedConversation);
                        displayConversation(userId);
                    }
                }
            }
        });
        JScrollPane conversationScrollPane = new JScrollPane(conversationList);
        conversationScrollPane.setPreferredSize(new Dimension(150, 0));
        mainPanel.add(conversationScrollPane, BorderLayout.WEST);

        chatPane = new JTextPane();
        chatPane.setContentType("text/html");
        chatPane.setEditable(false);
        chatPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String desc = e.getDescription();
                if (desc.startsWith("unlock_image_")) handleUnlockImage(desc);
                else if (desc.startsWith("unlock_file_")) handleUnlockFile(desc);
                else if (desc.startsWith("unlock_audio_")) handleUnlockAudio(desc);
                else if (desc.startsWith("file_")) saveFile(desc);
                else if (desc.startsWith("audio_")) playAudio(encryptedImageMap.get(desc));
                else if (desc.startsWith("stop_audio_")) stopAudio();
                else if (desc.startsWith("save_image_")) saveFile(desc);
            }
        });
        chatPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    int offset = chatPane.viewToModel2D(e.getPoint());
                    if (offset == -1) return;
                    HTMLDocument doc = (HTMLDocument) chatPane.getDocument();
                    javax.swing.text.Element elem = doc.getCharacterElement(offset);
                    javax.swing.text.Element imgElement = null;
                    javax.swing.text.Element current = elem;
                    while (current != null) {
                        if (current.getName().equals("img")) {
                            imgElement = current;
                            break;
                        }
                        current = current.getParentElement();
                    }
                    if (imgElement != null) {
                        javax.swing.text.AttributeSet attrs = imgElement.getAttributes();
                        String imageId = (String) attrs.getAttribute(javax.swing.text.html.HTML.Attribute.ID);
                        if (imageId != null && imageId.startsWith("image_")) {
                            byte[] imgBytes = encryptedImageMap.get(imageId);
                            if (imgBytes != null) {
                                JFrame imageFrame = new JFrame("Xem ảnh");
                                imageFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                                JLabel imageLabel = new JLabel(new ImageIcon(imgBytes));
                                imageFrame.add(new JScrollPane(imageLabel));
                                imageFrame.pack();
                                imageFrame.setLocationRelativeTo(null);
                                imageFrame.setVisible(true);
                            }
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ChatGUI.this, "Lỗi khi hiển thị ảnh: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JScrollPane chatScrollPane = new JScrollPane(chatPane);
        mainPanel.add(chatScrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.addActionListener(e -> sendTextMessage());
        sendBtn = new JButton("Gửi");
        mediaBtn = new JButton("Media");
        iconBtn = new JButton("Icon");
        createGroupBtn = new JButton("Tạo nhóm");
        recordBtn = new JButton("Ghi âm");

        sendBtn.addActionListener(e -> sendTextMessage());
        mediaBtn.addActionListener(e -> sendMediaMessage());
        iconBtn.addActionListener(e -> chooseAndSendIcon());
        createGroupBtn.addActionListener(e -> createGroup());
        recordBtn.addActionListener(e -> {
            if (!isRecording) startRecording();
            else stopRecording();
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(recordBtn);
        buttonPanel.add(sendBtn);
        buttonPanel.add(iconBtn);
        buttonPanel.add(mediaBtn);
        buttonPanel.add(createGroupBtn);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setVisible(true);
    }

    private void loadConversationList() {
        conversationListModel.clear();
        for (String userId : conversations.keySet()) {
            if (!userId.equals(currentUser)) { // Include groups and other users
                String displayName = conversationNames.getOrDefault(userId, userId);
                if (!conversationListModel.contains(displayName)) {
                    conversationListModel.addElement(displayName);
                    displayNameToId.putIfAbsent(displayName, userId);
                }
            }
        }
        // Load from chat_history.txt for users not in conversations map
        try (BufferedReader reader = new BufferedReader(new FileReader("chat_history.txt"))) {
            String line;
            Set<String> users = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                if (line.contains(" -> ")) {
                    String[] parts = line.split(" -> ");
                    if (parts.length >= 2) {
                        String sender = parts[0].trim();
                        String receiver = parts[1].split(":")[0].trim();
                        if (sender.equals(currentUser) && !receiver.equals("all") && !users.contains(receiver)) {
                            TaiKhoan tk = taiKhoanBUS.getTaiKhoanByMaNV(receiver);
                            String displayName = tk != null ? tk.getTen() : receiver;
                            if (!conversationListModel.contains(displayName)) {
                                conversationListModel.addElement(displayName);
                                displayNameToId.putIfAbsent(displayName, receiver);
                                conversationNames.putIfAbsent(receiver, displayName);
                                conversations.putIfAbsent(receiver, new ArrayList<>());
                            }
                            users.add(receiver);
                        } else if (receiver.equals(currentUser) && !users.contains(sender)) {
                            TaiKhoan tk = taiKhoanBUS.getTaiKhoanByMaNV(sender);
                            String displayName = tk != null ? tk.getTen() : sender;
                            if (!conversationListModel.contains(displayName)) {
                                conversationListModel.addElement(displayName);
                                displayNameToId.putIfAbsent(displayName, sender);
                                conversationNames.putIfAbsent(sender, displayName);
                                conversations.putIfAbsent(sender, new ArrayList<>());
                            }
                            users.add(sender);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Lỗi đọc lịch sử chat: " + e.getMessage());
        }
        if (!conversationListModel.isEmpty()) {
            conversationList.setSelectedIndex(0);
        }
    }

    private void loadAvailableUsers() {
        try {
            List<TaiKhoan> users = taiKhoanBUS.getAllTaiKhoan();
            userComboBox.removeAllItems();
            for (TaiKhoan tk : users) {
                String userId = String.valueOf(tk.getMaNhanVien());
                if (!userId.equals(currentUser)) {
                    String fullName = tk.getTen();
                    if (fullName != null && !fullName.isEmpty() && !containsItem(userComboBox, fullName)) {
                        userComboBox.addItem(fullName);
                        displayNameToId.putIfAbsent(fullName, userId);
                        conversationNames.putIfAbsent(userId, fullName);
                        conversations.putIfAbsent(userId, new ArrayList<>());
                    }
                }
            }
            if (userComboBox.getItemCount() > 0) {
                userComboBox.setSelectedIndex(0);
                String selectedUserName = (String) userComboBox.getSelectedItem();
                currentConversation = displayNameToId.get(selectedUserName);
                displayConversation(currentConversation);
            } else {
                JOptionPane.showMessageDialog(this, "Không tìm thấy người dùng khác để chat!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi khi tải danh sách người dùng: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendTextMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || userComboBox.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tin nhắn và chọn người nhận!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selectedUserName = (String) userComboBox.getSelectedItem();
        String targetUserId = displayNameToId.get(selectedUserName);
        if (targetUserId != null && !targetUserId.equals(currentUser)) {
            currentConversation = targetUserId;
            String encrypted = XORUtils.xorEncrypt(text, XOR_KEY);
            Message msg = new Message(Message.Type.TEXT, currentUser, role, encrypted, null, null, true);
            msg.setGroupId(targetUserId); // Sử dụng groupId để định tuyến
            msg.setTimestamp(LocalTime.now());
            conversations.computeIfAbsent(targetUserId, k -> new ArrayList<>()).add(msg);
            conversationNames.putIfAbsent(targetUserId, selectedUserName);
            if (!conversationListModel.contains(selectedUserName)) {
                conversationListModel.addElement(selectedUserName);
                displayNameToId.putIfAbsent(selectedUserName, targetUserId);
            }
            displayConversation(targetUserId);
            chatClient.sendMessage(msg);
            inputField.setText("");
        } else {
            JOptionPane.showMessageDialog(this, "Không thể nhắn với chính mình!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendMediaMessage() {
        if (userComboBox.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người nhận!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String fileName = file.getName().toLowerCase();
            try {
                byte[] fileData = Files.readAllBytes(file.toPath());
                boolean isEncrypted = false;
                byte[] dataToSend = fileData;
                Message.Type msgType;
                if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    msgType = Message.Type.IMAGE;
                    int choice = JOptionPane.showConfirmDialog(this, "Gửi ảnh công khai?", "Gửi ảnh", JOptionPane.YES_NO_OPTION);
                    if (choice == JOptionPane.NO_OPTION) {
                        Object[] options = {"Mã hóa ảnh", "Giấu tin trong ảnh"};
                        int stegoChoice = JOptionPane.showOptionDialog(this, "Mã hóa hay giấu tin?", "Chọn chế độ", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                        if (stegoChoice == 0) {
                            String keyInput = JOptionPane.showInputDialog(this, "Nhập mã khóa:");
                            if (keyInput != null && !keyInput.isEmpty()) {
                                dataToSend = XORUtils.xorEncrypt(fileData, keyInput.charAt(0));
                                isEncrypted = true;
                            }
                        } else if (stegoChoice == 1) {
                            JTextField messageField = new JTextField(20);
                            JTextField passwordField = new JTextField(10);
                            JPanel panel = new JPanel(new GridLayout(0, 1));
                            panel.add(new JLabel("Thông điệp cần giấu:"));
                            panel.add(messageField);
                            panel.add(new JLabel("Mật khẩu:"));
                            panel.add(passwordField);
                            if (JOptionPane.showConfirmDialog(this, panel, "Giấu tin", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                                String hiddenMessage = messageField.getText().trim();
                                String password = passwordField.getText().trim();
                                if (hiddenMessage.isEmpty() || password.isEmpty()) {
                                    JOptionPane.showMessageDialog(this, "Thông điệp và mật khẩu không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }
                                try {
                                    dataToSend = Steganography.hideMessage(fileData, hiddenMessage, password);
                                    isEncrypted = true;
                                    String imageId = "image_" + System.currentTimeMillis();
                                    encryptedImageMap.put(imageId, dataToSend); // Lưu ảnh đã giấu tin
                                } catch (IOException e) {
                                    JOptionPane.showMessageDialog(this, "Lỗi khi giấu tin: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }
                            }
                        }
                    }
                } else if (fileName.endsWith(".txt")) {
                    msgType = Message.Type.FILE;
                    int choice = JOptionPane.showConfirmDialog(this, "Gửi file công khai?", "Gửi file", JOptionPane.YES_NO_OPTION);
                    if (choice == JOptionPane.NO_OPTION) {
                        String keyInput = JOptionPane.showInputDialog(this, "Nhập mã khóa:");
                        if (keyInput != null && !keyInput.isEmpty()) {
                            dataToSend = XORUtils.xorEncrypt(fileData, keyInput.charAt(0));
                            isEncrypted = true;
                        }
                    }
                } else if (fileName.endsWith(".wav") || fileName.endsWith(".mp3")) {
                    msgType = Message.Type.AUDIO;
                    int choice = JOptionPane.showConfirmDialog(this, "Gửi âm thanh công khai?", "Gửi âm thanh", JOptionPane.YES_NO_OPTION);
                    if (choice == JOptionPane.NO_OPTION) {
                        String keyInput = JOptionPane.showInputDialog(this, "Nhập mã khóa:");
                        if (keyInput != null && !keyInput.isEmpty()) {
                            dataToSend = XORUtils.xorEncrypt(fileData, keyInput.charAt(0));
                            isEncrypted = true;
                        }
                    }
                } else {
                    return;
                }

                String selectedUserName = (String) userComboBox.getSelectedItem();
                String targetUserId = displayNameToId.get(selectedUserName);
                if (targetUserId != null && !targetUserId.equals(currentUser)) {
                    Message msg = new Message(msgType, currentUser, role, null, dataToSend, fileName, isEncrypted);
                    msg.setGroupId(targetUserId); // Use groupId for 1-1 messaging
                    conversations.computeIfAbsent(targetUserId, k -> new ArrayList<>()).add(msg);
                    conversationNames.putIfAbsent(targetUserId, selectedUserName);
                    if (!conversationListModel.contains(selectedUserName)) {
                        conversationListModel.addElement(selectedUserName);
                    }
                    displayConversation(targetUserId);
                    chatClient.sendMessage(msg);
                } else {
                    JOptionPane.showMessageDialog(this, "Không thể tự nhắn với chính mình!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Lỗi khi gửi media: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void chooseAndSendIcon() {
        if (userComboBox.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người nhận!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String[] emojiIcons = {"😀", "😁", "😂", "😃", "😄", "😅", "😉", "😊", "😎", "😍"};
        JPopupMenu popup = new JPopupMenu();
        JPanel panel = new JPanel(new GridLayout(2, 5));
        for (String icon : emojiIcons) {
            JButton btn = new JButton(icon);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
            btn.addActionListener(e -> {
                String selectedUserName = (String) userComboBox.getSelectedItem();
                String targetUserId = displayNameToId.get(selectedUserName);
                if (targetUserId != null && !targetUserId.equals(currentUser)) {
                    Message msg = new Message(Message.Type.ICON, currentUser, role, icon);
                    msg.setGroupId(targetUserId);
                    conversations.computeIfAbsent(targetUserId, k -> new ArrayList<>()).add(msg);
                    conversationNames.putIfAbsent(targetUserId, selectedUserName);
                    if (!conversationListModel.contains(selectedUserName)) {
                        conversationListModel.addElement(selectedUserName);
                    }
                    displayConversation(targetUserId);
                    chatClient.sendMessage(msg);
                } else {
                    JOptionPane.showMessageDialog(this, "Không thể tự nhắn với chính mình!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
                popup.setVisible(false);
            });
            panel.add(btn);
        }
        popup.add(panel);
        popup.show(iconBtn, -100, -popup.getPreferredSize().height);
    }

    private void createGroup() {
        if (!role.equals("admin")) {
            JOptionPane.showMessageDialog(this, "Chỉ admin có thể tạo nhóm!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<String> selectedUsers = new ArrayList<>();
        for (String name : displayNameToId.keySet()) {
            if (!name.equals(currentUser)) {
                selectedUsers.add(displayNameToId.get(name));
            }
        }
        JTextField groupNameField = new JTextField(20);
        JPanel panel = new JPanel(new GridLayout(1, 0));
        panel.add(new JLabel("Tên nhóm:"));
        panel.add(groupNameField);
        if (JOptionPane.showConfirmDialog(this, panel, "Tạo nhóm", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            String groupName = groupNameField.getText().trim();
            if (groupName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Tên nhóm không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String groupId = "group_" + System.currentTimeMillis();
            String groupInfo = groupName + "," + String.join(",", selectedUsers);
            Message msg = new Message(Message.Type.CREATE_GROUP, currentUser, role, groupInfo);
            msg.setGroupId(groupId);
            conversations.putIfAbsent(groupId, new ArrayList<>());
            conversationNames.put(groupId, groupName);
            displayNameToId.put(groupName, groupId);
            conversationListModel.addElement(groupName);
            chatClient.sendMessage(msg);
            JOptionPane.showMessageDialog(this, "Nhóm '" + groupName + "' đã được tạo!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void displayConversation(String conversationId) {
        if (conversationId == null) {
            chatPane.setText("");
            return;
        }
        chatPane.setText("");
        currentConversation = conversationId;
        List<Message> messages = conversations.getOrDefault(conversationId, new ArrayList<>());
        for (Message msg : messages) {
            if (msg.getContent() != null && (msg.getContent().startsWith("HiddenMessage:") || msg.getContent().startsWith("HiddenPassword:"))) {
                continue;
            }
            String senderLabel = msg.getSender().equals(currentUser) ? "Bạn" : conversationNames.getOrDefault(msg.getSender(), msg.getSender());
            String time = msg.getTimestamp() != null ? msg.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String conversationPrefix = conversationId.startsWith("group_") ? "[" + conversationNames.getOrDefault(conversationId, conversationId) + "] " : "";
            String styledHtml;

            switch (msg.getType()) {
                case GROUP_MESSAGE:
                    Message.Type originalType = msg.getOriginalType() != null ? msg.getOriginalType() : Message.Type.TEXT;
                    switch (originalType) {
                        case AUDIO:
                            String audioId = "audio_" + System.currentTimeMillis();
                            String stopId = "stop_audio_" + System.currentTimeMillis();
                            encryptedImageMap.put(audioId, msg.getData());
                            if (msg.isEncrypted()) {
                                String unlockAudioId = "unlock_audio_" + System.currentTimeMillis();
                                encryptedImageMap.put(unlockAudioId, msg.getData());
                                styledHtml = "<div>" + conversationPrefix +
                                        "[" + time + "] <b>" + senderLabel + " đã gửi âm thanh riêng tư 🔒:</b> " +
                                        "<a href=\"" + unlockAudioId + "\">Mở âm thanh</a></div>";
                            } else {
                                styledHtml = "<div>" + conversationPrefix +
                                        "[" + time + "] <b>" + senderLabel + ":</b> đã gửi âm thanh " +
                                        "<a href=\"" + audioId + "\">▶ Phát</a> | <a href=\"" + stopId + "\">⏹ Dừng</a></div>";
                            }
                            insertHtmlToPane(styledHtml);
                            break;
                        case TEXT:
                            String content = msg.getContent();
                            String decrypted = decryptContent(content, msg.isEncrypted());
                            styledHtml = "<div>" + conversationPrefix +
                                    "[" + time + "] <b>" + senderLabel + ":</b> " + (decrypted != null ? decrypted : "[Nội dung trống]") + "</div>";
                            insertHtmlToPane(styledHtml);
                            break;
                        case ICON:
                            styledHtml = "<div>" + conversationPrefix +
                                    "[" + time + "] <b>" + senderLabel + ":</b> " + msg.getContent() + "</div>";
                            insertHtmlToPane(styledHtml);
                            break;
                        case IMAGE:
                            String imageId = "image_" + System.currentTimeMillis();
                            String unlockImageId = "unlock_image_" + System.currentTimeMillis();
                            encryptedImageMap.put(imageId, msg.getData());
                            if (msg.isEncrypted()) {
                                String messageId = msg.getHiddenMessageId();
                                String passwordId = msg.getHiddenPasswordId();
                                if (messageId != null && passwordId != null) {
                                    encryptedImageMap.put(unlockImageId + "_message", messageId.getBytes());
                                    encryptedImageMap.put(unlockImageId + "_password", passwordId.getBytes());
                                    styledHtml = "<div>" + conversationPrefix +
                                            "[" + time + "] <b>" + senderLabel + " đã gửi ảnh có thông điệp 📜</b> " +
                                            "<a href=\"" + unlockImageId + "\">Mở ảnh</a></div>";
                                } else {
                                    encryptedImageMap.put(unlockImageId, msg.getData());
                                    styledHtml = "<div>" + conversationPrefix +
                                            "[" + time + "] <b>" + senderLabel + " đã gửi ảnh riêng tư 🔒</b> " +
                                            "<a href=\"" + unlockImageId + "\">Mở ảnh</a></div>";
                                }
                                insertHtmlToPane(styledHtml);
                            } else {
                                showImageInPane(senderLabel, msg.getData(), true);
                            }
                            break;
                        case FILE:
                            String fileId = "file_" + System.currentTimeMillis();
                            encryptedImageMap.put(fileId, msg.getData());
                            if (msg.isEncrypted()) {
                                String unlockFileId = "unlock_file_" + System.currentTimeMillis();
                                encryptedImageMap.put(unlockFileId, msg.getData());
                                styledHtml = "<div>" + conversationPrefix +
                                        "[" + time + "] <b>" + senderLabel + " đã gửi file riêng tư 🔒:</b> " + msg.getFileName() + " <a href=\"" + unlockFileId + "\">Mở file</a></div>";
                            } else {
                                styledHtml = "<div>" + conversationPrefix +
                                        "[" + time + "] <b>" + senderLabel + ":</b> đã gửi file " + msg.getFileName() + " <a href=\"" + fileId + "\">Tải xuống</a></div>";
                            }
                            insertHtmlToPane(styledHtml);
                            break;
                    }
                    break;
                case AUDIO:
                    String audioId = "audio_" + System.currentTimeMillis();
                    String stopId = "stop_audio_" + System.currentTimeMillis();
                    encryptedImageMap.put(audioId, msg.getData());
                    if (msg.isEncrypted()) {
                        String unlockAudioId = "unlock_audio_" + System.currentTimeMillis();
                        encryptedImageMap.put(unlockAudioId, msg.getData());
                        styledHtml = "<div>" + conversationPrefix +
                                "[" + time + "] <b>" + senderLabel + " đã gửi âm thanh riêng tư 🔒:</b> " +
                                "<a href=\"" + unlockAudioId + "\">Mở âm thanh</a></div>";
                    } else {
                        styledHtml = "<div>" + conversationPrefix +
                                "[" + time + "] <b>" + senderLabel + ":</b> đã gửi âm thanh " +
                                "<a href=\"" + audioId + "\">▶ Phát</a> | <a href=\"" + stopId + "\">⏹ Dừng</a></div>";
                    }
                    insertHtmlToPane(styledHtml);
                    break;
                case TEXT:
                    String content = msg.getContent();
                    String decrypted = decryptContent(content, msg.isEncrypted());
                    styledHtml = "<div>" + conversationPrefix +
                            "[" + time + "] <b>" + senderLabel + ":</b> " + (decrypted != null ? decrypted : "[Nội dung trống]") + "</div>";
                    insertHtmlToPane(styledHtml);
                    break;
                case ICON:
                    styledHtml = "<div>" + conversationPrefix +
                            "[" + time + "] <b>" + senderLabel + ":</b> " + msg.getContent() + "</div>";
                    insertHtmlToPane(styledHtml);
                    break;
                case IMAGE:
                    String imageId = "image_" + System.currentTimeMillis();
                    String unlockImageId = "unlock_image_" + System.currentTimeMillis();
                    encryptedImageMap.put(imageId, msg.getData());
                    if (msg.isEncrypted()) {
                        String messageId = msg.getHiddenMessageId();
                        String passwordId = msg.getHiddenPasswordId();
                        if (messageId != null && passwordId != null) {
                            encryptedImageMap.put(unlockImageId + "_message", messageId.getBytes());
                            encryptedImageMap.put(unlockImageId + "_password", passwordId.getBytes());
                            styledHtml = "<div>" + conversationPrefix +
                                    "[" + time + "] <b>" + senderLabel + " đã gửi ảnh có thông điệp 📜</b> " +
                                    "<a href=\"" + unlockImageId + "\">Mở ảnh</a></div>";
                        } else {
                            encryptedImageMap.put(unlockImageId, msg.getData());
                            styledHtml = "<div>" + conversationPrefix +
                                    "[" + time + "] <b>" + senderLabel + " đã gửi ảnh riêng tư 🔒</b> " +
                                    "<a href=\"" + unlockImageId + "\">Mở ảnh</a></div>";
                        }
                        insertHtmlToPane(styledHtml);
                    } else {
                        showImageInPane(senderLabel, msg.getData(), true);
                    }
                    break;
                case FILE:
                    String fileId = "file_" + System.currentTimeMillis();
                    encryptedImageMap.put(fileId, msg.getData());
                    if (msg.isEncrypted()) {
                        String unlockFileId = "unlock_file_" + System.currentTimeMillis();
                        encryptedImageMap.put(unlockFileId, msg.getData());
                        styledHtml = "<div>" + conversationPrefix +
                                "[" + time + "] <b>" + senderLabel + " đã gửi file riêng tư 🔒:</b> " + msg.getFileName() + " <a href=\"" + unlockFileId + "\">Mở file</a></div>";
                    } else {
                        styledHtml = "<div>" + conversationPrefix +
                                "[" + time + "] <b>" + senderLabel + ":</b> đã gửi file " + msg.getFileName() + " <a href=\"" + fileId + "\">Tải xuống</a></div>";
                    }
                    insertHtmlToPane(styledHtml);
                    break;
            }
        }
    }

    private String decryptContent(String content, boolean isEncrypted) {
        if (content == null) return null;
        if (isEncrypted) {
            try {
                String decrypted = XORUtils.xorEncrypt(content, XOR_KEY);
                System.out.println("Decrypted for " + currentUser + ": " + decrypted);
                return decrypted;
            } catch (Exception e) {
                System.out.println("Decryption failed for " + currentUser + ": " + e.getMessage());
                return content; // Trả về nguyên bản nếu giải mã thất bại
            }
        }
        System.out.println("No decryption needed for " + currentUser + ": " + content);
        return content;
    }

    private void handleMessage(Message msg) {
        String conversationId;
        String conversationName;

        if (msg.getType() == Message.Type.CREATE_GROUP) {
            String groupInfo = msg.getContent();
            String[] parts = groupInfo.split(",");
            if (parts.length < 2) return;

            String groupName = parts[0];
            String[] members = Arrays.copyOfRange(parts, 1, parts.length);
            if (!Arrays.asList(members).contains(currentUser)) return;

            conversationId = msg.getGroupId();
            if (conversationNames.containsKey(conversationId)) return;

            conversationName = groupName;
            conversations.putIfAbsent(conversationId, new ArrayList<>());
            conversationNames.put(conversationId, conversationName);
            displayNameToId.put(conversationName, conversationId);
            if (!conversationListModel.contains(conversationName)) {
                conversationListModel.addElement(conversationName);
            }
            conversationList.setSelectedValue(conversationName, true);
            return;
        }

        if (msg.getType() == Message.Type.TEXT && msg.getContent() != null && msg.getContent().startsWith("USER_LIST")) {
            String[] users = msg.getContent().substring(10).split(",");
            updateUserList(users);
            return;
        }

        if (msg.getType() == Message.Type.GROUP_MESSAGE || (msg.getGroupId() != null && msg.getGroupId().startsWith("group_"))) {
            conversationId = msg.getGroupId();
            conversationName = conversationNames.getOrDefault(conversationId, conversationId);
        } else {
            String content = msg.getContent();
            String otherUser;
            if (content != null && content.startsWith("PRIVATE")) {
                otherUser = msg.getSender();
            } else if (content != null && content.startsWith("@")) {
                int colonIndex = content.indexOf(':');
                if (colonIndex > 1) {
                    otherUser = content.substring(1, colonIndex).trim();
                } else {
                    otherUser = msg.getSender();
                }
            } else {
                otherUser = msg.getSender();
            }
            TaiKhoan tk = taiKhoanBUS.getTaiKhoanByMaNV(otherUser);
            conversationId = tk != null ? String.valueOf(tk.getMaNhanVien()) : otherUser;
            conversationName = tk != null ? tk.getTen() : otherUser;
        }

        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(msg);
        conversationNames.putIfAbsent(conversationId, conversationName);
        displayNameToId.putIfAbsent(conversationName, conversationId);
        if (!conversationListModel.contains(conversationName)) {
            conversationListModel.addElement(conversationName);
        }

        if (currentConversation != null && currentConversation.equals(conversationId)) {
            displayConversation(conversationId);
        }
    }

    private void checkAndUpdatePendingMessages() {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Message> entry : pendingMessages.entrySet()) {
            String unlockImageId = entry.getKey();
            Message msg = entry.getValue();
            String messageId = new String(encryptedImageMap.get(unlockImageId + "_message"));
            String passwordId = new String(encryptedImageMap.get(unlockImageId + "_password"));
            byte[] storedMessage = encryptedImageMap.get(messageId);
            byte[] storedPassword = encryptedImageMap.get(passwordId);
            if (storedMessage != null && storedPassword != null) {
                String senderLabel = msg.getSender().equals(currentUser) ? "Bạn" : conversationNames.get(currentConversation);
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String conversationPrefix = currentConversation.startsWith("group_") ? "[" + conversationNames.get(currentConversation) + "] " : "";
                String styledHtml = "<div style='margin:2px 0; line-height:1.2;'>" + conversationPrefix +
                        "[" + time + "] <b>" + senderLabel + " đã gửi một ảnh có chứa thông điệp 📜</b> " +
                        "<a href=\"" + unlockImageId + "\">Mở ảnh</a></div>";
                insertHtmlToPane(styledHtml);
                toRemove.add(unlockImageId);
            }
        }
        for (String id : toRemove) {
            pendingMessages.remove(id);
        }
    }

    private void handleUnlockImage(String id) {
        byte[] imgData = encryptedImageMap.get(id);
        if (imgData == null) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy dữ liệu ảnh!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String passwordInput = JOptionPane.showInputDialog(this, "Nhập mật khẩu để mở ảnh:");
        if (passwordInput == null || passwordInput.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Mật khẩu không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            String hiddenMessage = Steganography.extractMessage(imgData, passwordInput);
            if (hiddenMessage != null && !hiddenMessage.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Thông điệp: " + hiddenMessage, "Thông điệp giấu tin", JOptionPane.INFORMATION_MESSAGE);
                showImageInPane("", imgData, true); // Hiển thị ảnh gốc đã giấu tin
            } else {
                JOptionPane.showMessageDialog(this, "Mật khẩu không đúng hoặc không có thông điệp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Lỗi khi trích xuất thông điệp: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleUnlockFile(String id) {
        byte[] encryptedData = encryptedImageMap.get(id);
        if (encryptedData == null) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy dữ liệu file!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String keyInput = JOptionPane.showInputDialog(this, "Nhập mã để mở file:");
        if (keyInput != null && !keyInput.isEmpty()) {
            try {
                byte[] decrypted = XORUtils.xorEncrypt(encryptedData, keyInput.charAt(0));
                saveFile(decrypted, "decrypted_" + id.replace("unlock_file_", ""));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Giải mã thất bại: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleUnlockAudio(String id) {
        byte[] encryptedData = encryptedImageMap.get(id);
        if (encryptedData == null) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy dữ liệu âm thanh!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String keyInput = JOptionPane.showInputDialog(this, "Nhập mã để mở âm thanh:");
        if (keyInput != null && !keyInput.isEmpty()) {
            try {
                byte[] decrypted = XORUtils.xorEncrypt(encryptedData, keyInput.charAt(0));
                playAudio(decrypted);
                String audioId = "audio_" + System.currentTimeMillis();
                String stopId = "stop_audio_" + System.currentTimeMillis();
                encryptedImageMap.put(audioId, decrypted);
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String conversationPrefix = currentConversation != null && currentConversation.startsWith("group_") ? "[" + conversationNames.get(currentConversation) + "] " : "";
                String styledHtml = "<div style='margin:2px 0; line-height:1.2;'>" + conversationPrefix +
                        "[" + time + "] <b>Đang phát âm thanh đã giải mã:</b> " +
                        "<a href=\"" + audioId + "\">▶ Phát</a> | <a href=\"" + stopId + "\">⏹ Dừng</a></div>";
                insertHtmlToPane(styledHtml);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Giải mã thất bại: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveFile(String id) {
        byte[] data = encryptedImageMap.get(id);
        if (data == null) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy dữ liệu!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(chooser.getSelectedFile().toPath(), data);
                JOptionPane.showMessageDialog(this, "Lưu thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveFile(byte[] data, String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(chooser.getSelectedFile().toPath(), data);
                JOptionPane.showMessageDialog(this, "Lưu thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showImageInPane(String senderLabel, byte[] imgBytes, boolean addSaveLink) {
        try {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String imageId = "image_" + System.currentTimeMillis();
            String saveId = "save_image_" + System.currentTimeMillis();
            encryptedImageMap.put(imageId, imgBytes);
            encryptedImageMap.put(saveId, imgBytes);

            File tempFile = File.createTempFile("tempImage_" + System.currentTimeMillis(), ".png");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(imgBytes);
            }

            String imagePath = tempFile.toURI().toURL().toString();
            String saveLink = addSaveLink ? " <a href='" + saveId + "'>Lưu</a>" : "";
            String conversationPrefix = currentConversation != null && currentConversation.startsWith("group_") ? "[" + conversationNames.get(currentConversation) + "] " : "";

            String styledHtml = "<div style='margin:2px 0; line-height:1.2;'>" + conversationPrefix + "[" + time + "] <b>" + senderLabel + " đã gửi ảnh:</b><br>"
                    + "<img src='" + imagePath + "' width='150' id='" + imageId + "'/>" + saveLink + "</div>";
            insertHtmlToPane(styledHtml);
            tempFile.deleteOnExit();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi khi hiển thị ảnh: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void insertHtmlToPane(String html) {
        try {
            HTMLDocument doc = (HTMLDocument) chatPane.getDocument();
            HTMLEditorKit kit = (HTMLEditorKit) chatPane.getEditorKit();
            kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
            chatPane.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi khi hiển thị nội dung: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void playAudio(byte[] audioData) {
        try {
            if (audioData == null || audioData.length < 44) {
                JOptionPane.showMessageDialog(this, "Dữ liệu âm thanh không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            stopAudio();
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bais);
            currentClip = (Clip) AudioSystem.getLine(new DataLine.Info(Clip.class, audioInputStream.getFormat()));
            currentClip.open(audioInputStream);
            currentClip.start();
            audioInputStream.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi phát âm thanh: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopAudio() {
        if (currentClip != null) {
            currentClip.stop();
            currentClip.close();
            currentClip = null;
        }
    }

    private void startRecording() {
        try {
            audioFormat = new AudioFormat(44100.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                JOptionPane.showMessageDialog(this, "Thiết bị ghi âm không được hỗ trợ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            recordingThread = new Thread(() -> {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[targetDataLine.getBufferSize() / 5];
                while (isRecording) {
                    int numBytesRead = targetDataLine.read(buffer, 0, buffer.length);
                    if (numBytesRead > 0) {
                        out.write(buffer, 0, numBytesRead);
                    }
                }
                targetDataLine.stop();
                if (targetDataLine != null) {
                    targetDataLine.close();
                    targetDataLine = null;
                }
                byte[] rawData = out.toByteArray();
                byte[] wavData = convertToWav(rawData);
                if (wavData != null) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            sendRecordedAudio(wavData);
                        } catch (IOException e) {
                            JOptionPane.showMessageDialog(ChatGUI.this, "Lỗi gửi âm thanh: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            });
            recordingThread.start();
            isRecording = true;
            recordBtn.setText("Dừng ghi âm");
        } catch (Exception e) {
            isRecording = false;
            JOptionPane.showMessageDialog(this, "Lỗi khi khởi động ghi âm: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopRecording() {
        isRecording = false;
        if (targetDataLine != null) {
            targetDataLine.stop();
            targetDataLine.close();
            targetDataLine = null;
        }
        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            recordingThread = null;
        }
        recordBtn.setText("Ghi âm");
    }

    private void sendRecordedAudio(byte[] audioData) throws IOException {
        if (userComboBox.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người nhận!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this, "Gửi âm thanh công khai?", "Gửi audio", JOptionPane.YES_NO_OPTION);
        boolean isEncrypted = false;
        byte[] dataToSend = audioData;
        if (choice == JOptionPane.NO_OPTION) {
            String keyInput = JOptionPane.showInputDialog(this, "Nhập mã khóa:");
            if (keyInput != null && !keyInput.isEmpty()) {
                dataToSend = XORUtils.xorEncrypt(audioData, keyInput.charAt(0));
                isEncrypted = true;
            }
        }
        String selectedUserName = (String) userComboBox.getSelectedItem();
        String targetUserId = displayNameToId.get(selectedUserName);
        if (targetUserId != null && !targetUserId.equals(currentUser)) {
            Message msg = new Message(Message.Type.AUDIO, currentUser, role, null, dataToSend, "voice_" + System.currentTimeMillis() + ".wav", isEncrypted);
            msg.setGroupId(targetUserId);
            conversations.computeIfAbsent(targetUserId, k -> new ArrayList<>()).add(msg);
            conversationNames.putIfAbsent(targetUserId, selectedUserName);
            if (!conversationListModel.contains(selectedUserName)) {
                conversationListModel.addElement(selectedUserName);
            }
            displayConversation(targetUserId);
            chatClient.sendMessage(msg);
        } else {
            JOptionPane.showMessageDialog(this, "Không thể tự nhắn với chính mình!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private byte[] convertToWav(byte[] rawData) {
        ByteArrayOutputStream wavStream = new ByteArrayOutputStream();
        try {
            int sampleRate = 44100;
            int sampleSizeInBits = 16;
            int channels = 1;
            int frameSize = channels * (sampleSizeInBits / 8);
            int frameRate = sampleRate;
            int byteRate = frameRate * frameSize;
            int dataSize = rawData.length;
            int chunkSize = dataSize + 36;

            wavStream.write("RIFF".getBytes());
            writeInt(wavStream, chunkSize);
            wavStream.write("WAVE".getBytes());
            wavStream.write("fmt ".getBytes());
            writeInt(wavStream, 16);
            writeShort(wavStream, 1);
            writeShort(wavStream, channels);
            writeInt(wavStream, sampleRate);
            writeInt(wavStream, byteRate);
            writeShort(wavStream, frameSize);
            writeShort(wavStream, sampleSizeInBits);
            wavStream.write("data".getBytes());
            writeInt(wavStream, dataSize);
            wavStream.write(rawData);

            return wavStream.toByteArray();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Lỗi chuyển đổi WAV: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void writeInt(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private void writeShort(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private void updateUserList(String[] users) {
        Set<String> currentItems = new HashSet<>();
        for (int i = 0; i < userComboBox.getItemCount(); i++) {
            currentItems.add(userComboBox.getItemAt(i));
        }

        for (String user : users) {
            if (!user.equals(currentUser) && !user.isEmpty()) {
                TaiKhoan tk = taiKhoanBUS.getTaiKhoanByMaNV(user);
                String fullName = tk != null ? tk.getTen() : user;
                if (tk != null && !currentItems.contains(fullName)) {
                    userComboBox.addItem(fullName);
                    displayNameToId.putIfAbsent(fullName, user);
                    conversationNames.putIfAbsent(user, fullName);
                    conversations.putIfAbsent(user, new ArrayList<>());
                    currentItems.add(fullName);
                }
            }
        }

        if (userComboBox.getItemCount() > 0 && currentConversation == null) {
            userComboBox.setSelectedIndex(0);
            currentConversation = displayNameToId.get(userComboBox.getSelectedItem());
            displayConversation(currentConversation);
        }
    }

    // Helper method to check if an item exists in JComboBox
    private boolean containsItem(JComboBox<String> comboBox, String item) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            if (comboBox.getItemAt(i).equals(item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void dispose() {
        chatClient.disconnect();
        super.dispose();
    }
}