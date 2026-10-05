package com.example.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

/**
 * 点赞/收藏的互动记录。
 * 不是数据库表实体，而是 Redis 暂存与批量落库时使用的载体。
 */
@Data
@AllArgsConstructor
public class Interact {
    Integer tid;
    Integer uid;
    Date time;
    /** like 或 collect */
    String type;

    /** Redis Hash 里的 field：tid:uid */
    public String toKey() {
        return tid + ":" + uid;
    }

    /** 从 Redis Hash 的 field 还原成对象 */
    public static Interact parseInteract(String str, String type) {
        String[] keys = str.split(":");
        return new Interact(Integer.parseInt(keys[0]), Integer.parseInt(keys[1]), new Date(), type);
    }
}
