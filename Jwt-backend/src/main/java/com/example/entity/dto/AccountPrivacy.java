package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.entity.BaseData;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

/**
 * 用户隐私开关：true = 公开，false = 仅自己可见。
 *
 * 主键就是用户 id（不是自增），一个用户一行；
 * 没这一行表示「还没设置过」，按全部公开处理。
 */
@Data
@NoArgsConstructor
@TableName("db_account_privacy")
public class AccountPrivacy implements BaseData {

    @TableId(type = IdType.INPUT)
    Integer id;
    boolean phone = true;
    boolean email = true;
    boolean wx = true;
    boolean qq = true;
    boolean gender = true;

    public AccountPrivacy(Integer id) {
        this.id = id;
    }

    /**
     * 返回被用户设为「不公开」的字段名。
     * 用反射扫 boolean 字段 —— 以后加开关不用记得改这里。
     */
    public List<String> hiddenFields() {
        List<String> hidden = new LinkedList<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            try {
                if (field.getType().equals(boolean.class) && !field.getBoolean(this)) {
                    hidden.add(field.getName());
                }
            } catch (Exception ignored) {
                // 反射取值失败就当它是公开的，不要把整页资料干掉
            }
        }
        return hidden;
    }
}
