package com.vanky.im.testclient.ui;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.testclient.client.HttpClient;

import com.vanky.im.testclient.client.RealWebSocketClient;
import com.vanky.im.testclient.client.NettyTcpClient;
import com.vanky.im.testclient.storage.LocalMessageStorage;
import com.vanky.im.testclient.sync.OfflineMessageSyncManager;


import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 用户窗口界面 - Swing版本
 */
public class UserWindow extends JFrame implements RealWebSocketClient.MessageHandler, NettyTcpClient.MessageHandler {
    
    private final String userId;
    private JTextArea messageArea;
    private JTextField targetUserInput;
    private JTextField groupIdInput;
    private JTextField privateMessageInput;
    private JTextField groupMessageInput;
    private JButton connectButton;
    private JButton disconnectButton;
    private JButton sendPrivateButton;
    private JButton sendGroupButton;
    private JButton registerButton;
    private JButton createGroupButton;
    private JLabel statusLabel;
    private JComboBox<String> protocolComboBox;
    
    private HttpClient httpClient;
    private RealWebSocketClient realWebSocketClient;
    private NettyTcpClient tcpClient;
    private ScheduledExecutorService heartbeatExecutor;

    // 离线消息同步相关
    private LocalMessageStorage localStorage;
    private OfflineMessageSyncManager syncManager;
    private JLabel syncStatusLabel;
    
    public UserWindow(String userId) {
        this.userId = userId;
        this.httpClient = new HttpClient();

        // 初始化离线消息同步组件
        this.localStorage = new LocalMessageStorage();
        this.syncManager = new OfflineMessageSyncManager(httpClient, localStorage, this);

        initUI();
    }
    
    private void initUI() {
        setTitle("IM测试客户端 - 用户: " + userId);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 顶部状态和连接控制面板
        JPanel topPanel = new JPanel(new BorderLayout());
        
        // 状态显示
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));

        statusLabel = new JLabel("状态: 未连接");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

        syncStatusLabel = new JLabel("同步: 未开始");
        syncStatusLabel.setForeground(Color.GRAY);
        syncStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));

        statusPanel.add(statusLabel);
        statusPanel.add(syncStatusLabel);
        
        // 协议选择和连接控制面板
        JPanel controlPanel = new JPanel(new FlowLayout());

        // 协议选择
        JLabel protocolLabel = new JLabel("协议:");
        String[] protocols = {"WebSocket", "TCP"};
        protocolComboBox = new JComboBox<>(protocols);
        protocolComboBox.setSelectedIndex(0); // 默认选择WebSocket

        // 连接控制按钮
        connectButton = new JButton("连接");
        disconnectButton = new JButton("断开");
        disconnectButton.setEnabled(false);

        // 新功能按钮
        registerButton = new JButton("用户注册");
        createGroupButton = new JButton("创建群聊");

        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect());
        registerButton.addActionListener(e -> showRegisterDialog());
        createGroupButton.addActionListener(e -> showCreateGroupDialog());

        controlPanel.add(protocolLabel);
        controlPanel.add(protocolComboBox);
        controlPanel.add(connectButton);
        controlPanel.add(disconnectButton);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(registerButton);
        controlPanel.add(createGroupButton);
        
        topPanel.add(statusPanel, BorderLayout.WEST);
        topPanel.add(controlPanel, BorderLayout.EAST);
        
        // 消息显示区域
        messageArea = new JTextArea(15, 50);
        messageArea.setEditable(false);
        messageArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        messageScrollPane.setBorder(BorderFactory.createTitledBorder("消息记录"));
        messageScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        // 底部输入面板
        JPanel bottomPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        // 私聊面板
        JPanel privatePanel = new JPanel(new BorderLayout(5, 5));
        privatePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "私聊消息", 
                TitledBorder.LEFT, TitledBorder.TOP));
        
        JPanel privateInputPanel = new JPanel(new BorderLayout(5, 5));
        targetUserInput = new JTextField("user2", 8);
        targetUserInput.setBorder(BorderFactory.createTitledBorder("目标用户"));
        
        privateMessageInput = new JTextField();
        privateMessageInput.setBorder(BorderFactory.createTitledBorder("消息内容"));
        privateMessageInput.addActionListener(e -> {
            if (sendPrivateButton.isEnabled()) {
                sendPrivateMessage();
            }
        });
        
        sendPrivateButton = new JButton("发送私聊");
        sendPrivateButton.setEnabled(false);
        sendPrivateButton.addActionListener(e -> sendPrivateMessage());
        
        privateInputPanel.add(targetUserInput, BorderLayout.WEST);
        privateInputPanel.add(privateMessageInput, BorderLayout.CENTER);
        privateInputPanel.add(sendPrivateButton, BorderLayout.EAST);
        privatePanel.add(privateInputPanel, BorderLayout.CENTER);
        
        // 群聊面板
        JPanel groupPanel = new JPanel(new BorderLayout(5, 5));
        groupPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.BLUE), "群聊消息", 
                TitledBorder.LEFT, TitledBorder.TOP));
        
        JPanel groupInputPanel = new JPanel(new BorderLayout(5, 5));
        groupIdInput = new JTextField("22486444098588672", 8);
        groupIdInput.setBorder(BorderFactory.createTitledBorder("群组ID"));
        
        groupMessageInput = new JTextField();
        groupMessageInput.setBorder(BorderFactory.createTitledBorder("消息内容"));
        groupMessageInput.addActionListener(e -> {
            if (sendGroupButton.isEnabled()) {
                sendGroupMessage();
            }
        });
        
        sendGroupButton = new JButton("发送群聊");
        sendGroupButton.setEnabled(false);
        sendGroupButton.addActionListener(e -> sendGroupMessage());
        
        groupInputPanel.add(groupIdInput, BorderLayout.WEST);
        groupInputPanel.add(groupMessageInput, BorderLayout.CENTER);
        groupInputPanel.add(sendGroupButton, BorderLayout.EAST);
        groupPanel.add(groupInputPanel, BorderLayout.CENTER);
        
        bottomPanel.add(privatePanel);
        bottomPanel.add(groupPanel);
        
        // 组装主面板
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(messageScrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // 窗口关闭事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                if (heartbeatExecutor != null) {
                    heartbeatExecutor.shutdown();
                }
                dispose();
            }
        });
    }
    
    /**
     * 连接到服务器
     */
    private void connect() {
        // 先登录获取token
        HttpClient.LoginResponse loginResponse = httpClient.login(userId, "123456"); // 默认密码
        
        if (loginResponse == null) {
            return;
        }
        
        // 根据选择的协议建立连接
        String selectedProtocol = (String) protocolComboBox.getSelectedItem();
        boolean connectionSuccess = false;
        
        if ("WebSocket".equals(selectedProtocol)) {
            realWebSocketClient = new RealWebSocketClient(userId, loginResponse.getToken(), this);
            realWebSocketClient.connect();

            // 等待连接建立
            if (realWebSocketClient.waitForConnection(10, TimeUnit.SECONDS)) {
                // 等待登录完成
                int retryCount = 0;
                while (retryCount < 50 && !realWebSocketClient.isLoggedIn()) { // 等待5秒
                    try {
                        Thread.sleep(100);
                        retryCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (realWebSocketClient.isLoggedIn()) {
                    connectionSuccess = true;
                }
            }
        } else if ("TCP".equals(selectedProtocol)) {
            tcpClient = new NettyTcpClient(userId, loginResponse.getToken(), this);
            tcpClient.connect();

            // 等待连接建立
            if (tcpClient.waitForConnection(10, TimeUnit.SECONDS)) {
                // 等待登录完成
                int retryCount = 0;
                while (retryCount < 50 && !tcpClient.isLoggedIn()) { // 等待5秒
                    try {
                        Thread.sleep(100);
                        retryCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (tcpClient.isLoggedIn()) {
                    connectionSuccess = true;
                }
            }
        }
        
        if (connectionSuccess) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("状态: 已连接 (" + selectedProtocol + ")");
                statusLabel.setForeground(Color.GREEN);
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                sendPrivateButton.setEnabled(true);
                sendGroupButton.setEnabled(true);
                protocolComboBox.setEnabled(false); // 连接后禁用协议选择
            });

            // 启动心跳
            startHeartbeat();

            // 启动离线消息同步
            startOfflineMessageSync();
        }
    }
    
    /**
     * 断开连接
     */
    private void disconnect() {
        // 断开WebSocket连接
        if (realWebSocketClient != null) {
            realWebSocketClient.disconnect();
            realWebSocketClient = null;
        }
        
        // 断开TCP连接
        if (tcpClient != null) {
            tcpClient.disconnect();
            tcpClient = null;
        }
        

        
        // 停止心跳
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            heartbeatExecutor = null;
        }
        
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("状态: 未连接");
            statusLabel.setForeground(Color.RED);
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            sendPrivateButton.setEnabled(false);
            sendGroupButton.setEnabled(false);
            protocolComboBox.setEnabled(true); // 断开后重新启用协议选择
        });
        
        appendMessage("已断开连接");
    }
    
    /**
     * 发送私聊消息
     */
    private void sendPrivateMessage() {
        String toUserId = targetUserInput.getText().trim();
        String content = privateMessageInput.getText().trim();
        
        if (toUserId.isEmpty() || content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入目标用户和消息内容", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        boolean sent = false;
        
        // 使用WebSocket客户端发送
        if (realWebSocketClient != null && realWebSocketClient.isConnected()) {
            realWebSocketClient.sendPrivateMessage(toUserId, content);
            sent = true;
        }
        // 使用TCP客户端发送
        else if (tcpClient != null && tcpClient.isConnected()) {
            tcpClient.sendPrivateMessage(toUserId, content);
            sent = true;
        }

        
        if (sent) {
            appendMessage(String.format("[私聊] 发送给 %s: %s", toUserId, content));
            privateMessageInput.setText("");
        } else {
            appendMessage("连接已断开，无法发送消息");
        }
    }
    
    /**
     * 发送群聊消息
     */
    private void sendGroupMessage() {
        String groupId = groupIdInput.getText().trim();
        String content = groupMessageInput.getText().trim();
        
        if (groupId.isEmpty() || content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入群组ID和消息内容", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        boolean sent = false;
        
        // 使用WebSocket客户端发送
        if (realWebSocketClient != null && realWebSocketClient.isConnected()) {
            realWebSocketClient.sendGroupMessage(groupId, content);
            sent = true;
        }
        // 使用TCP客户端发送
        else if (tcpClient != null && tcpClient.isConnected()) {
            tcpClient.sendGroupMessage(groupId, content);
            sent = true;
        }

        
        if (sent) {
            appendMessage(String.format("[群聊] 发送到群 %s: %s", groupId, content));
            groupMessageInput.setText("");
        } else {
            appendMessage("连接已断开，无法发送消息");
        }
    }
    
    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            boolean heartbeatSent = false;
            
            // 使用WebSocket客户端发送心跳
            if (realWebSocketClient != null && realWebSocketClient.isConnected()) {
                realWebSocketClient.sendHeartbeat();
                heartbeatSent = true;
            }
            // 使用TCP客户端发送心跳
            else if (tcpClient != null && tcpClient.isConnected()) {
                tcpClient.sendHeartbeat();
                heartbeatSent = true;
            }

            
            if (heartbeatSent) {
                SwingUtilities.invokeLater(() -> appendMessage("[心跳] 发送心跳包"));
            }
        }, 30, 30, TimeUnit.SECONDS); // 每30秒发送一次心跳
    }
    
    /**
     * 处理接收到的消息
     */
    @Override
    public void handleMessage(ChatMessage message) {
        String messageText = null;
        
        switch (message.getType()) {
            case MessageTypeConstants.PRIVATE_CHAT_MESSAGE:
                messageText = String.format("[私聊] %s: %s", message.getFromId(), message.getContent());
                break;
            case MessageTypeConstants.GROUP_CHAT_MESSAGE:
                messageText = String.format("[群聊] %s@%s: %s", message.getFromId(), message.getToId(), message.getContent());
                break;
            case MessageTypeConstants.GROUP_MESSAGE_NOTIFICATION:
                messageText = String.format("[群聊] %s@%s: %s", message.getFromId(), message.getConversationId(), message.getContent());
                break;
            // 忽略系统消息和其他类型消息
            default:
                return; // 不显示其他类型的消息
        }
        
        if (messageText != null) {
            appendMessage(messageText);
        }
    }
    
    /**
     * 添加消息到显示区域
     */
    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            messageArea.append(String.format("[%s] %s\n", timestamp, message));
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    /**
     * 显示同步的离线消息
     * @param messages 消息列表
     * @param syncType 同步类型
     */
    public void displaySyncMessages(java.util.List<Object> messages, String syncType) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        for (Object messageObj : messages) {
            if (messageObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> messageMap = (java.util.Map<String, Object>) messageObj;
                
                String content = (String) messageMap.get("content");
                String fromId = (String) messageMap.get("fromId");
                String toId = (String) messageMap.get("toId");
                String conversationId = (String) messageMap.get("conversationId");
                String type = (String) messageMap.get("type");
                
                String messageText = null;
                
                // 根据消息类型格式化显示
                if ("1".equals(type)) { // 私聊消息
                    messageText = String.format("[离线私聊] %s: %s", fromId, content);
                } else if ("2".equals(type)) { // 群聊消息
                    messageText = String.format("[离线群聊] %s@%s: %s", fromId, conversationId, content);
                }
                
                if (messageText != null) {
                    appendMessage(messageText);
                }
            }
        }
    }
    
    /**
     * 获取用户ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * 设置目标用户ID（用于快速测试）
     */
    public void setTargetUserId(String targetUserId) {
        SwingUtilities.invokeLater(() -> targetUserInput.setText(targetUserId));
    }
    
    /**
     * 设置群组ID（用于快速测试）
     */
    public void setGroupId(String groupId) {
        SwingUtilities.invokeLater(() -> groupIdInput.setText(groupId));
    }

    // ========== 离线消息同步相关方法 ==========

    /**
     * 启动离线消息同步
     */
    private void startOfflineMessageSync() {
        appendMessage("启动离线消息同步...");
        updateSyncStatus("检查中...", Color.ORANGE);

        // 启动同步，带进度回调
        syncManager.startSyncIfNeeded(userId, new OfflineMessageSyncManager.SyncProgressCallback() {
            @Override
            public void onProgress(OfflineMessageSyncManager.SyncStatus status, String message) {
                SwingUtilities.invokeLater(() -> {
                    switch (status) {
                        case STARTED:
                            updateSyncStatus("同步开始", Color.BLUE);
                            appendMessage("离线消息同步开始: " + message);
                            break;
                        case SYNCING:
                            updateSyncStatus("同步中...", Color.BLUE);
                            appendMessage("同步进度: " + message);
                            break;
                        case COMPLETED:
                            updateSyncStatus("同步完成", Color.GREEN);
                            appendMessage("离线消息同步完成: " + message);
                            break;
                        case ERROR:
                            updateSyncStatus("同步失败", Color.RED);
                            appendMessage("离线消息同步失败: " + message);
                            break;
                    }
                });
            }
        });
    }

    /**
     * 更新同步状态显示
     */
    private void updateSyncStatus(String status, Color color) {
        if (syncStatusLabel != null) {
            syncStatusLabel.setText("同步: " + status);
            syncStatusLabel.setForeground(color);
        }
    }

    /**
     * 获取本地存储统计信息
     */
    public void showStorageStats() {
        if (localStorage != null) {
            LocalMessageStorage.StorageStats stats = localStorage.getStorageStats();
            String message = String.format("存储统计 - 总大小: %d bytes, 用户数: %d",
                                          stats.getTotalSize(), stats.getUserCount());
            appendMessage(message);
        }
    }

    /**
     * 清理用户本地数据
     */
    public void clearLocalData() {
        if (localStorage != null) {
            localStorage.clearUserData(userId);
            appendMessage("本地数据已清理");
            updateSyncStatus("已清理", Color.GRAY);
        }
    }

    /**
     * 显示用户注册对话框
     */
    private void showRegisterDialog() {
        JDialog registerDialog = new JDialog(this, "用户注册", true);
        registerDialog.setSize(400, 200);
        registerDialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // 用户ID输入
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("用户ID:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField userIdField = new JTextField(20);
        panel.add(userIdField, gbc);

        // 用户名输入
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("用户名:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField usernameField = new JTextField(20);
        panel.add(usernameField, gbc);

        // 默认密码提示
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel passwordHintLabel = new JLabel("<html><font color='blue'>注册成功后，默认密码为：123456<br/>建议首次登录后修改密码</font></html>");
        passwordHintLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(passwordHintLabel, gbc);

        // 按钮面板
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel buttonPanel = new JPanel(new FlowLayout());

        JButton registerBtn = new JButton("注册");
        JButton cancelBtn = new JButton("取消");

        registerBtn.addActionListener(e -> {
            String userIdInput = userIdField.getText().trim();
            String username = usernameField.getText().trim();

            // 验证输入
            if (userIdInput.isEmpty() || username.isEmpty()) {
                JOptionPane.showMessageDialog(registerDialog, "用户ID和用户名不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 执行注册，使用默认密码"123456"
            performUserRegistration(userIdInput, username, registerDialog);
        });

        cancelBtn.addActionListener(e -> registerDialog.dispose());

        buttonPanel.add(registerBtn);
        buttonPanel.add(cancelBtn);
        panel.add(buttonPanel, gbc);

        registerDialog.add(panel);
        registerDialog.setVisible(true);
    }

    /**
     * 执行用户注册
     */
    private void performUserRegistration(String userIdInput, String username, JDialog dialog) {
        try {
            appendMessage("[系统] 正在注册用户: " + userIdInput + " (" + username + ")");

            // 调用真实的注册API，使用默认密码"123456"
            boolean success = httpClient.register(userIdInput, username, "123456");

            if (success) {
                appendMessage("[系统] 用户注册成功！用户ID: " + userIdInput + ", 用户名: " + username + ", 默认密码: 123456");
                JOptionPane.showMessageDialog(dialog,
                    "注册成功！\n用户ID: " + userIdInput + "\n用户名: " + username + "\n默认密码: 123456\n\n建议首次登录后修改密码",
                    "注册成功", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } else {
                appendMessage("[系统] 用户注册失败！可能用户ID已存在或服务异常");
                JOptionPane.showMessageDialog(dialog,
                    "注册失败！\n可能原因：\n1. 用户ID已存在\n2. 用户服务未启动\n3. 网络连接异常",
                    "注册失败", JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            appendMessage("[系统] 注册异常: " + e.getMessage());
            JOptionPane.showMessageDialog(dialog,
                "注册异常: " + e.getMessage() + "\n请检查用户服务是否正常运行",
                "注册异常", JOptionPane.ERROR_MESSAGE);
        }
    }



    /**
     * 显示创建群聊对话框
     */
    private void showCreateGroupDialog() {
        JDialog groupDialog = new JDialog(this, "创建群聊", true);
        groupDialog.setSize(500, 400);
        groupDialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 顶部信息输入面板
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // 群聊名称
        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("群聊名称:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField groupNameField = new JTextField(20);
        topPanel.add(groupNameField, gbc);

        // 群聊描述
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        topPanel.add(new JLabel("群聊描述:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField groupDescField = new JTextField(20);
        topPanel.add(groupDescField, gbc);

        // 中间成员选择面板
        JPanel memberPanel = new JPanel(new BorderLayout(5, 5));
        memberPanel.setBorder(BorderFactory.createTitledBorder("群聊成员"));

        // 成员输入区域
        JPanel memberInputPanel = new JPanel(new BorderLayout(5, 5));
        JTextField memberInput = new JTextField();
        memberInput.setBorder(BorderFactory.createTitledBorder("输入用户ID（回车添加）"));
        JButton addMemberBtn = new JButton("添加成员");

        memberInputPanel.add(memberInput, BorderLayout.CENTER);
        memberInputPanel.add(addMemberBtn, BorderLayout.EAST);

        // 成员列表
        DefaultListModel<String> memberListModel = new DefaultListModel<>();
        memberListModel.addElement(userId + " (群主)"); // 添加当前用户作为群主
        JList<String> memberList = new JList<>(memberListModel);
        memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane memberScrollPane = new JScrollPane(memberList);
        memberScrollPane.setPreferredSize(new Dimension(400, 150));

        // 删除成员按钮
        JButton removeMemberBtn = new JButton("移除选中成员");
        removeMemberBtn.addActionListener(e -> {
            int selectedIndex = memberList.getSelectedIndex();
            if (selectedIndex > 0) { // 不能删除群主
                memberListModel.remove(selectedIndex);
            } else if (selectedIndex == 0) {
                JOptionPane.showMessageDialog(groupDialog, "不能移除群主！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });

        // 添加成员事件
        ActionListener addMemberAction = e -> {
            String memberId = memberInput.getText().trim();
            if (!memberId.isEmpty()) {
                // 检查是否已存在
                boolean exists = false;
                for (int i = 0; i < memberListModel.size(); i++) {
                    String existingMember = memberListModel.getElementAt(i);
                    if (existingMember.startsWith(memberId + " ") || existingMember.equals(memberId)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    memberListModel.addElement(memberId);
                    memberInput.setText("");
                } else {
                    JOptionPane.showMessageDialog(groupDialog, "成员已存在！", "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        };

        memberInput.addActionListener(addMemberAction);
        addMemberBtn.addActionListener(addMemberAction);

        JPanel memberButtonPanel = new JPanel(new FlowLayout());
        memberButtonPanel.add(removeMemberBtn);

        memberPanel.add(memberInputPanel, BorderLayout.NORTH);
        memberPanel.add(memberScrollPane, BorderLayout.CENTER);
        memberPanel.add(memberButtonPanel, BorderLayout.SOUTH);

        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton createBtn = new JButton("创建群聊");
        JButton cancelBtn = new JButton("取消");

        createBtn.addActionListener(e -> {
            String groupName = groupNameField.getText().trim();
            String groupDesc = groupDescField.getText().trim();

            if (groupName.isEmpty()) {
                JOptionPane.showMessageDialog(groupDialog, "群聊名称不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (memberListModel.size() < 2) {
                JOptionPane.showMessageDialog(groupDialog, "群聊至少需要2个成员！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 收集成员列表
            java.util.List<String> members = new java.util.ArrayList<>();
            for (int i = 0; i < memberListModel.size(); i++) {
                String member = memberListModel.getElementAt(i);
                // 提取用户ID（去掉后缀）
                if (member.contains(" (")) {
                    member = member.substring(0, member.indexOf(" ("));
                }
                members.add(member);
            }

            // 执行创建群聊
            performCreateGroup(groupName, groupDesc, members, groupDialog);
        });

        cancelBtn.addActionListener(e -> groupDialog.dispose());

        buttonPanel.add(createBtn);
        buttonPanel.add(cancelBtn);

        // 组装对话框
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(memberPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        groupDialog.add(panel);
        groupDialog.setVisible(true);
    }

    /**
     * 执行创建群聊
     */
    private void performCreateGroup(String groupName, String groupDesc, java.util.List<String> members, JDialog dialog) {
        try {
            appendMessage("[系统] 正在创建群聊: " + groupName);
            appendMessage("[系统] 群聊成员: " + String.join(", ", members));

            // 调用真实的创建群聊API
            String groupId = httpClient.createGroup(groupName, groupDesc, members, userId);

            if (groupId != null && !groupId.isEmpty()) {
                appendMessage("[系统] 群聊创建成功！群聊ID: " + groupId);
                appendMessage("[系统] 已在conversation表中创建会话记录");
                appendMessage("[系统] 已在user_conversation_list表中添加所有群成员");

                // 自动填充群聊ID到群聊输入框
                groupIdInput.setText(groupId);

                JOptionPane.showMessageDialog(dialog,
                    "群聊创建成功！\n群聊ID: " + groupId + "\n群聊名称: " + groupName +
                    "\n成员数量: " + members.size() + "\n\n数据库操作：\n" +
                    "✓ conversation表已添加会话记录\n" +
                    "✓ user_conversation_list表已添加群成员",
                    "创建成功", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } else {
                appendMessage("[系统] 群聊创建失败！可能原因：消息服务未启动或网络异常");
                JOptionPane.showMessageDialog(dialog,
                    "创建失败！\n可能原因：\n1. 消息服务未启动\n2. 网络连接异常\n3. 数据库连接失败\n4. 部分成员不存在",
                    "创建失败", JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            appendMessage("[系统] 创建群聊异常: " + e.getMessage());
            JOptionPane.showMessageDialog(dialog,
                "创建异常: " + e.getMessage() + "\n请检查消息服务是否正常运行",
                "创建异常", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 发送群聊会话ACK确认
     * @param ackContent ACK内容，格式：conversationId1:seq1,conversationId2:seq2
     * @return 是否发送成功
     */
    public boolean sendGroupConversationAck(String ackContent) {
        try {
            if (ackContent == null || ackContent.trim().isEmpty()) {
                System.err.println("群聊会话ACK内容为空");
                return false;
            }

            boolean sent = false;

            // 使用WebSocket客户端发送
            if (realWebSocketClient != null && realWebSocketClient.isConnected()) {
                realWebSocketClient.sendGroupConversationAck(ackContent);
                sent = true;
                System.out.println("[DEBUG] 群聊会话ACK已通过WebSocket发送");
            }
            // 使用TCP客户端发送
            else if (tcpClient != null && tcpClient.isConnected()) {
                tcpClient.sendGroupConversationAck(ackContent);
                sent = true;
                System.out.println("[DEBUG] 群聊会话ACK已通过TCP发送");
            }

            return sent;

        } catch (Exception e) {
            System.err.println("发送群聊会话ACK失败: " + e.getMessage());
            return false;
        }
    }


}