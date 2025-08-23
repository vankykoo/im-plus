package com.vanky.im.testclient.ui;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.testclient.client.HttpClient;
import com.vanky.im.testclient.client.IMClient;
import com.vanky.im.testclient.processor.RealtimeMessageProcessor;
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
public class UserWindow extends JFrame implements IMClient.MessageHandler {

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
    private JButton markReadButton;
    private JLabel statusLabel;
    private JComboBox<String> protocolComboBox;
    
    private HttpClient httpClient;
    private IMClient imClient;
    private ScheduledExecutorService heartbeatExecutor;

    // 离线消息同步相关
    private LocalMessageStorage localStorage;
    private OfflineMessageSyncManager syncManager;
    private JLabel syncStatusLabel;
    private RealtimeMessageProcessor realtimeMessageProcessor;
    
    public UserWindow(String userId, IMClient imClient) {
        this.userId = userId;
        this.imClient = imClient;
        this.httpClient = new HttpClient();

        // 初始化离线消息同步组件
        this.localStorage = new LocalMessageStorage();
        this.syncManager = new OfflineMessageSyncManager(httpClient, localStorage, this);
        this.realtimeMessageProcessor = new RealtimeMessageProcessor(this, localStorage, syncManager);
        this.syncManager.setRealtimeMessageProcessor(this.realtimeMessageProcessor);

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
        markReadButton = new JButton("标记已读");

        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect());
        registerButton.addActionListener(e -> showRegisterDialog());
        createGroupButton.addActionListener(e -> showCreateGroupDialog());
        markReadButton.addActionListener(e -> showMarkReadDialog());

        controlPanel.add(protocolLabel);
        controlPanel.add(protocolComboBox);
        controlPanel.add(connectButton);
        controlPanel.add(disconnectButton);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(registerButton);
        controlPanel.add(createGroupButton);
        controlPanel.add(markReadButton);
        
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
        imClient.connect();

        // 这里可以添加一个回调或者事件监听来更新UI，而不是阻塞等待
        // 为了简单起见，我们假设连接会成功并立即更新UI
        // 在真实的异步场景中，应该由 AbstractClient 在连接成功后回调来更新UI
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("状态: 连接中...");
            statusLabel.setForeground(Color.ORANGE);
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            sendPrivateButton.setEnabled(true);
            sendGroupButton.setEnabled(true);
            protocolComboBox.setEnabled(false); // 连接后禁用协议选择
        });

        // 启动消息同步
        startMessageSync();

        // 心跳和重连逻辑已经移到 AbstractClient 中，这里不再需要手动管理
    }
    
    /**
     * 断开连接
     */
    private void disconnect() {
        if (imClient != null) {
            imClient.disconnect();
        }

        // 心跳和重连逻辑已经移到 AbstractClient 中，这里不再需要手动管理
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
        
        if (imClient != null && imClient.isConnected()) {
            imClient.sendPrivateMessage(toUserId, content);
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
        
        if (imClient != null && imClient.isConnected()) {
            imClient.sendGroupMessage(groupId, content);
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
        // 心跳逻辑已移至 AbstractClient
    }
    
    /**
     * 处理接收到的消息
     */
    @Override
    public void handleMessage(ChatMessage message) {
        // 根据消息类型进行分发
        switch (message.getType()) {
            case MessageTypeConstants.PRIVATE_CHAT_MESSAGE:
            case MessageTypeConstants.GROUP_CHAT_MESSAGE:
            case MessageTypeConstants.GROUP_MESSAGE_NOTIFICATION:
                // 将实时聊天消息交由RealtimeMessageProcessor处理
                realtimeMessageProcessor.processMessage(message);
                break;
            case MessageTypeConstants.MESSAGE_READ_NOTIFICATION:
                // 已读通知直接显示
                displayReadNotification(message);
                break;
            // 忽略系统消息和其他类型消息
            default:
                // 其他类型的消息不处理
                break;
        }
    }

    /**
     * 格式化并显示一条聊天消息
     * @param message 聊天消息
     */
    public void formatAndDisplayMessage(ChatMessage message) {
        String messageText = null;
        switch (message.getType()) {
            case MessageTypeConstants.PRIVATE_CHAT_MESSAGE:
                messageText = String.format("[私聊] %s: %s", message.getFromId(), message.getContent());
                break;
            case MessageTypeConstants.GROUP_CHAT_MESSAGE:
            case MessageTypeConstants.GROUP_MESSAGE_NOTIFICATION:
                messageText = String.format("[群聊] %s@%s: %s", message.getFromId(), message.getConversationId(), message.getContent());
                break;
            default:
                // 其他类型不在此处显示
                return;
        }
        if (messageText != null) {
            appendMessage(messageText);
        }
    }

    /**
     * 处理并显示已读通知
     * @param message 包含已读通知的ChatMessage
     */
    private void displayReadNotification(ChatMessage message) {
        String messageText;
        if (message.hasReadNotification()) {
            var readNotification = message.getReadNotification();
            String conversationId = readNotification.getConversationId();
            String msgId = readNotification.getMsgId();
            int readCount = readNotification.getReadCount();
            long lastReadSeq = readNotification.getLastReadSeq();

            if (conversationId.startsWith("private_")) {
                messageText = String.format("[已读通知] 私聊会话 %s 的消息已被读取，已读序列号: %d",
                        conversationId, lastReadSeq);
            } else if (conversationId.startsWith("group_")) {
                messageText = String.format("[已读通知] 群聊消息 %s 已被读取，当前已读数: %d",
                        msgId, readCount);
            } else {
                messageText = String.format("[已读通知] 消息 %s 已被读取", msgId);
            }
        } else {
            messageText = "[已读通知] 收到已读通知（格式异常）";
        }
        appendMessage(messageText);
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
     * 显示同步的消息
     * @param messages 消息列表
     * @param syncType 同步类型
     */
    public void displaySyncMessages(java.util.List<Object> messages, String syncType) {
        if (messages == null || messages.isEmpty()) {
            System.out.println("[DEBUG] UserWindow.displaySyncMessages: 消息列表为空");
            return;
        }

        System.out.println("[DEBUG] UserWindow.displaySyncMessages: 开始显示 " + messages.size() + " 条消息");

        for (int i = 0; i < messages.size(); i++) {
            Object messageObj = messages.get(i);
            System.out.println("[DEBUG] 处理消息[" + i + "]: " + messageObj.getClass().getName());

            if (messageObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> messageMap = (java.util.Map<String, Object>) messageObj;

                System.out.println("[DEBUG] 消息Map内容: " + messageMap);

                String content = (String) messageMap.get("content");
                String fromUserId = (String) messageMap.get("fromUserId");  // 修正字段名
                String toUserId = (String) messageMap.get("toUserId");      // 修正字段名
                String conversationId = (String) messageMap.get("conversationId");
                Object msgTypeObj = messageMap.get("msgType");              // 修正字段名

                System.out.println("[DEBUG] 解析字段 - content: " + content +
                                 ", fromUserId: " + fromUserId +
                                 ", toUserId: " + toUserId +
                                 ", conversationId: " + conversationId +
                                 ", msgType: " + msgTypeObj);

                String messageText = null;

                // 根据消息类型格式化显示（msgType是数值类型）
                if (msgTypeObj != null) {
                    int msgType = 0;
                    if (msgTypeObj instanceof Number) {
                        msgType = ((Number) msgTypeObj).intValue();
                    } else if (msgTypeObj instanceof String) {
                        try {
                            msgType = Integer.parseInt((String) msgTypeObj);
                        } catch (NumberFormatException e) {
                            System.err.println("[DEBUG] 无法解析消息类型: " + msgTypeObj);
                        }
                    }

                    if (msgType == 1) { // 私聊消息
                        messageText = String.format("[同步私聊] %s: %s", fromUserId, content);
                        System.out.println("[DEBUG] 格式化私聊消息: " + messageText);
                    } else if (msgType == 2) { // 群聊消息
                        messageText = String.format("[同步群聊] %s@%s: %s", fromUserId, conversationId, content);
                        System.out.println("[DEBUG] 格式化群聊消息: " + messageText);
                    } else {
                        messageText = String.format("[同步消息] %s: %s", fromUserId, content);
                        System.out.println("[DEBUG] 格式化未知类型消息 (msgType=" + msgType + "): " + messageText);
                    }
                }

                if (messageText != null) {
                    System.out.println("[DEBUG] 显示消息: " + messageText);
                    appendMessage(messageText);
                } else {
                    System.err.println("[DEBUG] 无法格式化消息，跳过显示");
                }
            } else {
                System.err.println("[DEBUG] 消息对象不是Map类型: " + messageObj);
            }
        }

        System.out.println("[DEBUG] UserWindow.displaySyncMessages: 消息显示完成");
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

    // ========== 消息同步相关方法 ==========

    /**
     * 启动消息同步
     */
    private void startMessageSync() {
        appendMessage("启动消息同步...");
        updateSyncStatus("检查中...", Color.ORANGE);

        // 启动同步，带进度回调
        syncManager.startSyncIfNeeded(userId, new OfflineMessageSyncManager.SyncProgressCallback() {
            @Override
            public void onProgress(OfflineMessageSyncManager.SyncStatus status, String message) {
                SwingUtilities.invokeLater(() -> {
                    switch (status) {
                        case STARTED:
                            updateSyncStatus("同步开始", Color.BLUE);
                            appendMessage("消息同步开始: " + message);
                            break;
                        case SYNCING:
                            updateSyncStatus("同步中...", Color.BLUE);
                            appendMessage("同步进度: " + message);
                            break;
                        case COMPLETED:
                            updateSyncStatus("同步完成", Color.GREEN);
                            appendMessage("消息同步完成: " + message);
                            break;
                        case ERROR:
                            updateSyncStatus("同步失败", Color.RED);
                            appendMessage("消息同步失败: " + message);
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

            if (imClient != null && imClient.isConnected()) {
                imClient.sendGroupConversationAck(ackContent);
                sent = true;
                System.out.println("[DEBUG] 群聊会话ACK已发送");
            }

            return sent;

        } catch (Exception e) {
            System.err.println("发送群聊会话ACK失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 显示标记已读对话框
     */
    private void showMarkReadDialog() {
        JDialog readDialog = new JDialog(this, "标记消息已读", true);
        readDialog.setSize(500, 350);
        readDialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 顶部说明
        JLabel titleLabel = new JLabel("选择要标记为已读的会话");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // 中间会话选择面板
        JPanel conversationPanel = new JPanel(new BorderLayout(5, 5));
        conversationPanel.setBorder(BorderFactory.createTitledBorder("会话信息"));

        // 会话类型选择
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel typeLabel = new JLabel("会话类型:");
        ButtonGroup typeGroup = new ButtonGroup();
        JRadioButton privateRadio = new JRadioButton("私聊", true);
        JRadioButton groupRadio = new JRadioButton("群聊");
        typeGroup.add(privateRadio);
        typeGroup.add(groupRadio);
        typePanel.add(typeLabel);
        typePanel.add(privateRadio);
        typePanel.add(groupRadio);

        // 会话ID输入
        JPanel idPanel = new JPanel(new BorderLayout(5, 5));
        JLabel idLabel = new JLabel("对方用户ID/群聊ID:");
        JTextField idField = new JTextField();
        idField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLoweredBevelBorder(),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        idPanel.add(idLabel, BorderLayout.WEST);
        idPanel.add(idField, BorderLayout.CENTER);

        // 已读序列号输入
        JPanel seqPanel = new JPanel(new BorderLayout(5, 5));
        JLabel seqLabel = new JLabel("已读到序列号:");
        JTextField seqField = new JTextField();
        seqField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLoweredBevelBorder(),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        seqField.setText("0"); // 默认值
        seqPanel.add(seqLabel, BorderLayout.WEST);
        seqPanel.add(seqField, BorderLayout.CENTER);

        // 组装会话面板
        JPanel inputPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        inputPanel.add(typePanel);
        inputPanel.add(idPanel);
        inputPanel.add(seqPanel);
        conversationPanel.add(inputPanel, BorderLayout.CENTER);

        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton confirmButton = new JButton("确认标记");
        JButton cancelButton = new JButton("取消");

        confirmButton.setPreferredSize(new Dimension(100, 30));
        cancelButton.setPreferredSize(new Dimension(100, 30));

        // 确认按钮事件
        confirmButton.addActionListener(e -> {
            String targetId = idField.getText().trim();
            String seqText = seqField.getText().trim();

            if (targetId.isEmpty()) {
                JOptionPane.showMessageDialog(readDialog, "请输入对方用户ID或群聊ID！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            long lastReadSeq;
            try {
                lastReadSeq = Long.parseLong(seqText);
                if (lastReadSeq < 0) {
                    throw new NumberFormatException("序列号不能为负数");
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(readDialog, "请输入有效的序列号（非负整数）！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 生成会话ID
            String conversationId;
            if (privateRadio.isSelected()) {
                // 私聊会话ID：确保较小的用户ID在前
                if (userId.compareTo(targetId) < 0) {
                    conversationId = "private_" + userId + "_" + targetId;
                } else {
                    conversationId = "private_" + targetId + "_" + userId;
                }
            } else {
                // 群聊会话ID
                conversationId = "group_" + targetId;
            }

            // 发送已读回执
            boolean sent = sendReadReceipt(conversationId, lastReadSeq);
            if (sent) {
                String typeText = privateRadio.isSelected() ? "私聊" : "群聊";
                appendMessage(String.format("[已读] 已标记%s会话 %s 的消息为已读，序列号: %d",
                        typeText, conversationId, lastReadSeq));
                readDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(readDialog, "发送已读回执失败！请检查连接状态。", "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        // 取消按钮事件
        cancelButton.addActionListener(e -> readDialog.dispose());

        buttonPanel.add(confirmButton);
        buttonPanel.add(cancelButton);

        // 组装主面板
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(conversationPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        readDialog.add(panel);
        readDialog.setVisible(true);
    }

    /**
     * 发送已读回执
     */
    private boolean sendReadReceipt(String conversationId, long lastReadSeq) {
        try {
            if (imClient != null && imClient.isConnected()) {
                imClient.sendReadReceipt(conversationId, lastReadSeq);
                return true;
            } else {
                appendMessage("[错误] 未连接到服务器，无法发送已读回执");
                return false;
            }
        } catch (Exception e) {
            appendMessage("[错误] 发送已读回执失败: " + e.getMessage());
            return false;
        }
    }


}