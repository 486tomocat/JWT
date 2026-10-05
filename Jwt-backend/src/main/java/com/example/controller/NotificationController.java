package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.vo.response.NotificationVO;
import com.example.service.NotificationService;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内通知接口。
 * 所有方法的 uid 都取自令牌（@RequestAttribute），不接受前端传入，
 * 从根上杜绝「查看/删除别人通知」的越权。
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @Resource
    NotificationService service;

    @GetMapping("/list")
    public RestBean<List<NotificationVO>> listNotification(@RequestAttribute(Const.ATTR_USER_ID) int id) {
        return RestBean.success(service.findUserNotification(id));
    }

    @GetMapping("/delete")
    public RestBean<Void> deleteNotification(@RequestParam @Min(value = 0, message = "参数不能为负") int id,
                                             @RequestAttribute(Const.ATTR_USER_ID) int uid) {
        service.deleteUserNotification(id, uid);
        return RestBean.success();
    }

    @GetMapping("/delete-all")
    public RestBean<Void> deleteAllNotification(@RequestAttribute(Const.ATTR_USER_ID) int uid) {
        service.deleteUserAllNotification(uid);
        return RestBean.success();
    }
}
