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

import javax.imageio.ImageIO;
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
import com.github.sarxos.webcam.Webcam;
import java.awt.image.BufferedImage;

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
    
    private volatile boolean isRunning = true;

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

        TaiKhoan currentAccount = taiKhoanBUS.getTaiKhoanByMaNV(username);
        String displayName = currentAccount != null && currentAccount.getTen() != null ? currentAccount.getTen() : username;
        String userRole = currentAccount != null ? currentAccount.getQuyen() : role;
        setTitle("Chat - " + displayName + " (" + userRole + ")");
        setSize(600, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Đóng cửa sổ sẽ gọi dispose()
        setLocationRelativeTo(null);
        buildUI();

        // Khởi động luồng nhận tin nhắn
        new Thread(() -> {
            while (isRunning) { // Sử dụng flag để kiểm soát
                try {
                    Message msg = chatClient.receiveMessage();
                    if (msg != null) {
                        System.out.println("Nhận tin nhắn: " + (msg.getContent() != null ? msg.getContent() : "[Media]") + " từ " + msg.getSender());
                        SwingUtilities.invokeLater(() -> handleMessage(msg));
                    }
                } catch (Exception e) {
                    System.out.println("Lỗi nhận tin nhắn: " + e.getMessage());
                    break; // Thoát luồng nếu có lỗi
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

        // Thêm CSS vào chatPane
        HTMLDocument doc = (HTMLDocument) chatPane.getDocument();
        HTMLEditorKit kit = (HTMLEditorKit) chatPane.getEditorKit();
        try {
            kit.insertHTML(doc, doc.getLength(), "<style>" +
                    ".message-left { text-align: left; margin: 5px; padding: 5px; background-color: #e0e0e0; border-radius: 5px; display: inline-block; }" +
                    ".message-right { text-align: right; margin: 5px; padding: 5px; background-color: #DCF8C6; border-radius: 5px; display: inline-block; }" +
                    ".time { font-size: 0.8em; color: #888; }" +
                    "</style>", 0, 0, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JScrollPane chatScrollPane = new JScrollPane(chatPane);
        mainPanel.add(chatScrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.addActionListener(e -> sendTextMessage());
        sendBtn = new JButton("➤"); // Send icon
        mediaBtn = new JButton("📂"); // Media icon
        iconBtn = new JButton("😀"); // Icon/Emoji icon
        createGroupBtn = new JButton("📸"); // Camera icon
        createGroupBtn.setToolTipText("Chụp ảnh từ webcam");
        recordBtn = new JButton("🎙️"); // Record icon

        sendBtn.addActionListener(e -> sendTextMessage());
        mediaBtn.addActionListener(e -> sendMediaMessage());
        iconBtn.addActionListener(e -> chooseAndSendIcon());
        createGroupBtn.addActionListener(e -> captureAndSendPhoto());
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
        Set<String> uniqueDisplayNames = new HashSet<>();
        for (String userId : conversations.keySet()) {
            if (!userId.equals(currentUser)) {
                TaiKhoan tk = taiKhoanBUS.getTaiKhoanByMaNV(userId);
                if (tk != null) {
                    String fullName = tk.getTen() != null ? tk.getTen() : tk.getTenDangNhap();
                    String userQuyen = tk.getQuyen();
                    String displayName = fullName + " (" + userQuyen + ")";
                    if (!uniqueDisplayNames.contains(displayName)) {
                        conversationListModel.addElement(displayName);
                        displayNameToId.putIfAbsent(displayName, userId);
                        uniqueDisplayNames.add(displayName);
                    }
                }
            }
        }
        // Load from chat_history.txt
        try (BufferedReader reader = new BufferedReader(new FileReader("chat_history.txt"))) {
            String line;
            Set<String> users = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                if (line.contains(" -> ")) {
                    String[] parts = line.split(" -> ");
                    if (parts.length >= 2) {
                        String sender = parts[0].trim();
                        String receiver = parts[1].split(":")[0].trim();
                        String target = sender.equals(currentUser) ? receiver : sender;
                        if (!target.equals(currentUser) && !users.contains(target)) {
                            TaiKhoan tk = taiKhoanBUS.getTaiKhoanByMaNV(target);
                            if (tk != null) {
                                String fullName = tk.getTen() != null ? tk.getTen() : tk.getTenDangNhap();
                                String userQuyen = tk.getQuyen();
                                String displayName = fullName + " (" + userQuyen + ")";
                                if (!uniqueDisplayNames.contains(displayName)) {
                                    conversationListModel.addElement(displayName);
                                    displayNameToId.putIfAbsent(displayName, target);
                                    conversationNames.putIfAbsent(target, fullName);
                                    conversations.putIfAbsent(target, new ArrayList<>());
                                    uniqueDisplayNames.add(displayName);
                                }
                                users.add(target);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Lỗi đọc lịch sử chat: " + e.getMessage());
        }
        if (!conversationListModel.isEmpty()) {
            conversationList.setSelectedIndex(0);
            String selectedUserName = (String) conversationList.getSelectedValue();
            currentConversation = displayNameToId.get(selectedUserName);
            displayConversation(currentConversation);
        }
    }

    private void loadAvailableUsers() {
        try {
            List<TaiKhoan> users = taiKhoanBUS.getAllTaiKhoan();
            Set<String> uniqueDisplayNames = new HashSet<>(); // Sử dụng Set để tránh trùng lặp
            userComboBox.removeAllItems();
            for (TaiKhoan tk : users) {
                String userId = String.valueOf(tk.getMaNhanVien());
                if (!userId.equals(currentUser)) {
                    String fullName = tk.getTen() != null ? tk.getTen() : tk.getTenDangNhap();
                    String userQuyen = tk.getQuyen();
                    String displayName = fullName + " (" + userQuyen + ")";
                    if (!uniqueDisplayNames.contains(displayName)) {
                        userComboBox.addItem(displayName);
                        displayNameToId.put(displayName, userId);
                        conversationNames.putIfAbsent(userId, fullName);
                        conversations.putIfAbsent(userId, new ArrayList<>());
                        uniqueDisplayNames.add(displayName);
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
                    msg.setGroupId(targetUserId); // Định tuyến chính xác
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

    private void captureAndSendPhoto() {
        if (userComboBox.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người nhận!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Webcam webcam = Webcam.getDefault();
            if (webcam == null) {
                JOptionPane.showMessageDialog(this, "Không tìm thấy webcam! Vui lòng kiểm tra kết nối hoặc quyền truy cập.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Đặt kích thước mong muốn trước khi mở webcam
            Dimension[] supportedSizes = webcam.getViewSizes();
            Dimension preferredSize = supportedSizes.length > 0 ? supportedSizes[supportedSizes.length - 1] : new Dimension(640, 480); // Chọn kích thước lớn nhất hoặc mặc định 640x480
            webcam.setViewSize(preferredSize);

            webcam.open();

            // Tạo frame để hiển thị webcam
            JFrame webcamFrame = new JFrame("Webcam");
            webcamFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            JLabel webcamLabel = new JLabel();
            webcamFrame.add(webcamLabel);
            webcamFrame.setSize(preferredSize.width, preferredSize.height);
            webcamFrame.setLocationRelativeTo(null);
            webcamFrame.setVisible(true);

            // Tạo nút chụp ảnh
            JButton captureButton = new JButton("Chụp ảnh");
            webcamFrame.add(captureButton, BorderLayout.SOUTH);

            // Cập nhật hình ảnh từ webcam
            Thread webcamThread = new Thread(() -> {
                while (webcamFrame.isVisible()) {
                    BufferedImage image = webcam.getImage();
                    if (image != null) {
                        webcamLabel.setIcon(new ImageIcon(image));
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            webcamThread.start();

            captureButton.addActionListener(e -> {
                try {
                    BufferedImage image = webcam.getImage();
                    if (image == null) {
                        JOptionPane.showMessageDialog(this, "Không thể chụp ảnh!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", baos);
                    byte[] imageData = baos.toByteArray();

                    webcam.close();
                    webcamFrame.dispose();

                    // Hỏi người dùng xem có muốn mã hóa hoặc giấu tin
                    int choice = JOptionPane.showConfirmDialog(this, "Gửi ảnh công khai?", "Gửi ảnh", JOptionPane.YES_NO_OPTION);
                    boolean isEncrypted = false;
                    byte[] dataToSend = imageData;
                    String fileName = "webcam_photo_" + System.currentTimeMillis() + ".png";

                    if (choice == JOptionPane.NO_OPTION) {
                        Object[] options = {"Mã hóa ảnh", "Giấu tin trong ảnh"};
                        int stegoChoice = JOptionPane.showOptionDialog(this, "Mã hóa hay giấu tin?", "Chọn chế độ", 
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                        if (stegoChoice == 0) {
                            String keyInput = JOptionPane.showInputDialog(this, "Nhập mã khóa:");
                            if (keyInput != null && !keyInput.isEmpty()) {
                                dataToSend = XORUtils.xorEncrypt(imageData, keyInput.charAt(0));
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
                                    dataToSend = Steganography.hideMessage(imageData, hiddenMessage, password);
                                    isEncrypted = true;
                                    String imageId = "image_" + System.currentTimeMillis();
                                    encryptedImageMap.put(imageId, dataToSend);
                                } catch (IOException ex) {
                                    JOptionPane.showMessageDialog(this, "Lỗi khi giấu tin: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }
                            }
                        }
                    }

                    String selectedUserName = (String) userComboBox.getSelectedItem();
                    String targetUserId = displayNameToId.get(selectedUserName);
                    if (targetUserId != null && !targetUserId.equals(currentUser)) {
                        Message msg = new Message(Message.Type.IMAGE, currentUser, role, null, dataToSend, fileName, isEncrypted);
                        msg.setGroupId(targetUserId);
                        conversations.computeIfAbsent(targetUserId, k -> new ArrayList<>()).add(msg);
                        conversationNames.putIfAbsent(targetUserId, selectedUserName);
                        if (!conversationListModel.contains(selectedUserName)) {
                            conversationListModel.addElement(selectedUserName);
                        }
                        displayConversation(targetUserId);
                        chatClient.sendMessage(msg);
                        JOptionPane.showMessageDialog(this, "Ảnh đã được gửi thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Không thể tự nhắn với chính mình!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi khi chụp hoặc gửi ảnh: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            });

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khi mở webcam: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
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
            String conversationPrefix = "";
            String styledHtml;

            boolean isSentByMe = msg.getSender().equals(currentUser);
            String messageClass = isSentByMe ? "message-right" : "message-left";

            switch (msg.getType()) {
                case AUDIO:
                    String audioId = "audio_" + System.currentTimeMillis();
                    String stopId = "stop_audio_" + System.currentTimeMillis();
                    encryptedImageMap.put(audioId, msg.getData());
                    if (msg.isEncrypted()) {
                        String unlockAudioId = "unlock_audio_" + System.currentTimeMillis();
                        encryptedImageMap.put(unlockAudioId, msg.getData());
                        styledHtml = "<div class='" + messageClass + "'>" + conversationPrefix +
                                "<span class='time'>[" + time + "]</span> <b>" + senderLabel + " đã gửi âm thanh riêng tư 🔒:</b> " +
                                "<a href=\"" + unlockAudioId + "\">Mở âm thanh</a></div>";
                    } else {
                        styledHtml = "<div class='" + messageClass + "'>" + conversationPrefix +
                                "<span class='time'>[" + time + "]</span> <b>" + senderLabel + ":</b> đã gửi âm thanh " +
                                "<a href=\"" + audioId + "\">▶ Phát</a> | <a href=\"" + stopId + "\">⏹ Dừng</a></div>";
                    }
                    insertHtmlToPane(styledHtml);
                    break;
                case TEXT:
                    String content = msg.getContent();
                    String decrypted = decryptContent(content, msg.isEncrypted());
                    styledHtml = "<div class='" + messageClass + "'>" + conversationPrefix +
                            "<span class='time'>[" + time + "]</span> <b>" + senderLabel + ":</b> " + (decrypted != null ? decrypted : "[Nội dung trống]") + "</div>";
                    insertHtmlToPane(styledHtml);
                    break;
                case ICON:
                    styledHtml = "<div class='" + messageClass + "'>" + conversationPrefix +
                            "<span class='time'>[" + time + "]</span> <b>" + senderLabel + ":</b> " + msg.getContent() + "</div>";
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
                            styledHtml = "<div class='" + messageClass + "'>" + conversationPrefix +
                                    "<span class='time'>[" + time + "]</span> <b>" + senderLabel + " đã gửi ảnh có thông điệp 📜</b> " +
                                    "<a href=\"" + unlockImageId + "\">Mở ảnh</a></div>";
                        } else {
                            encryptedImageMap.put(unlockImageId, msg.getData());
                            styledHtml = "<div class='" + messageClass + "'>" + conversationPrefix +
                                    "<span class='time'>[" + time + "]</span> <b>" + senderLabel + " đã gửi ảnh riêng tư 🔒</b> " +
                                    "<a href=\"" + unlockImageId + "\">Mở ảnh</a></div>";
                        }
                        insertHtmlToPane(styledHtml);
                    } else {
                        showImageInPane(senderLabel, msg.getData(), true, isSentByMe);
                    }
                    break;
                case FILE:
                    String fileId = "file_" + System.currentTimeMillis();
                    encryptedImageMap.put(fileId, msg.getData());
                    if (msg.isEncrypted()) {
                        String unlockFileId = "unlock_file_" + System.currentTimeMillis();
                        encryptedImageMap.put(unlockFileId, msg.getData());
                        styledHtml = "<div class='" + messageClass + "'>" + conversationPrefix +
                                "<span class='time'>[" + time + "]</span> <b>" + senderLabel + " đã gửi file riêng tư 🔒:</b> " + msg.getFileName() + " <a href=\"" + unlockFileId + "\">Mở file</a></div>";
                    } else {
                        styledHtml = "<div class='" + messageClass + "'>" + conversationPrefix +
                                "<span class='time'>[" + time + "]</span> <b>" + senderLabel + ":</b> đã gửi file " + msg.getFileName() + " <a href=\"" + fileId + "\">Tải xuống</a></div>";
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

        if (msg.getType() == Message.Type.TEXT && msg.getContent() != null) {
            if (msg.getContent().startsWith("ERROR")) {
                JOptionPane.showMessageDialog(this, msg.getContent().substring(6), "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Bỏ qua tin nhắn SENT: để tránh hiển thị trùng lặp
            if (msg.getContent().startsWith("SENT:")) {
                return; // Không hiển thị tin nhắn echo
            }
        }

        if (msg.getSender().equals(currentUser) && msg.getContent() != null && msg.getContent().startsWith("SENT:")) {
            conversationId = msg.getGroupId();
            if (conversationId != null) {
                TaiKhoan tk = taiKhoanBUS.getTaiKhoanByMaNV(conversationId);
                conversationName = tk != null ? tk.getTen() + " (" + tk.getQuyen() + ")" : conversationId;
            } else {
                conversationId = currentUser;
                conversationName = "Bạn";
            }
        } else {
            String otherUser = msg.getSender();
            TaiKhoan tk = taiKhoanBUS.getTaiKhoanByMaNV(otherUser);
            conversationId = tk != null ? String.valueOf(tk.getMaNhanVien()) : otherUser;
            conversationName = tk != null ? tk.getTen() + " (" + tk.getQuyen() + ")" : otherUser;
        }

        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(msg);
        conversationNames.putIfAbsent(conversationId, conversationName);
        if (!displayNameToId.containsKey(conversationName)) {
            displayNameToId.put(conversationName, conversationId);
            if (!conversationListModel.contains(conversationName)) {
                conversationListModel.addElement(conversationName);
            }
        }

        if (currentConversation != null && (currentConversation.equals(conversationId) || 
                (msg.getContent() != null && msg.getContent().startsWith("SENT:") && currentConversation.equals(msg.getGroupId())))) {
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
                String conversationPrefix = currentConversation != null && currentConversation.startsWith("group_") ? "[" + conversationNames.get(currentConversation) + "] " : "";
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
            // Giải mã bằng XOR trước (nếu chỉ mã hóa XOR)
            byte[] decryptedData = XORUtils.xorEncrypt(imgData, passwordInput.charAt(0));
            
            // Kiểm tra xem ảnh đã được giải mã thành công bằng cách thử đọc nó
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(decryptedData));
            if (image != null) {
                // Nếu đọc thành công, hiển thị ảnh
                showImageInPane("", decryptedData, true, false);
                JOptionPane.showMessageDialog(this, "Ảnh đã được giải mã và hiển thị!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Nếu không phải chỉ mã hóa XOR, thử trích xuất thông điệp giấu tin
            String hiddenMessage = Steganography.extractMessage(imgData, passwordInput);
            if (hiddenMessage != null && !hiddenMessage.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Thông điệp: " + hiddenMessage, "Thông điệp giấu tin", JOptionPane.INFORMATION_MESSAGE);
                showImageInPane("", imgData, true, false); // Hiển thị ảnh gốc (vẫn mã hóa nếu không giải mã đúng)
            } else {
                JOptionPane.showMessageDialog(this, "Mật khẩu không đúng hoặc không có thông điệp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Lỗi khi trích xuất hoặc giải mã: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
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

    private void showImageInPane(String senderLabel, byte[] imgBytes, boolean addSaveLink, boolean isSentByMe) {
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
            String messageClass = isSentByMe ? "message-right" : "message-left";

            String styledHtml = "<div class='" + messageClass + "' style='margin:2px 0; line-height:1.2;'>" + conversationPrefix +
                    "<span class='time'>[" + time + "]</span> <b>" + senderLabel + " đã gửi ảnh:</b><br>" +
                    "<img src='" + imagePath + "' width='150' id='" + imageId + "'/>" + saveLink + "</div>";
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
            recordBtn.setText("⏹️"); // Stop recording icon
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
        recordBtn.setText("🎙️"); // Record icon
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
        isRunning = false; // Dừng luồng nhận tin nhắn
        chatClient.disconnect(); // Đóng kết nối client
        super.dispose(); // Đóng cửa sổ
    }
}