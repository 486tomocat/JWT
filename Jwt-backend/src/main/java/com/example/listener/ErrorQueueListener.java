package com.example.listener;

import com.example.entity.dto.EmailRecord;
import com.example.mapper.EmailRecordMapper;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者。
 *
 * 到这里说明这封邮件已经「救不回来了」：要么 3 分钟内没人消费（消费者挂了），
 * 要么消费时抛了拒绝异常。这里只做一件事——把记录状态改成 2（失败），
 * 让管理后台能筛出来，并且允许人工点「重发」。
 */
@Slf4j
@Component
@RabbitListener(queues = Const.MQ_ERROR)
public class ErrorQueueListener {

    @Resource
    EmailRecordMapper recordMapper;

    @RabbitHandler
    public void saveErrorToDatabase(EmailRecord record) {
        log.error("邮件进入死信队列，标记为发送失败: id={}, email={}", record.getId(), record.getEmail());
        EmailRecord update = new EmailRecord();
        update.setId(record.getId());
        update.setStatus(2);
        recordMapper.updateById(update);
    }
}
