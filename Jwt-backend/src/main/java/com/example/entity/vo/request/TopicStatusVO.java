package com.example.entity.vo.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

/** 管理后台切换帖子状态（置顶 / 锁定 / 隐藏）的通用入参 */
@Data
public class TopicStatusVO {

    @Min(value = 1, message = "帖子 id 非法")
    int tid;

    /** true = 打开该状态，false = 关闭 */
    boolean status;
}
