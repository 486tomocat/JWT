package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.entity.dto.EmailRecord;
import com.example.mapper.EmailRecordMapper;
import com.example.service.EmailService;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    @Resource
    AmqpTemplate amqpTemplate;

    @Resource
    EmailRecordMapper recordMapper;

    @Override
    public void sendVerifyEmail(String type, String email, int code) {
        EmailRecord record = switch (type) {
            case "register" -> new EmailRecord(email, "欢迎注册我们的网站",
                    "您的邮件注册验证码为: " + code + "，有效时间 3 分钟，为了保障您的账户安全，请勿向他人泄露验证码信息。");
            case "reset" -> new EmailRecord(email, "您的密码重置邮件",
                    "你好，您正在执行重置密码操作，验证码: " + code + "，有效时间 3 分钟，如非本人操作，请无视。");
            default -> throw new IllegalArgumentException("未知的验证码类型: " + type);
        };
        // 先落库再投递：insert 之后 record.id 被回填，消息里带上 id，
        // 监听器才能反过来更新这条记录的状态（这是「可观测」的关键）。
        recordMapper.insert(record);
        amqpTemplate.convertAndSend(Const.MQ_MAIL, record);
    }

    @Override
    public Page<EmailRecord> listEmailRecord(int page, int size) {
        return recordMapper.selectPage(Page.of(page, size), Wrappers.<EmailRecord>query().orderByDesc("time"));
    }

    @Override
    public boolean resendEmailRecord(int id) {
        EmailRecord record = recordMapper.selectById(id);
        if (record == null) {
            return false;
        }
        record.setStatus(0);
        amqpTemplate.convertAndSend(Const.MQ_MAIL, record);
        recordMapper.updateById(record);
        return true;
    }
}
