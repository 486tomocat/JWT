package com.example.entity.vo.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
public class TopicDetailVO {
    Integer id;
    String title;
    String content;
    Integer type;
    Date time;
    User user;
    Interact interact;
    /** 点赞总数 —— 列表项本来就有，详情也补上，前端两处的按钮表现才一致 */
    Integer like;
    /** 收藏总数 */
    Integer collect;
    Long comments;
    Integer locked;

    /** 当前登录用户对这篇帖子的互动状态 */
    @Data
    @AllArgsConstructor
    public static class Interact {
        Boolean like;
        Boolean collect;
    }

    /** 楼主信息 */
    @Data
    public static class User {
        Integer id;
        String username;
        String avatar;
        String desc;
        Integer gender;
        String qq;
        String wx;
        String phone;
        String email;
    }
}
