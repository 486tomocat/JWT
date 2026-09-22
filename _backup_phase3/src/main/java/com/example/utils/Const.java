package com.example.utils;

/**
 * 全局常量。
 * 统一收口字符串 key，避免散落在各处写错（比如日志/邮件里少一个冒号）。
 */
public final class Const {

    // ==================== JWT 令牌 ====================
    /** 令牌黑名单前缀：登出后把 jti 写进来 */
    public static final String JWT_BLACK_LIST = "jwt:blacklist:";
    /** 令牌申请频率计数前缀：防止同一用户疯狂刷令牌 */
    public static final String JWT_FREQUENCY = "jwt:frequency:";

    // ==================== 邮件验证码 ====================
    /** 按 IP 限制验证码发送频率 */
    public static final String VERIFY_EMAIL_LIMIT = "verify:email:limit:";
    /** 验证码本身，3 分钟过期 */
    public static final String VERIFY_EMAIL_DATA = "verify:email:data:";

    // ==================== 全局限流 ====================
    public static final String FLOW_LIMIT_COUNTER = "flow:counter:";
    public static final String FLOW_LIMIT_BLOCK = "flow:block:";
    /** 限流过滤器优先级（数字越小越先执行） */
    public static final int ORDER_LIMIT = -101;
    /** 跨域过滤器优先级 */
    public static final int ORDER_CORS = -102;

    // ==================== 请求上下文属性 ====================
    /** JwtAuthorizeFilter 解析出 userId 后放进 request 的 key */
    public static final String ATTR_USER_ID = "id";

    // ==================== 角色 ====================
    public static final String ROLE_DEFAULT = "user";
    public static final String ROLE_ADMIN = "admin";

    // ==================== 论坛模块（阶段 2 使用） ====================
    /** 帖子列表分页缓存前缀 */
    public static final String FORUM_TOPIC_PREVIEW_CACHE = "topic:preview:";
    /** 发帖频率限制：每用户每小时最多 N 篇 */
    public static final String FORUM_TOPIC_CREATE_COUNTER = "forum:topic:create:";
    /** 评论频率限制：每用户每分钟最多 N 条 */
    public static final String FORUM_TOPIC_COMMENT_COUNTER = "forum:topic:comment:";

    private Const() {
        // 常量类不允许实例化
    }
}
