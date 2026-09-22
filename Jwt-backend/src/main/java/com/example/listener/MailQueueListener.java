package com.example.listener;

import com.example.entity.dto.EmailRecord;
import com.example.mapper.EmailRecordMapper;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮件发送消费者。
 *
 * 阶段 3 的两个变化：
 * 1. 消息体从 Map 变成 EmailRecord —— 消息里自带数据库主键，发送结果能写回记录表；
 * 2. 发送失败时抛 AmqpRejectAndDontRequeueException 而不是普通异常。
 *    普通异常在默认配置下会被 requeue，消息立刻回到队首再次投递，
 *    形成一个「失败 → 重投 → 再失败」的热循环，把 CPU 打满；
 *    抛这个异常表示「拒绝且不要重新入队」，消息会按队列配置进入死信交换机。
 */
@Slf4j
@Component
@RabbitListener(queues = Const.MQ_MAIL)
public class MailQueueListener {

    @Resource
    JavaMailSender sender;

    @Resource
    EmailRecordMapper recordMapper;

    @Value("${spring.mail.username}")
    String username;

    @RabbitHandler
    public void sendMailMessage(EmailRecord email) {
        try {
            sender.send(createMessage(email));
            email.setStatus(1);
            recordMapper.updateById(email);
            log.info("邮件发送成功，记录ID：{}，收件人：{}", email.getId(), email.getEmail());
        } catch (Exception e) {
            log.error("邮件发送失败，记录ID：{}，收件人：{}，原因：{}", email.getId(), email.getEmail(), e.getMessage());
            // 抛给 RabbitMQ：拒绝且不重新入队 -> 走死信交换机 -> ErrorQueueListener 标记失败
            throw new AmqpRejectAndDontRequeueException("邮件发送失败: " + email.getId(), e);
        }
    }

    private SimpleMailMessage createMessage(EmailRecord email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setSubject(email.getTitle());
        message.setText(email.getContent());
        message.setTo(email.getEmail());
        message.setFrom(username);
        return message;
    }
}
