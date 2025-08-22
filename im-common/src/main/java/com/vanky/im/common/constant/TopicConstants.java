package com.vanky.im.common.constant;

/**
 * RocketMQ Topic 常量定义
 * @author vanky
 * @create 2025/7/20
 */
public class TopicConstants {
    
    /**
     * 私聊消息主题
     */
    public static final String TOPIC_MESSAGE_P2P = "TOPIC_MESSAGE_P2P";
    
    /**
     * 群聊消息主题
     */
    public static final String TOPIC_MESSAGE_GROUP = "TOPIC_MESSAGE_GROUP";
    
    /**
     * 统一会话消息主题（包含私聊和群聊）
     */
    public static final String TOPIC_CONVERSATION_MESSAGE = "TOPIC_CONVERSATION_MESSAGE";

    /**
     * ACK消息专用主题（消息确认、批量确认、群聊会话确认）
     */
    public static final String TOPIC_MESSAGE_ACK = "TOPIC_MESSAGE_ACK";

    /**
     * 网关推送消息主题
     */
    public static final String TOPIC_PUSH_TO_GATEWAY = "TOPIC_PUSH_TO_GATEWAY";

   /**
    * 网关推送消息主题前缀
    */
   public static final String TOPIC_PUSH_TO_GATEWAY_PREFIX = "TOPIC_PUSH_TO_GATEWAY_";

    /**
     * IM消息主题（包含聊天消息和ACK确认消息）
     */
    public static final String IM_MESSAGE_TOPIC = "IM_MESSAGE_TOPIC";
    
    /**
     * 私聊消息消费者组
     */
    public static final String CONSUMER_GROUP_MESSAGE_P2P = "im-message-consumer-group";
    
    /**
     * 群聊消息消费者组
     */
    public static final String CONSUMER_GROUP_MESSAGE_GROUP = "im-message-group-consumer";
    
    /**
     * 统一会话消息消费者组
     */
    public static final String CONSUMER_GROUP_CONVERSATION_MESSAGE = "im-conversation-message-consumer";

    /**
     * ACK消息消费者组
     */
    public static final String CONSUMER_GROUP_MESSAGE_ACK = "im-message-ack-consumer";

    /**
     * 网关推送消费者组
     */
    public static final String CONSUMER_GROUP_GATEWAY_PUSH = "gateway-push-consumer-group";
    
    /**
     * 私有构造函数，防止实例化
     */
    private TopicConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}