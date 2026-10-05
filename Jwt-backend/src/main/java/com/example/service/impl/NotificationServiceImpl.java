package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.Notification;
import com.example.entity.vo.response.NotificationVO;
import com.example.mapper.NotificationMapper;
import com.example.service.NotificationService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    @Override
    public List<NotificationVO> findUserNotification(int uid) {
        // 按时间倒序：新通知在最上面，这是通知列表最起码的体验
        return this.list(Wrappers.<Notification>query()
                        .eq("uid", uid)
                        .orderByDesc("time"))
                .stream()
                .map(notification -> notification.asViewObject(NotificationVO.class))
                .toList();
    }

    @Override
    public void deleteUserNotification(int id, int uid) {
        // 同时用 id 和 uid 限定：否则改一下 id 就能删掉别人的通知（水平越权）
        this.remove(Wrappers.<Notification>query().eq("id", id).eq("uid", uid));
    }

    @Override
    public void deleteUserAllNotification(int uid) {
        this.remove(Wrappers.<Notification>query().eq("uid", uid));
    }

    @Override
    public void addNotification(int uid, String title, String content, String type, String url) {
        Notification notification = new Notification();
        notification.setUid(uid);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setUrl(url);
        // 参考实现漏了这一行，通知时间全是 null
        notification.setTime(new Date());
        this.save(notification);
    }
}
