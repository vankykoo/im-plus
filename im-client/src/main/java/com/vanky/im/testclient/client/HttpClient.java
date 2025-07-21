package com.vanky.im.testclient.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP客户端，用于调用用户服务API
 */
public class HttpClient {
    
    private static final String USER_SERVICE_BASE_URL = "http://localhost:8090";
    
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
}