package com.vanky.im.testclient.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP客户端，用于调用用户服务API
 */
public class HttpClient {

    private static final String USER_SERVICE_BASE_URL = "http://localhost:8090";
    private static final String MESSAGE_SERVICE_BASE_URL = "http://localhost:8101";

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
                    .uri(URI.create(MESSAGE_SERVICE_BASE_URL + "/conversations/create"))
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
    
    // ========== 离线消息同步相关API ==========

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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MESSAGE_SERVICE_BASE_URL + "/api/messages/sync-check"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

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
        } catch (Exception e) {
            System.err.println("同步检查请求异常: " + e.getMessage());
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

                return new PullMessagesResponse(success, hasMore, nextSeq, count, null);
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
        }

        public boolean isSuccess() { return success; }
        public boolean isHasMore() { return hasMore; }
        public Long getNextSeq() { return nextSeq; }
        public Integer getCount() { return count; }
        public String getErrorMessage() { return errorMessage; }
        public java.util.List<Object> getMessages() { return messages; }
    }
}