package com.example.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.entity.dto.EmailRecord;

public interface EmailService {

    /** 落库 + 投递到 mail 队列，由 MailQueueListener 真正发送 */
    void sendVerifyEmail(String type, String email, int code);

    Page<EmailRecord> listEmailRecord(int page, int size);

    boolean resendEmailRecord(int id);
}
