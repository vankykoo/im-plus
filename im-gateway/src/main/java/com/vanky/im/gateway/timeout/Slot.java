package com.vanky.im.gateway.timeout;

import lombok.extern.slf4j.Slf4j;

/**
 * 时间轮槽
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 时间轮中的每个槽，管理一个双向链表的超时任务
 */
@Slf4j
public class Slot {
    
    /**
     * 链表头节点（哨兵节点）
     */
    private final TimerTask head;
    
    /**
     * 链表尾节点（哨兵节点）
     */
    private final TimerTask tail;
    
    /**
     * 槽级别的锁，减少锁竞争
     */
    private final Object lock = new Object();
    
    /**
     * 当前槽中的任务数量
     */
    private volatile int taskCount = 0;
    
    /**
     * 构造函数，初始化哨兵节点
     */
    public Slot() {
        // 创建哨兵节点
        this.head = new TimerTask(null, null, null, 0);
        this.tail = new TimerTask(null, null, null, 0);
        
        // 连接哨兵节点
        head.setNext(tail);
        tail.setPrev(head);
    }
    
    /**
     * 添加任务到槽的末尾
     * 
     * @param task 要添加的任务
     */
    public void addTask(TimerTask task) {
        if (task == null || task.isCancelled()) {
            return;
        }
        
        synchronized (lock) {
            // 插入到尾部（tail前面）
            TimerTask lastTask = tail.getPrev();
            
            task.setPrev(lastTask);
            task.setNext(tail);
            lastTask.setNext(task);
            tail.setPrev(task);
            
            taskCount++;
            
            log.debug("添加任务到槽 - 任务ID: {}, 用户: {}, 槽任务数: {}", 
                    task.getAckId(), task.getUserId(), taskCount);
        }
    }
    
    /**
     * 从槽中移除任务
     * 
     * @param task 要移除的任务
     * @return true if removed successfully
     */
    public boolean removeTask(TimerTask task) {
        if (task == null) {
            return false;
        }
        
        synchronized (lock) {
            // 检查任务是否在链表中
            if (task.getPrev() == null || task.getNext() == null) {
                return false;
            }
            
            // 从链表中移除
            task.getPrev().setNext(task.getNext());
            task.getNext().setPrev(task.getPrev());
            
            // 清理任务的链表指针
            task.setPrev(null);
            task.setNext(null);
            
            taskCount--;
            
            log.debug("从槽中移除任务 - 任务ID: {}, 用户: {}, 槽任务数: {}", 
                    task.getAckId(), task.getUserId(), taskCount);
            
            return true;
        }
    }
    
    /**
     * 处理槽中的所有任务
     * 
     * @param taskHandler 任务处理器
     */
    public void processTasks(TaskHandler taskHandler) {
        synchronized (lock) {
            TimerTask current = head.getNext();
            
            while (current != tail) {
                TimerTask next = current.getNext(); // 提前保存下一个节点
                
                try {
                    if (current.isCancelled()) {
                        // 移除已取消的任务
                        removeTaskUnsafe(current);
                    } else if (current.isReadyToExecute()) {
                        // 任务准备执行
                        removeTaskUnsafe(current);
                        taskHandler.handle(current);
                    } else {
                        // 减少圈数
                        current.decrementCycle();
                    }
                } catch (Exception e) {
                    log.error("处理任务时发生异常 - 任务ID: {}, 用户: {}", 
                            current.getAckId(), current.getUserId(), e);
                    // 移除异常任务
                    removeTaskUnsafe(current);
                }
                
                current = next;
            }
        }
    }
    
    /**
     * 不加锁的移除任务（内部使用）
     * 
     * @param task 要移除的任务
     */
    private void removeTaskUnsafe(TimerTask task) {
        if (task.getPrev() != null && task.getNext() != null) {
            task.getPrev().setNext(task.getNext());
            task.getNext().setPrev(task.getPrev());
            task.setPrev(null);
            task.setNext(null);
            taskCount--;
        }
    }
    
    /**
     * 获取槽中的任务数量
     * 
     * @return 任务数量
     */
    public int getTaskCount() {
        return taskCount;
    }
    
    /**
     * 检查槽是否为空
     * 
     * @return true if empty
     */
    public boolean isEmpty() {
        synchronized (lock) {
            return head.getNext() == tail;
        }
    }
    
    /**
     * 清空槽中的所有任务
     */
    public void clear() {
        synchronized (lock) {
            TimerTask current = head.getNext();
            
            while (current != tail) {
                TimerTask next = current.getNext();
                current.cancel();
                current = next;
            }
            
            head.setNext(tail);
            tail.setPrev(head);
            taskCount = 0;
            
            log.debug("清空槽中的所有任务");
        }
    }
    
    /**
     * 任务处理器接口
     */
    @FunctionalInterface
    public interface TaskHandler {
        void handle(TimerTask task);
    }
    
    @Override
    public String toString() {
        return String.format("Slot{taskCount=%d, isEmpty=%s}", taskCount, isEmpty());
    }
}
