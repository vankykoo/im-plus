package com.vanky.im.gateway.util;

import com.vanky.im.common.constant.TopicConstants;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.common.protocol.body.TopicList;

import java.util.Arrays;
import java.util.List;

/**
 * RocketMQ Topic创建工具
 * 直接运行main方法即可在指定NameServer上创建IM系统所需的Topic。
 *
 * 依赖：rocketmq-client, rocketmq-tools
 *
 * @author vanky
 */
public class RocketMQTopicCreator {
    // NameServer地址（请根据实际情况修改）
    private static final String NAMESRV_ADDR = "192.168.10.6:9876";
    // 需要创建的Topic列表
    private static final List<String> TOPICS = Arrays.asList(
            "conversation_im_topic",
            "user_im_topic",
            TopicConstants.TOPIC_MESSAGE_P2P,
            TopicConstants.TOPIC_MESSAGE_GROUP,
            TopicConstants.TOPIC_CONVERSATION_MESSAGE,
            TopicConstants.TOPIC_PUSH_TO_GATEWAY
    );

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "list".equalsIgnoreCase(args[0])) {
            listTopics();
            return;
        }
        DefaultMQAdminExt admin = new DefaultMQAdminExt();
        admin.setNamesrvAddr(NAMESRV_ADDR);
        admin.setInstanceName("topicCreatorInstance");
        try {
            admin.start();
            for (String topic : TOPICS) {
                try {
                    admin.createTopic("", topic, 8); // 8个队列，可根据需要调整
                    System.out.println("[OK] Topic created: " + topic);
                } catch (Exception e) {
                    System.err.println("[WARN] Topic may already exist or error: " + topic + " - " + e.getMessage());
                }
            }
        } finally {
            admin.shutdown();
        }
        System.out.println("All topic creation requests finished.");
    }

    /**
     * 列出当前NameServer下所有Topic
     */
    private static void listTopics() throws Exception {
        DefaultMQAdminExt admin = new DefaultMQAdminExt();
        admin.setNamesrvAddr(NAMESRV_ADDR);
        admin.setInstanceName("topicListInstance");
        try {
            admin.start();
            TopicList topicList = admin.fetchAllTopicList();
            System.out.println("===== Available Topics =====");
            for (String topic : topicList.getTopicList()) {
                System.out.println(topic);
            }
            System.out.println("===========================");
        } finally {
            admin.shutdown();
        }
    }
} 