package com.vanky.im.testclient.client;

import java.io.InputStream;
import java.util.Properties;

/**
 * 客户端配置加载类
 */
public class ClientConfig {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ClientConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Sorry, unable to find config.properties");
            } else {
                // 从输入流加载一个properties文件
                properties.load(input);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}