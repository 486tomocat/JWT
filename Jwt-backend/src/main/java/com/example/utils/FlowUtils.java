package com.example.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 限流通用工具。
 *
 * 核心思想：用 Redis 的「计数 + 过期」模拟一个时间窗口，
 * 把「是否放行」这件事抽象成一个动作（LimitAction），
 * 不同业务只要传入不同的频率/时间和超限后的处理策略即可。
 */
@Slf4j
@Component
public class FlowUtils {

    @Resource
    StringRedisTemplate template;

    /** 默认策略：超过频率直接拒绝，不做额外惩罚 */
    private static final LimitAction DEFAULT_ACTION = overclock -> !overclock;

    /**
     * 单次冷却：blockTime 秒内只允许通过一次。
     * 典型场景：60 秒内只能发一次验证码。
     */
    public boolean limitOnceCheck(String key, int blockTime) {
        return this.internalCheck(key, 1, blockTime, DEFAULT_ACTION);
    }

    /**
     * 单次冷却 + 阶梯封禁。
     * 超过 frequency 次后不仅拒绝，还把封禁时间从 baseTime 延长到 upgradeTime。
     * 典型场景：别人盗刷接口疯狂申请令牌，第一次封 10 秒，还刷就封 300 秒。
     */
    public boolean limitOnceUpgradeCheck(String key, int frequency, int baseTime, int upgradeTime) {
        return this.internalCheck(key, frequency, baseTime, overclock -> {
            if (overclock) {
                template.opsForValue().set(key, "1", upgradeTime, TimeUnit.SECONDS);
            }
            return false;
        });
    }

    /**
     * 时间窗口内计数限制：period 秒内最多 frequency 次，超出仅拒绝。
     * 典型场景：每用户每小时最多发 3 篇帖子。
     */
    public boolean limitPeriodCounterCheck(String key, int frequency, int period) {
        return this.internalCheck(key, frequency, period, DEFAULT_ACTION);
    }

    /**
     * 时间窗口内计数限制 + 拉黑。
     * 超出后把 blockKey 写进 Redis 拉黑 blockTime 秒。
     * 典型场景：某 IP 3 秒内请求超过 10 次，封禁 30 秒。
     */
    public boolean limitPeriodCheck(String counterKey, String blockKey,
                                    int blockTime, int frequency, int period) {
        return this.internalCheck(counterKey, frequency, period, overclock -> {
            if (overclock) {
                template.opsForValue().set(blockKey, "", blockTime, TimeUnit.SECONDS);
            }
            return !overclock;
        });
    }

    /**
     * 统一实现：key 不存在 → 首次访问，设为 1 并设置窗口过期时间；
     * key 已存在 → 自增，交给 action 判断是否超限。
     */
    private boolean internalCheck(String key, int frequency, int period, LimitAction action) {
        if (Boolean.TRUE.equals(template.hasKey(key))) {
            Long value = Optional.ofNullable(template.opsForValue().increment(key)).orElse(0L);
            // 自愈：发现计数 key 没有过期时间就补一个。
            // 为什么会出现「没有 TTL 的 key」：hasKey 与 increment 是两次独立的 Redis 往返（非原子）。
            // 如果 key 恰好在这两步之间过期，INCR 会新建一个**永不过期**的 key；
            // 此后它永远命中，调用方就被永久限流——验证码、登录都会一直提示「请求频繁」。
            // 实测就踩到过：某个 IP 的 verify:email:limit 变成 ttl=-1、计数 25，再也发不出验证码。
            // 根治要靠 Lua 把「判断+自增+设过期」做成一次原子操作（见补全讲解），
            // 这里先做一层成本极低的自愈兜底。
            Long ttl = template.getExpire(key);
            if (ttl == null || ttl < 0) {
                template.expire(key, period, TimeUnit.SECONDS);
            }
            return action.run(value > frequency);
        } else {
            template.opsForValue().set(key, "1", period, TimeUnit.SECONDS);
            return true;
        }
    }

    /** 限制策略：参数表示「是否超频」，返回是否放行 */
    private interface LimitAction {
        boolean run(boolean overclock);
    }
}
