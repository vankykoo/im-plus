package com.vanky.im.testclient.ui;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.enums.ClientToClientMessageType;
import com.vanky.im.common.enums.ServerToClientMessageType;
import com.vanky.im.testclient.client.HttpClient;
import com.vanky.im.testclient.client.IMWebSocketClient;
import com.vanky.im.testclient.client.RealWebSocketClient;
import com.vanky.im.testclient.client.NettyTcpClient;


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
public class UserWindow extends JFrame implements IMWebSocketClient.MessageHandler, RealWebSocketClient.MessageHandler, NettyTcpClient.MessageHandler {
    
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
    private JLabel statusLabel;
    private JComboBox<String> protocolComboBox;
    
    private HttpClient httpClient;
    private IMWebSocketClient webSocketClient;
    private RealWebSocketClient realWebSocketClient;
    private NettyTcpClient tcpClient;
    private ScheduledExecutorService heartbeatExecutor;
    
    public UserWindow(String userId) {
        this.userId = userId;
        this.httpClient = new HttpClient();
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
        statusLabel = new JLabel("状态: 未连接");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        
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
        
        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect());
        
        controlPanel.add(protocolLabel);
        controlPanel.add(protocolComboBox);
        controlPanel.add(connectButton);
        controlPanel.add(disconnectButton);
        
        topPanel.add(statusLabel, BorderLayout.WEST);
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
        groupIdInput = new JTextField("group1", 8);
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
        appendMessage("正在登录...");
        HttpClient.LoginResponse loginResponse = httpClient.login(userId, "123456"); // 默认密码
        
        if (loginResponse == null) {
            appendMessage("登录失败，请检查用户服务是否启动");
            return;
        }
        
        appendMessage("登录成功，获取到token: " + loginResponse.getToken());
        
        // 根据选择的协议建立连接
        String selectedProtocol = (String) protocolComboBox.getSelectedItem();
        boolean connectionSuccess = false;
        
        if ("WebSocket".equals(selectedProtocol)) {
            appendMessage("正在连接WebSocket服务器...");
            realWebSocketClient = new RealWebSocketClient(userId, loginResponse.getToken(), this);
            realWebSocketClient.connect();

            // 等待连接建立
            if (realWebSocketClient.waitForConnection(10, TimeUnit.SECONDS)) {
                appendMessage("WebSocket连接已建立，等待登录响应...");

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
                    appendMessage("WebSocket连接和登录成功");
                } else {
                    appendMessage("WebSocket连接成功但登录失败");
                }
            } else {
                appendMessage("WebSocket连接失败");
            }
        } else if ("TCP".equals(selectedProtocol)) {
            appendMessage("正在连接TCP服务器...");
            tcpClient = new NettyTcpClient(userId, loginResponse.getToken(), this);
            tcpClient.connect();

            // 等待连接建立
            if (tcpClient.waitForConnection(10, TimeUnit.SECONDS)) {
                appendMessage("TCP连接已建立，等待登录响应...");

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
                    appendMessage("TCP连接和登录成功");
                } else {
                    appendMessage("TCP连接成功但登录失败");
                }
            } else {
                appendMessage("TCP连接失败");
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
        
        // 断开旧的WebSocket连接（兼容性）
        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
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
        // 兼容旧的WebSocket客户端
        else if (webSocketClient != null && webSocketClient.isLoggedIn()) {
            webSocketClient.sendPrivateMessage(toUserId, content);
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
        // 兼容旧的WebSocket客户端
        else if (webSocketClient != null && webSocketClient.isLoggedIn()) {
            webSocketClient.sendGroupMessage(groupId, content);
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
            // 兼容旧的WebSocket客户端
            else if (webSocketClient != null && webSocketClient.isLoggedIn()) {
                webSocketClient.sendHeartbeat();
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
        String messageText;
        
        switch (message.getType()) {
            case 3001: // PRIVATE_CHAT_MESSAGE
                messageText = String.format("[私聊] %s: %s", message.getFromId(), message.getContent());
                break;
            case 3002: // GROUP_CHAT_MESSAGE
                messageText = String.format("[群聊] %s@%s: %s", message.getFromId(), message.getToId(), message.getContent());
                break;
            case 1001: // LOGIN_RESPONSE
                messageText = String.format("[系统] 登录响应: %s", message.getContent());
                break;
            case 1005: // MESSAGE_DELIVERY_SUCCESS
                messageText = String.format("[系统] 消息投递成功: %s", message.getContent());
                break;
            case 1006: // MESSAGE_DELIVERY_FAILED
                messageText = String.format("[系统] 消息投递失败: %s", message.getContent());
                break;
            case 1004: // SYSTEM_NOTIFICATION
                messageText = String.format("[系统通知] %s", message.getContent());
                break;
            default:
                messageText = String.format("[未知类型%d] %s: %s", message.getType(), message.getFromId(), message.getContent());
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
}