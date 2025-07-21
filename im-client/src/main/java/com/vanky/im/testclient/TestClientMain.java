package com.vanky.im.testclient;

import com.vanky.im.testclient.ui.UserWindow;


import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * IM测试客户端主程序 - Swing版本
 */
public class TestClientMain extends JFrame {
    
    private List<UserWindow> userWindows = new ArrayList<>();
    
    public TestClientMain() {
        initUI();
    }
    
    private void initUI() {
        setTitle("IM测试客户端控制台");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(450, 700);
        setLocationRelativeTo(null);
        
        // 创建主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // 标题
        JLabel titleLabel = new JLabel("IM系统集成测试客户端");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // 说明文字
        JTextArea descArea = new JTextArea(
                "使用说明:\n" +
                "1. 点击下方按钮创建测试用户窗口\n" +
                "2. 在用户窗口中点击'连接'按钮登录\n" +
                "3. 可以进行私聊和群聊测试\n" +
                "4. 支持多用户同时在线测试");
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        descArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // 快速创建用户按钮区域
        JPanel userButtonPanel = new JPanel(new GridLayout(2, 3, 10, 10));
        userButtonPanel.setBorder(BorderFactory.createTitledBorder("快速创建用户"));
        
        String[] presetUsers = {"user1", "user2", "user3", "user4", "user5", "admin"};
        for (String userId : presetUsers) {
            JButton userButton = new JButton("创建: " + userId);
            userButton.addActionListener(e -> createUserWindow(userId));
            userButtonPanel.add(userButton);
        }
        
        // 自定义用户创建区域
        JPanel customUserPanel = new JPanel(new FlowLayout());
        customUserPanel.setBorder(BorderFactory.createTitledBorder("自定义用户"));
        
        JTextField customUserInput = new JTextField(15);
        customUserInput.setBorder(BorderFactory.createTitledBorder("用户ID"));
        
        JButton createCustomButton = new JButton("创建用户");
        createCustomButton.addActionListener(e -> {
            String userId = customUserInput.getText().trim();
            if (!userId.isEmpty()) {
                createUserWindow(userId);
                customUserInput.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "请输入用户ID", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 回车键支持
        customUserInput.addActionListener(e -> createCustomButton.doClick());
        
        customUserPanel.add(customUserInput);
        customUserPanel.add(createCustomButton);
        
        // 测试场景按钮区域
        JPanel scenarioPanel = new JPanel();
        scenarioPanel.setLayout(new BoxLayout(scenarioPanel, BoxLayout.Y_AXIS));
        scenarioPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GREEN), "快速测试场景", 
                TitledBorder.LEFT, TitledBorder.TOP));
        
        JButton scenario1Button = new JButton("场景1: 私聊测试 (user1 ↔ user2)");
        scenario1Button.setAlignmentX(Component.CENTER_ALIGNMENT);
        scenario1Button.addActionListener(e -> setupPrivateChatScenario());
        
        JButton scenario2Button = new JButton("场景2: 群聊测试 (user1, user2, user3 → group1)");
        scenario2Button.setAlignmentX(Component.CENTER_ALIGNMENT);
        scenario2Button.addActionListener(e -> setupGroupChatScenario());
        
        JButton scenario3Button = new JButton("场景3: 离线消息测试");
        scenario3Button.setAlignmentX(Component.CENTER_ALIGNMENT);
        scenario3Button.addActionListener(e -> setupOfflineMessageScenario());
        
        scenarioPanel.add(Box.createVerticalStrut(5));
        scenarioPanel.add(scenario1Button);
        scenarioPanel.add(Box.createVerticalStrut(5));
        scenarioPanel.add(scenario2Button);
        scenarioPanel.add(Box.createVerticalStrut(5));
        scenarioPanel.add(scenario3Button);
        scenarioPanel.add(Box.createVerticalStrut(5));
        
        // 系统信息区域
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.BLUE), "系统信息", 
                TitledBorder.LEFT, TitledBorder.TOP));
        
        JTextArea serviceInfo = new JTextArea(
                "用户服务: http://localhost:8090\n" +
                "网关服务: ws://localhost:8080/websocket\n" +
                "消息服务: http://localhost:8092");
        serviceInfo.setEditable(false);
        serviceInfo.setOpaque(false);
        serviceInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        
        infoPanel.add(serviceInfo, BorderLayout.CENTER);
        
        // 添加所有组件到主面板
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(descArea);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(userButtonPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(customUserPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(scenarioPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(infoPanel);
        
        add(mainPanel);
        
        // 程序退出时关闭所有用户窗口
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                // 关闭所有用户窗口
                for (UserWindow window : userWindows) {
                    window.dispose();
                }
                System.exit(0);
            }
        });
    }
    
    /**
     * 创建用户窗口
     */
    private void createUserWindow(String userId) {
        // 检查是否已存在该用户的窗口
        for (UserWindow window : userWindows) {
            if (window.getUserId().equals(userId)) {
                JOptionPane.showMessageDialog(this, 
                        "用户 " + userId + " 的窗口已存在！", 
                        "警告", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        
        UserWindow userWindow = new UserWindow(userId);
        userWindows.add(userWindow);
        userWindow.setVisible(true);
        
        System.out.println("创建用户窗口: " + userId);
    }
    
    /**
     * 设置私聊测试场景
     */
    private void setupPrivateChatScenario() {
        createUserWindow("user1");
        createUserWindow("user2");
        
        // 设置互相为目标用户
        UserWindow user1Window = findUserWindow("user1");
        UserWindow user2Window = findUserWindow("user2");
        
        if (user1Window != null) {
            user1Window.setTargetUserId("user2");
        }
        if (user2Window != null) {
            user2Window.setTargetUserId("user1");
        }
        
        showScenarioInfo("私聊测试场景已设置", 
                "1. user1 和 user2 窗口已创建\n" +
                "2. 请分别点击'连接'按钮登录\n" +
                "3. 在私聊输入框中输入消息进行测试");
    }
    
    /**
     * 设置群聊测试场景
     */
    private void setupGroupChatScenario() {
        createUserWindow("user1");
        createUserWindow("user2");
        createUserWindow("user3");
        
        // 设置群组ID
        String groupId = "group1";
        UserWindow user1Window = findUserWindow("user1");
        UserWindow user2Window = findUserWindow("user2");
        UserWindow user3Window = findUserWindow("user3");
        
        if (user1Window != null) {
            user1Window.setGroupId(groupId);
        }
        if (user2Window != null) {
            user2Window.setGroupId(groupId);
        }
        if (user3Window != null) {
            user3Window.setGroupId(groupId);
        }
        
        showScenarioInfo("群聊测试场景已设置", 
                "1. user1, user2, user3 窗口已创建\n" +
                "2. 群组ID已设置为: " + groupId + "\n" +
                "3. 请分别点击'连接'按钮登录\n" +
                "4. 在群聊输入框中输入消息进行测试");
    }
    
    /**
     * 设置离线消息测试场景
     */
    private void setupOfflineMessageScenario() {
        createUserWindow("user1");
        createUserWindow("user2");
        
        UserWindow user1Window = findUserWindow("user1");
        UserWindow user2Window = findUserWindow("user2");
        
        if (user1Window != null) {
            user1Window.setTargetUserId("user2");
            user1Window.setGroupId("group1");
        }
        if (user2Window != null) {
            user2Window.setTargetUserId("user1");
            user2Window.setGroupId("group1");
        }
        
        showScenarioInfo("离线消息测试场景已设置", 
                "测试步骤:\n" +
                "1. user1 和 user2 都先登录\n" +
                "2. user2 断开连接（模拟离线）\n" +
                "3. user1 发送私聊和群聊消息\n" +
                "4. user2 重新连接，查看是否收到离线消息");
    }
    
    /**
     * 查找用户窗口
     */
    private UserWindow findUserWindow(String userId) {
        return userWindows.stream()
                .filter(window -> window.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 显示场景信息
     */
    private void showScenarioInfo(String title, String content) {
        JOptionPane.showMessageDialog(this, content, title, JOptionPane.INFORMATION_MESSAGE);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TestClientMain().setVisible(true);
        });
    }
}