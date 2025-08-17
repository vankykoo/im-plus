package com.vanky.im.testclient.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;

/**
 * HTTP客户端，用于调用用户服务API
 */
public class HttpClient {

    private static final String USER_SERVICE_BASE_URL = "http://localhost:8090";
    private static final String MESSAGE_SERVICE_BASE_URL = "http://localhost:8100";

    private final java.net.http.HttpClient client;
    
    public HttpClient() {
        this.client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * 用户登录
     * @param userId 用户ID
     * @param password 密码
     * @return 登录响应，包含token
     */
    public LoginResponse login(String userId, String password) {
        try {
            // 构建JSON请求体
            String jsonBody = "{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USER_SERVICE_BASE_URL + "/users/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                System.err.println("登录失败，HTTP状态码: " + response.statusCode());
                return null;
            }
            
            String responseBody = response.body();
            System.out.println("登录响应: " + responseBody);
            
            // 简单解析JSON响应
            if (parseJsonField(responseBody, "code").equals("200")) {
                String userId1 = parseNestedJsonField(responseBody, "data", "userId");
                String username = parseNestedJsonField(responseBody, "data", "username");
                String token = parseNestedJsonField(responseBody, "data", "token");
                return new LoginResponse(userId1, username, token);
            } else {
                String message = parseJsonField(responseBody, "message");
                System.err.println("登录失败: " + message);
                return null;
            }
        } catch (Exception e) {
            System.err.println("登录请求异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 用户注册
     * @param userId 用户ID
     * @param username 用户名
     * @param password 密码
     * @return 是否注册成功
     */
    public boolean register(String userId, String username, String password) {
        try {
            // 构建JSON请求体
            String jsonBody = "{\"userId\":\"" + userId + "\",\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USER_SERVICE_BASE_URL + "/users/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                System.err.println("注册失败，HTTP状态码: " + response.statusCode());
                return false;
            }
            
            String responseBody = response.body();
            System.out.println("注册响应: " + responseBody);
            
            return parseJsonField(responseBody, "code").equals("200");
        } catch (Exception e) {
            System.err.println("注册请求异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 创建群聊会话
     * @param groupName 群聊名称
     * @param groupDesc 群聊描述
     * @param members 群聊成员列表（包含群主）
     * @param creatorId 创建者ID
     * @return 创建成功返回群聊ID，失败返回null
     */
    public String createGroup(String groupName, String groupDesc, List<String> members, String creatorId) {
        try {
            // 构建JSON请求体
            StringBuilder membersJson = new StringBuilder("[");
            for (int i = 0; i < members.size(); i++) {
                if (i > 0) membersJson.append(",");
                membersJson.append("\"").append(members.get(i)).append("\"");
            }
            membersJson.append("]");

            String jsonBody = "{" +
                    "\"conversationName\":\"" + groupName + "\"," +
                    "\"conversationDesc\":\"" + groupDesc + "\"," +
                    "\"conversationType\":\"GROUP\"," +
                    "\"creatorId\":\"" + creatorId + "\"," +
                    "\"members\":" + membersJson.toString() +
                    "}";

            System.out.println("创建群聊请求: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MESSAGE_SERVICE_BASE_URL + "/api/conversations/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("创建群聊响应状态: " + response.statusCode());
            System.out.println("创建群聊响应内容: " + response.body());

            if (response.statusCode() != 200) {
                System.err.println("创建群聊失败，HTTP状态码: " + response.statusCode());
                return null;
            }

            String responseBody = response.body();

            // 检查响应是否成功
            String code = parseJsonField(responseBody, "code");
            if ("200".equals(code)) {
                // 尝试从data字段中获取conversationId
                String conversationId = parseNestedJsonField(responseBody, "data", "conversationId");
                if (conversationId.isEmpty()) {
                    // 如果data是字符串类型，直接获取
                    conversationId = parseJsonField(responseBody, "data");
                }
                return conversationId.isEmpty() ? null : conversationId;
            } else {
                String message = parseJsonField(responseBody, "message");
                System.err.println("创建群聊失败: " + message);
                return null;
            }

        } catch (Exception e) {
            System.err.println("创建群聊请求异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 简单解析JSON字段值
     */
    private String parseJsonField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // 尝试解析数字或布尔值
        pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*([^,}]*)");
        matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
    
    /**
     * 解析嵌套JSON字段值
     */
    private String parseNestedJsonField(String json, String parentField, String childField) {
        // 先找到父字段的值
        Pattern parentPattern = Pattern.compile("\"" + parentField + "\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher parentMatcher = parentPattern.matcher(json);
        if (parentMatcher.find()) {
            String parentValue = parentMatcher.group(1);
            return parseJsonField("{" + parentValue + "}", childField);
        }
        return "";
    }

    /**
     * 从JSON字符串中提取一个完整的JSON对象（作为字符串）
     */
    private String extractJsonObject(String json, String fieldName) {
        // 查找字段名的位置
        String fieldPattern = "\"" + fieldName + "\"\\s*:\\s*";
        Pattern pattern = Pattern.compile(fieldPattern);
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        int startIndex = matcher.end();
        if (startIndex >= json.length() || json.charAt(startIndex) != '{') {
            return null;
        }

        // 使用括号计数来找到完整的JSON对象
        int braceCount = 0;
        int endIndex = startIndex;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startIndex; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        endIndex = i + 1;
                        break;
                    }
                }
            }
        }

        if (braceCount == 0 && endIndex > startIndex) {
            return json.substring(startIndex, endIndex);
        }

        return null;
    }

    /**
     * 将简单的JSON对象字符串解析为Map<String, Long>
     */
    private Map<String, Long> parseMap(String jsonMap) {
        Map<String, Long> map = new HashMap<>();
        if (jsonMap == null || jsonMap.isEmpty() || !jsonMap.startsWith("{") || !jsonMap.endsWith("}")) {
            return map;
        }
        // 移除花括号
        String content = jsonMap.substring(1, jsonMap.length() - 1);
        // 按逗号分割键值对
        String[] pairs = content.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                try {
                    String key = keyValue[0].trim().replace("\"", "");
                    Long value = Long.parseLong(keyValue[1].trim());
                    map.put(key, value);
                } catch (NumberFormatException e) {
                    System.err.println("解析Map值失败: " + pair);
                }
            }
        }
        return map;
    }

    /**
     * 解析群聊消息列表（特殊格式：按会话ID分组）
     */
    private java.util.List<Object> parseGroupMessageList(String responseBody) {
        java.util.List<Object> messages = new java.util.ArrayList<>();
        
        try {
            // 群聊消息格式：{"conversations":{"group_xxx":[{message1},{message2}]},"latestSeqs":{},"totalCount":1,"hasMore":false}
            // 提取conversations对象
            String conversationsStr = extractJsonObject(responseBody, "conversations");

            if (conversationsStr == null || conversationsStr.trim().isEmpty()) {
                System.out.println("[DEBUG] parseGroupMessageList - conversations对象为空或null");
                return messages;
            }
            
            // 直接在conversations字符串中查找群聊会话
            // 使用更简单的方法：查找"group_"开头的键
            int startIndex = 0;
            while (true) {
                int groupIndex = conversationsStr.indexOf("\"group_", startIndex);
                if (groupIndex == -1) break;
                
                // 找到会话ID的结束位置
                int idEndIndex = conversationsStr.indexOf("\"", groupIndex + 1);
                if (idEndIndex == -1) break;
                
                String conversationId = conversationsStr.substring(groupIndex + 1, idEndIndex);
                
                // 找到对应的消息数组
                int arrayStartIndex = conversationsStr.indexOf("[", idEndIndex);
                if (arrayStartIndex == -1) {
                    startIndex = idEndIndex + 1;
                    continue;
                }
                
                // 找到数组的结束位置（需要处理嵌套的[]）
                int arrayEndIndex = findMatchingBracket(conversationsStr, arrayStartIndex);
                if (arrayEndIndex == -1) {
                    startIndex = idEndIndex + 1;
                    continue;
                }
                
                String messagesArrayContent = conversationsStr.substring(arrayStartIndex + 1, arrayEndIndex);
                System.out.println("[DEBUG] parseGroupMessageList - 会话 " + conversationId + " 的消息数组: " + messagesArrayContent);
                
                if (!messagesArrayContent.trim().isEmpty()) {
                    // 分割消息数组
                    String[] messageItems = splitJsonArray(messagesArrayContent);
                    System.out.println("[DEBUG] parseGroupMessageList - 会话 " + conversationId + " 的消息数量: " + messageItems.length);
                    
                    for (int i = 0; i < messageItems.length; i++) {
                        String messageItem = messageItems[i];
                        System.out.println("[DEBUG] parseGroupMessageList - 处理消息项[" + i + "]: " + messageItem);
                        
                        if (messageItem.trim().isEmpty()) continue;
                        
                        Map<String, Object> messageMap = new HashMap<>();
                        messageMap.put("msgId", parseJsonField(messageItem, "msgId"));
                        messageMap.put("fromUserId", parseJsonField(messageItem, "fromUserId"));  // 修正字段名
                        messageMap.put("toUserId", parseJsonField(messageItem, "toUserId"));      // 修正字段名
                        messageMap.put("content", parseJsonField(messageItem, "content"));
                        messageMap.put("msgType", parseJsonField(messageItem, "msgType"));        // 修正字段名
                        messageMap.put("conversationId", parseJsonField(messageItem, "conversationId"));
                        messageMap.put("seq", parseJsonField(messageItem, "seq"));
                        messageMap.put("createTime", parseJsonField(messageItem, "createTime"));
                        messageMap.put("groupId", parseJsonField(messageItem, "groupId"));
                        
                        System.out.println("[DEBUG] parseGroupMessageList - 解析后的消息Map: " + messageMap);
                        System.out.println("[DEBUG] parseGroupMessageList - 字段验证 - fromUserId: " + messageMap.get("fromUserId") +
                                         ", msgType: " + messageMap.get("msgType") +
                                         ", conversationId: " + messageMap.get("conversationId"));
                        messages.add(messageMap);
                    }
                }
                
                startIndex = arrayEndIndex + 1;
            }
        } catch (Exception e) {
            System.err.println("解析群聊消息列表失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("[DEBUG] parseGroupMessageList - 最终返回消息数量: " + messages.size());
        return messages;
    }

    /**
     * 解析私聊消息列表
     */
    private java.util.List<Object> parseMessageList(String responseBody) {
        java.util.List<Object> messages = new java.util.ArrayList<>();
        
        try {
            System.out.println("[DEBUG] parseMessageList - 原始响应: " + responseBody);
            
            // 提取messages数组
            String messagesStr = extractJsonArray(responseBody, "messages");
            System.out.println("[DEBUG] parseMessageList - 提取的messages数组: " + messagesStr);
            
            if (messagesStr == null || messagesStr.trim().isEmpty()) {
                System.out.println("[DEBUG] parseMessageList - messages数组为空或null");
                return messages;
            }
            
            // 简单解析消息数组
            String[] messageItems = splitJsonArray(messagesStr);
            System.out.println("[DEBUG] parseMessageList - 分割后的消息项数量: " + messageItems.length);
            
            for (int i = 0; i < messageItems.length; i++) {
                String messageItem = messageItems[i];
                System.out.println("[DEBUG] parseMessageList - 处理消息项[" + i + "]: " + messageItem);
                
                if (messageItem.trim().isEmpty()) continue;
                
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("msgId", parseJsonField(messageItem, "msgId"));
                messageMap.put("fromUserId", parseJsonField(messageItem, "fromUserId"));  // 修正字段名
                messageMap.put("toUserId", parseJsonField(messageItem, "toUserId"));      // 修正字段名
                messageMap.put("content", parseJsonField(messageItem, "content"));
                messageMap.put("msgType", parseJsonField(messageItem, "msgType"));        // 修正字段名
                messageMap.put("conversationId", parseJsonField(messageItem, "conversationId"));
                messageMap.put("seq", parseJsonField(messageItem, "seq"));
                messageMap.put("createTime", parseJsonField(messageItem, "createTime"));
                
                System.out.println("[DEBUG] parseMessageList - 解析后的消息Map: " + messageMap);
                messages.add(messageMap);
            }
        } catch (Exception e) {
            System.err.println("解析消息列表失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("[DEBUG] parseMessageList - 最终返回消息数量: " + messages.size());
        return messages;
    }

    /**
     * 提取JSON数组字段
     */
    private String extractJsonArray(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\\[(.*?)\\]";
        Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 分割JSON数组
     */
    private String[] splitJsonArray(String arrayContent) {
        if (arrayContent == null || arrayContent.trim().isEmpty()) {
            return new String[0];
        }
        
        java.util.List<String> items = new java.util.ArrayList<>();
        int braceCount = 0;
        int start = 0;
        
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    items.add(arrayContent.substring(start, i + 1));
                    start = i + 1;
                    // 跳过逗号和空格
                    while (start < arrayContent.length() && 
                           (arrayContent.charAt(start) == ',' || Character.isWhitespace(arrayContent.charAt(start)))) {
                        start++;
                    }
                    i = start - 1;
                }
            }
        }
        
        return items.toArray(new String[0]);
    }
    
    /**
     * 找到匹配的括号位置
     */
    private int findMatchingBracket(String str, int startIndex) {
        if (startIndex >= str.length() || str.charAt(startIndex) != '[') {
            return -1;
        }
        
        int bracketCount = 1;
        for (int i = startIndex + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
                if (bracketCount == 0) {
                    return i;
                }
            }
        }
        
        return -1;
    }
    
    // ========== 会话概览同步API ==========

    /**
     * 同步用户会话概览
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 会话概览同步响应
     */
    public ConversationSyncResponse syncConversations(String userId, Integer limit) {
        try {
            // 构建JSON请求体 - 注意userId需要是数字类型，不是字符串
            String jsonBody = "{\"userId\":" + userId + ",\"limit\":" + (limit != null ? limit : 100) + "}";
            String url = MESSAGE_SERVICE_BASE_URL + "/api/conversations/sync";

            System.out.println("发送会话概览同步请求 - URL: " + url);
            System.out.println("请求体: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            System.out.println("正在发送会话同步HTTP请求...");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("收到会话同步HTTP响应，状态码: " + response.statusCode());

            if (response.statusCode() != 200) {
                System.err.println("会话概览同步失败，HTTP状态码: " + response.statusCode());
                return new ConversationSyncResponse(false, "HTTP错误: " + response.statusCode());
            }

            String responseBody = response.body();
            System.out.println("会话概览同步响应: " + responseBody);

            // 解析响应 - 检查code字段而不是success字段
            String codeStr = parseJsonField(responseBody, "code");
            if (!"200".equals(codeStr)) {
                String errorMessage = parseJsonField(responseBody, "message");
                return new ConversationSyncResponse(false, errorMessage);
            }

            return new ConversationSyncResponse(true, null, responseBody);

        } catch (Exception e) {
            System.err.println("会话概览同步请求异常: " + e.getMessage());
            e.printStackTrace();
            return new ConversationSyncResponse(false, "网络异常: " + e.getMessage());
        }
    }

    // ========== 离线消息同步相关API（私聊写扩散） ==========

    /**
     * 检查用户是否需要进行离线消息同步
     * @param userId 用户ID
     * @param lastSyncSeq 客户端最后同步序列号
     * @return 同步检查响应
     */
    public SyncCheckResponse checkSyncNeeded(String userId, Long lastSyncSeq) {
        try {
            // 构建JSON请求体
            String jsonBody = "{\"userId\":\"" + userId + "\",\"lastSyncSeq\":" + lastSyncSeq + "}";
            String url = MESSAGE_SERVICE_BASE_URL + "/api/messages/sync-check";

            System.out.println("发送同步检查请求 - URL: " + url);
            System.out.println("请求体: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            System.out.println("正在发送HTTP请求...");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("收到HTTP响应，状态码: " + response.statusCode());

            if (response.statusCode() != 200) {
                System.err.println("同步检查失败，HTTP状态码: " + response.statusCode());
                return null;
            }

            String responseBody = response.body();
            System.out.println("同步检查响应: " + responseBody);

            // 解析响应
            boolean success = parseJsonField(responseBody, "success").equals("true");
            if (success) {
                boolean syncNeeded = parseJsonField(responseBody, "syncNeeded").equals("true");
                String targetSeqStr = parseJsonField(responseBody, "targetSeq");
                String currentSeqStr = parseJsonField(responseBody, "currentSeq");

                Long targetSeq = targetSeqStr.isEmpty() ? 0L : Long.parseLong(targetSeqStr);
                Long currentSeq = currentSeqStr.isEmpty() ? 0L : Long.parseLong(currentSeqStr);

                return new SyncCheckResponse(success, syncNeeded, targetSeq, currentSeq, null);
            } else {
                String errorMessage = parseJsonField(responseBody, "errorMessage");
                return new SyncCheckResponse(false, false, 0L, 0L, errorMessage);
            }
        } catch (java.net.ConnectException e) {
            System.err.println("连接失败 - 服务器可能未启动: " + MESSAGE_SERVICE_BASE_URL);
            System.err.println("连接异常详情: " + e.getMessage());
            return new SyncCheckResponse(false, false, 0L, 0L, "连接失败，服务器未启动: " + e.getMessage());
        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("请求超时: " + e.getMessage());
            return new SyncCheckResponse(false, false, 0L, 0L, "请求超时: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("同步检查请求异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return new SyncCheckResponse(false, false, 0L, 0L, "网络异常: " + e.getMessage());
        }
    }

    /**
     * 批量拉取用户的离线消息
     * @param userId 用户ID
     * @param fromSeq 起始序列号
     * @param limit 拉取数量限制
     * @return 批量拉取响应
     */
    public PullMessagesResponse pullMessagesBatch(String userId, Long fromSeq, Integer limit) {
        try {
            // 构建JSON请求体
            String jsonBody = "{\"userId\":\"" + userId + "\",\"fromSeq\":" + fromSeq + ",\"limit\":" + limit + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MESSAGE_SERVICE_BASE_URL + "/api/messages/pull-batch"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("批量拉取消息失败，HTTP状态码: " + response.statusCode());
                return null;
            }

            String responseBody = response.body();
            System.out.println("批量拉取消息响应: " + responseBody);

            // 解析响应
            boolean success = parseJsonField(responseBody, "success").equals("true");
            if (success) {
                boolean hasMore = parseJsonField(responseBody, "hasMore").equals("true");
                String nextSeqStr = parseJsonField(responseBody, "nextSeq");
                String countStr = parseJsonField(responseBody, "count");

                Long nextSeq = (nextSeqStr.isEmpty() || "null".equals(nextSeqStr)) ? null : Long.parseLong(nextSeqStr);
                Integer count = countStr.isEmpty() ? 0 : Integer.parseInt(countStr);

                // 解析消息列表
                java.util.List<Object> messages = parseMessageList(responseBody);

                return new PullMessagesResponse(success, hasMore, nextSeq, count, null, messages);
            } else {
                String errorMessage = parseJsonField(responseBody, "errorMessage");
                return new PullMessagesResponse(false, false, null, 0, errorMessage);
            }
        } catch (Exception e) {
            System.err.println("批量拉取消息请求异常: " + e.getMessage());
            e.printStackTrace();
            return new PullMessagesResponse(false, false, null, 0, "网络异常: " + e.getMessage());
        }
    }

    // ========== 响应类定义 ==========

    /**
     * 登录响应类
     */
    public static class LoginResponse {
        private String userId;
        private String username;
        private String token;

        public LoginResponse(String userId, String username, String token) {
            this.userId = userId;
            this.username = username;
            this.token = token;
        }

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getToken() { return token; }
    }

    /**
     * 同步检查响应类
     */
    public static class SyncCheckResponse {
        private boolean success;
        private boolean syncNeeded;
        private Long targetSeq;
        private Long currentSeq;
        private String errorMessage;

        public SyncCheckResponse(boolean success, boolean syncNeeded, Long targetSeq, Long currentSeq, String errorMessage) {
            this.success = success;
            this.syncNeeded = syncNeeded;
            this.targetSeq = targetSeq;
            this.currentSeq = currentSeq;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public boolean isSyncNeeded() { return syncNeeded; }
        public Long getTargetSeq() { return targetSeq; }
        public Long getCurrentSeq() { return currentSeq; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 批量拉取消息响应类
     */
    public static class PullMessagesResponse {
        private boolean success;
        private boolean hasMore;
        private Long nextSeq;
        private Integer count;
        private String errorMessage;
        private java.util.List<Object> messages; // 添加消息列表字段
        private java.util.List<String> msgIds; // 用于批量ACK

        public PullMessagesResponse(boolean success, boolean hasMore, Long nextSeq, Integer count, String errorMessage) {
            this(success, hasMore, nextSeq, count, errorMessage, new java.util.ArrayList<>());
        }

        public PullMessagesResponse(boolean success, boolean hasMore, Long nextSeq, Integer count, String errorMessage, java.util.List<Object> messages) {
            this.success = success;
            this.hasMore = hasMore;
            this.nextSeq = nextSeq;
            this.count = count;
            this.errorMessage = errorMessage;
            this.messages = messages != null ? messages : new java.util.ArrayList<>();
            
            // 从消息中提取msgId
            this.msgIds = new java.util.ArrayList<>();
            if (this.messages != null) {
                for (Object msg : this.messages) {
                    if (msg instanceof Map) {
                        Map<String, Object> msgMap = (Map<String, Object>) msg;
                        Object msgId = msgMap.get("msgId");
                        if (msgId != null) {
                            this.msgIds.add(msgId.toString());
                        }
                    }
                }
            }
        }

        public boolean isSuccess() { return success; }
        public boolean isHasMore() { return hasMore; }
        public Long getNextSeq() { return nextSeq; }
        public Integer getCount() { return count; }
        public String getErrorMessage() { return errorMessage; }
        public java.util.List<Object> getMessages() { return messages; }
        public java.util.List<String> getMsgIds() { return msgIds; }
    }

    // ========== 群聊消息同步相关API（读扩散） ==========

    /**
     * 拉取群聊消息（读扩散模式）
     * @param userId 用户ID
     * @param conversationSeqMap 群聊同步点映射，Key为conversationId，Value为lastSeq
     * @param limit 每个会话的拉取限制
     * @return 群聊消息拉取响应
     */
    public PullGroupMessagesResponse pullGroupMessages(String userId, Map<String, Long> conversationSeqMap, Integer limit) {
        try {
            // 构建JSON请求体
            StringBuilder conversationsJson = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Long> entry : conversationSeqMap.entrySet()) {
                if (!first) conversationsJson.append(",");
                conversationsJson.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                first = false;
            }
            conversationsJson.append("}");

            String jsonBody = "{" +
                    "\"userId\":\"" + userId + "\"," +
                    "\"conversations\":" + conversationsJson.toString() + "," +
                    "\"limit\":" + (limit != null ? limit : 100) +
                    "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MESSAGE_SERVICE_BASE_URL + "/api/group-messages/pull"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("群聊消息拉取失败，HTTP状态码: " + response.statusCode());
                return new PullGroupMessagesResponse("HTTP错误: " + response.statusCode());
            }

            String responseBody = response.body();
            System.out.println("群聊消息拉取响应: " + responseBody);

            // 解析消息数据
            String dataJson = responseBody; // 直接使用响应体作为数据JSON
            int totalCount = 0;
            boolean hasMore = false;
            Map<String, Long> latestSeqMap = new HashMap<>();

            if (dataJson != null && !dataJson.isEmpty()) {
                try {
                    String countStr = parseJsonField(dataJson, "totalCount");
                    if (countStr != null && !countStr.isEmpty()) {
                        totalCount = Integer.parseInt(countStr);
                    }
                    String hasMoreStr = parseJsonField(dataJson, "hasMore");
                    if (hasMoreStr != null && !hasMoreStr.isEmpty()) {
                        hasMore = Boolean.parseBoolean(hasMoreStr);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("解析群聊消息元数据失败: " + e.getMessage());
                }

                // 注意：服务端返回的字段名是 latestSeqs，不是 latestSeqMap
                String seqMapJson = extractJsonObject(dataJson, "latestSeqs");
                System.out.println("[DEBUG] 提取的latestSeqs JSON: " + seqMapJson);
                if (seqMapJson != null && !seqMapJson.isEmpty()) {
                    latestSeqMap = parseMap(seqMapJson);
                    System.out.println("[DEBUG] 解析后的latestSeqMap: " + latestSeqMap);
                } else {
                    System.out.println("[DEBUG] latestSeqs JSON为空或null");
                }
            }

            return new PullGroupMessagesResponse(totalCount, hasMore, responseBody, latestSeqMap);

        } catch (Exception e) {
            System.err.println("群聊消息拉取请求异常: " + e.getMessage());
            e.printStackTrace();
            return new PullGroupMessagesResponse("网络异常: " + e.getMessage());
        }
    }

    /**
     * 批量获取群聊会话的最新序列号
     * @param conversationIds 会话ID列表
     * @return 最新序列号响应
     */
    public GroupLatestSeqsResponse getGroupLatestSeqs(List<String> conversationIds) {
        try {
            // 构建JSON请求体
            StringBuilder idsJson = new StringBuilder("[");
            for (int i = 0; i < conversationIds.size(); i++) {
                if (i > 0) idsJson.append(",");
                idsJson.append("\"").append(conversationIds.get(i)).append("\"");
            }
            idsJson.append("]");

            String jsonBody = "{\"conversationIds\":" + idsJson.toString() + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MESSAGE_SERVICE_BASE_URL + "/api/group-messages/latest-seqs"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("获取群聊最新序列号失败，HTTP状态码: " + response.statusCode());
                return new GroupLatestSeqsResponse(false, "HTTP错误: " + response.statusCode());
            }

            String responseBody = response.body();
            System.out.println("群聊最新序列号响应: " + responseBody);

            return new GroupLatestSeqsResponse(true, null, responseBody);

        } catch (Exception e) {
            System.err.println("获取群聊最新序列号请求异常: " + e.getMessage());
            e.printStackTrace();
            return new GroupLatestSeqsResponse(false, "网络异常: " + e.getMessage());
        }
    }

    // ========== 群聊同步响应类定义 ==========

    /**
     * 群聊消息拉取响应类
     */
    public static class PullGroupMessagesResponse {
        private final String errorMessage;
        private final int totalCount;
        private final boolean hasMore;
        private final String rawResponse; // 原始响应，用于进一步解析
        private final Map<String, Long> latestSeqMap;
        private java.util.List<Object> messages; // 解析后的消息列表

        public PullGroupMessagesResponse(String errorMessage) {
            this(errorMessage, 0, false, null, null);
        }

        public PullGroupMessagesResponse(int totalCount, boolean hasMore, String rawResponse, Map<String, Long> latestSeqMap) {
            this(null, totalCount, hasMore, rawResponse, latestSeqMap);
        }

        public PullGroupMessagesResponse(String errorMessage, int totalCount, boolean hasMore, String rawResponse, Map<String, Long> latestSeqMap) {
            this.errorMessage = errorMessage;
            this.totalCount = totalCount;
            this.hasMore = hasMore;
            this.rawResponse = rawResponse;
            this.latestSeqMap = latestSeqMap;
            this.messages = null; // 延迟解析
        }

        public boolean isSuccess() { return errorMessage == null; }
        public String getErrorMessage() { return errorMessage; }
        public int getTotalCount() { return totalCount; }
        public boolean isHasMore() { return hasMore; }
        public String getRawResponse() { return rawResponse; }
        public Map<String, Long> getLatestSeqMap() { return latestSeqMap; }
        
        /**
         * 获取解析后的消息列表
         * @param httpClient HttpClient实例，用于调用parseGroupMessageList方法
         * @return 解析后的消息列表
         */
        public java.util.List<Object> getMessages(HttpClient httpClient) {
            if (messages == null && rawResponse != null) {
                messages = httpClient.parseGroupMessageList(rawResponse);
            }
            return messages != null ? messages : new java.util.ArrayList<>();
        }
    }

    /**
     * 群聊最新序列号响应类
     */
    public static class GroupLatestSeqsResponse {
        private boolean success;
        private String errorMessage;
        private String rawResponse; // 原始响应，用于进一步解析

        public GroupLatestSeqsResponse(boolean success, String errorMessage) {
            this(success, errorMessage, null);
        }

        public GroupLatestSeqsResponse(boolean success, String errorMessage, String rawResponse) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.rawResponse = rawResponse;
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public String getRawResponse() { return rawResponse; }
    }

    /**
     * 会话概览同步响应类
     */
    public static class ConversationSyncResponse {
        private boolean success;
        private String errorMessage;
        private String rawResponse; // 原始响应，用于进一步解析

        public ConversationSyncResponse(boolean success, String errorMessage) {
            this(success, errorMessage, null);
        }

        public ConversationSyncResponse(boolean success, String errorMessage, String rawResponse) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.rawResponse = rawResponse;
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public String getRawResponse() { return rawResponse; }
    }
}