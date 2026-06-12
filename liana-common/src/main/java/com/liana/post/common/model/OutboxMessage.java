package com.liana.post.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 发件箱消息实体
 * 支持边缘端离线模式下的最终一致性消息投递
 * 
 * 适用场景：
 * - 边缘端（支局）在网络不稳定时，将消息存储到本地数据库
 * - 后续通过定时任务异步补偿发送
 * - 支持指数退避重试和乐观锁并发控制
 */
@TableName("outbox_message")
public class OutboxMessage {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 全局唯一消息ID（UUID/雪花ID） */
    private String msgId;
    
    /** 事件类型：PACKAGE_RECEIVED|OFFICE_HANDOFF|DISPATCH_SUMMARY_APPROVED 等 */
    private String eventType;
    
    /** 序列化后的业务载荷数据（JSON 格式） */
    private String payload;
    
    /** 状态：NEW(新建)→SENDING(发送中)→SUCCESS(成功)→FAILED(永久失败) */
    private String status;
    
    /** 当前重试次数 */
    private Integer retryCount;
    
    /** 最大可重试次数 */
    private Integer maxRetries;
    
    /** 下一次执行补偿发送的时间戳（用于指数退避） */
    private LocalDateTime nextRetryTime;
    
    /** 乐观锁版本号，支撑状态抢占（分布式锁降级） */
    private Integer version;
    
    /** 消息创建时间 */
    private LocalDateTime createdAt;
    
    /** 消息更新时间 */
    private LocalDateTime updatedAt;

    // ==================== Getter & Setter ====================
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    
    public LocalDateTime getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(LocalDateTime nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "OutboxMessage{" +
                "id=" + id +
                ", msgId='" + msgId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", status='" + status + '\'' +
                ", retryCount=" + retryCount +
                '}';
    }
}

