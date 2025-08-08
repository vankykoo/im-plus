package com.vanky.im.gateway.conversation;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.gateway.server.processor.client.GroupMsgProcessor;
import com.vanky.im.gateway.server.processor.client.PrivateMsgProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话工作线程池
 * 使用固定大小的线程池和哈希分配策略，确保同一会话的消息串行处理
 * 
 * @author vanky
 * @create 2025/8/7
 * @description 管理会话消息的串行化处理，每个工作线程处理多个会话的队列
 */
@Slf4j
@Component
public class ConversationWorkerPool {
    
    @Autowired
    private ConversationProcessorConfig config;
    
    @Autowired
    private PrivateMsgProcessor privateMsgProcessor;
    
    @Autowired
    private GroupMsgProcessor groupMsgProcessor;
    
    /**
     * 工作线程池
     */
    private ExecutorService[] workers;
    
    /**
     * 每个工作线程对应的会话队列映射
     * workers[i] 对应 queueMaps[i]
     */
    private ConcurrentHashMap<String, BlockingQueue<ConversationMessage>>[] queueMaps;
    
    /**
     * 线程池是否已启动
     */
    private volatile boolean started = false;
    
    /**
     * 统计信息
     */
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final AtomicLong totalMessagesSubmitted = new AtomicLong(0);
    private final AtomicInteger activeConversations = new AtomicInteger(0);
    
    /**
     * 初始化工作线程池
     */
    @PostConstruct
    @SuppressWarnings("unchecked")
    public void init() {
        if (!config.isEnabled()) {
            log.info("会话级串行化处理已禁用，跳过ConversationWorkerPool初始化");
            return;
        }
        
        try {
            int poolSize = config.getWorkerPoolSize();
            
            // 初始化工作线程池
            workers = new ExecutorService[poolSize];
            queueMaps = new ConcurrentHashMap[poolSize];
            
            for (int i = 0; i < poolSize; i++) {
                queueMaps[i] = new ConcurrentHashMap<>();
                workers[i] = createWorkerExecutor(i);
                // 启动工作线程
                startWorkerThread(i);
            }

            started = true;
            log.info("ConversationWorkerPool初始化完成 - 工作线程数: {}, 队列容量: {}",
                    poolSize, config.getQueueCapacity());
                    
        } catch (Exception e) {
            log.error("ConversationWorkerPool初始化失败", e);
            throw new RuntimeException("Failed to initialize ConversationWorkerPool", e);
        }
    }
    
    /**
     * 关闭工作线程池
     */
    @PreDestroy
    public void destroy() {
        if (!started) {
            return;
        }
        
        log.info("正在关闭ConversationWorkerPool...");
        started = false;
        
        if (workers != null) {
            for (ExecutorService worker : workers) {
                if (worker != null) {
                    worker.shutdown();
                }
            }
            
            // 等待所有线程完成
            long shutdownTimeout = config.getShutdownTimeoutMs();
            for (int i = 0; i < workers.length; i++) {
                try {
                    if (!workers[i].awaitTermination(shutdownTimeout, TimeUnit.MILLISECONDS)) {
                        log.warn("工作线程 {} 未能在超时时间内完成，强制关闭", i);
                        workers[i].shutdownNow();
                    }
                } catch (InterruptedException e) {
                    log.warn("等待工作线程 {} 关闭时被中断", i);
                    workers[i].shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        log.info("ConversationWorkerPool已关闭 - 总处理消息数: {}, 总提交消息数: {}", 
                totalMessagesProcessed.get(), totalMessagesSubmitted.get());
    }
    
    /**
     * 提交消息到对应的工作线程
     * 
     * @param conversationId 会话ID
     * @param message 会话消息
     * @return true如果提交成功，false如果提交失败
     */
    public boolean submitMessage(String conversationId, ConversationMessage message) {
        if (!started) {
            log.warn("ConversationWorkerPool未启动，无法提交消息");
            return false;
        }
        
        try {
            // 使用哈希算法分配到工作线程
            int workerIndex = Math.abs(conversationId.hashCode()) % workers.length;
            
            // 获取或创建队列
            BlockingQueue<ConversationMessage> queue = getOrCreateQueue(conversationId, workerIndex);
            if (queue == null) {
                log.warn("无法创建队列，消息提交失败 - 会话ID: {}", conversationId);
                return false;
            }
            
            // 提交消息到队列
            boolean offered = queue.offer(message);
            if (offered) {
                totalMessagesSubmitted.incrementAndGet();
                log.info("消息已提交到工作线程 {} - 会话ID: {}, 队列长度: {}, 消息类型: {}",
                         workerIndex, conversationId, queue.size(), message.getChatMessage().getType());
            } else {
                log.warn("队列已满，消息提交失败 - 会话ID: {}, 工作线程: {}", conversationId, workerIndex);
            }
            
            return offered;
            
        } catch (Exception e) {
            log.error("提交消息异常 - 会话ID: {}", conversationId, e);
            return false;
        }
    }
    
    /**
     * 获取或创建指定会话的队列
     * 
     * @param conversationId 会话ID
     * @param workerIndex 工作线程索引
     * @return 队列，如果创建失败则返回null
     */
    private BlockingQueue<ConversationMessage> getOrCreateQueue(String conversationId, int workerIndex) {
        ConcurrentHashMap<String, BlockingQueue<ConversationMessage>> queueMap = queueMaps[workerIndex];
        
        return queueMap.computeIfAbsent(conversationId, k -> {
            // 检查是否超过最大会话数限制
            if (activeConversations.get() >= config.getMaxConversations()) {
                log.warn("已达到最大会话数限制 {}, 拒绝创建新队列 - 会话ID: {}", 
                        config.getMaxConversations(), conversationId);
                return null;
            }
            
            BlockingQueue<ConversationMessage> newQueue = new LinkedBlockingQueue<>(config.getQueueCapacity());
            activeConversations.incrementAndGet();
            
            if (config.isVerboseLogging()) {
                log.debug("为会话创建新队列 - 会话ID: {}, 工作线程: {}, 活跃会话数: {}", 
                         conversationId, workerIndex, activeConversations.get());
            }
            
            return newQueue;
        });
    }
    
    /**
     * 创建工作线程执行器
     *
     * @param workerIndex 工作线程索引
     * @return 执行器
     */
    private ExecutorService createWorkerExecutor(int workerIndex) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, config.getWorkerThreadPrefix() + workerIndex);
            thread.setDaemon(false); // 非守护线程，确保消息处理完成
            return thread;
        });
    }

    /**
     * 启动工作线程
     *
     * @param workerIndex 工作线程索引
     */
    private void startWorkerThread(int workerIndex) {
        log.info("正在启动工作线程 {} ...", workerIndex);
        workers[workerIndex].submit(() -> {
            log.info("工作线程 {} 的任务已提交到ExecutorService", workerIndex);
            runWorker(workerIndex);
        });
        log.info("工作线程 {} 启动请求已发送", workerIndex);
    }

    /**
     * 工作线程主循环
     *
     * @param workerIndex 工作线程索引
     */
    private void runWorker(int workerIndex) {
        log.info("工作线程 {} 启动", workerIndex);
        ConcurrentHashMap<String, BlockingQueue<ConversationMessage>> queueMap = queueMaps[workerIndex];

        while (started && !Thread.currentThread().isInterrupted()) {
            try {
                // 轮询所有队列，处理消息
                boolean hasMessage = false;

                // 创建队列快照，避免并发修改问题
                var queueSnapshot = new java.util.HashMap<>(queueMap);
                log.debug("工作线程 {} 轮询开始 - 当前队列数: {}", workerIndex, queueSnapshot.size());

                for (var entry : queueSnapshot.entrySet()) {
                    String conversationId = entry.getKey();
                    BlockingQueue<ConversationMessage> queue = entry.getValue();

                    // 处理队列中的所有消息
                    ConversationMessage message;
                    while ((message = queue.poll()) != null) {
                        hasMessage = true;
                        log.info("工作线程 {} 开始处理消息 - 会话ID: {}, 消息类型: {}, 队列剩余: {}",
                                workerIndex, conversationId, message.getChatMessage().getType(), queue.size());
                        processMessage(message, workerIndex);
                    }
                }

                // 如果没有消息，短暂休眠避免CPU空转
                if (!hasMessage) {
                    Thread.sleep(50); // 增加休眠时间，减少CPU占用
                }

            } catch (InterruptedException e) {
                log.info("工作线程 {} 被中断，准备退出", workerIndex);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("工作线程 {} 处理异常", workerIndex, e);
                // 异常后短暂休眠，避免无限循环
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("工作线程 {} 退出", workerIndex);
    }

    /**
     * 处理单个消息
     *
     * @param message 会话消息
     * @param workerIndex 工作线程索引
     */
    private void processMessage(ConversationMessage message, int workerIndex) {
        long startTime = System.currentTimeMillis();

        try {
            // 检查消息是否过期
            long messageAge = message.getAge();
            if (messageAge > config.getMessageTimeoutMs()) {
                log.warn("消息处理超时，跳过处理 - 会话ID: {}, 消息年龄: {}ms, 工作线程: {}",
                        message.getConversationId(), messageAge, workerIndex);
                return;
            }

            // 检查Channel是否仍然活跃
            if (!message.isChannelActive()) {
                log.warn("Channel已断开，跳过消息处理 - 会话ID: {}, 工作线程: {}",
                        message.getConversationId(), workerIndex);
                return;
            }

            // 根据消息类型调用对应的处理器
            int messageType = message.getChatMessage().getType();
            if (messageType == MessageTypeConstants.PRIVATE_CHAT_MESSAGE) {
                privateMsgProcessor.process(message.getChatMessage(), message.getChannel());
            } else if (messageType == MessageTypeConstants.GROUP_CHAT_MESSAGE) {
                groupMsgProcessor.process(message.getChatMessage(), message.getChannel());
            } else {
                log.warn("不支持的消息类型 - 类型: {}, 会话ID: {}, 工作线程: {}",
                        messageType, message.getConversationId(), workerIndex);
                return;
            }

            totalMessagesProcessed.incrementAndGet();

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("消息处理完成 - 会话ID: {}, 处理时间: {}ms, 工作线程: {}, 消息类型: {}",
                     message.getConversationId(), processingTime, workerIndex, message.getChatMessage().getType());

        } catch (Exception e) {
            log.error("消息处理异常 - 会话ID: {}, 工作线程: {}",
                     message.getConversationId(), workerIndex, e);
        }
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStatistics() {
        return String.format(
            "ConversationWorkerPool[submitted=%d, processed=%d, activeConversations=%d, workers=%d]",
            totalMessagesSubmitted.get(), totalMessagesProcessed.get(),
            activeConversations.get(), workers != null ? workers.length : 0
        );
    }
}
