package com.example.controller.admin;

import com.example.entity.PageRestBean;
import com.example.entity.RestBean;
import com.example.entity.dto.EmailRecord;
import com.example.service.EmailService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 · 邮件记录。
 * 这张表的存在意义就是让 MQ 里那条消息「可追踪」，失败能被发现并重发。
 */
@RestController
@RequestMapping("/api/admin/email")
public class EmailAdminController {

    @Resource
    EmailService service;

    @GetMapping("/list")
    public PageRestBean<EmailRecord> listEmailRecord(@RequestParam @Min(value = 0, message = "参数不能为负") int page,
                                                     @RequestParam @Min(value = 1, message = "参数必须大于 0") int size) {
        return PageRestBean.success(service.listEmailRecord(page, size));
    }

    @GetMapping("/resend")
    public RestBean<Void> resendEmailRecord(@RequestParam @Min(value = 1, message = "参数必须大于 0") int id) {
        return service.resendEmailRecord(id)
                ? RestBean.success()
                : RestBean.failure(400, "邮件记录不存在，重发失败");
    }
}
