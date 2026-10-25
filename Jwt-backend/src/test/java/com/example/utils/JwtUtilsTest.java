package com.example.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtUtils 单元测试。
 *
 * 不启动 Spring 上下文、不连 Redis：Redis 与限流器都用 Mockito 替身，
 * 只验证「令牌签发 / 校验 / 失效」这段纯逻辑。
 */
class JwtUtilsTest {

    private static final String SECRET = "unit-test-secret-key-0123456789";

    private StringRedisTemplate template;
    private ValueOperations<String, String> valueOps;
    private FlowUtils flowUtils;
    private JwtUtils utils;

    @BeforeEach
    void setUp() {
        template = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOps);

        flowUtils = mock(FlowUtils.class);
        // 默认：未触发频率熔断
        when(flowUtils.limitOnceUpgradeCheck(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(true);

        utils = new JwtUtils();
        utils.key = SECRET;
        utils.expire = 7;
        utils.limitBase = 10;
        utils.limitUpgrade = 300;
        utils.limitFrequency = 30;
        utils.template = template;
        utils.flowUtils = flowUtils;
    }

    private UserDetails user() {
        return User.withUsername("tester@example.com").password("x").roles("user").build();
    }

    @Test
    @DisplayName("签发的令牌可被解析，并还原出 id、用户名与权限")
    void createThenResolve() {
        String token = utils.createJwt(user(), 42, "tester@example.com");
        assertNotNull(token, "正常情况下应签发成功");

        DecodedJWT jwt = utils.resolveJwt("Bearer " + token);
        assertNotNull(jwt, "自己签发的令牌应通过校验");

        assertEquals(42, utils.toId(jwt), "id 声明应被正确还原");
        UserDetails restored = utils.toUser(jwt);
        assertEquals("tester@example.com", restored.getUsername());
        assertTrue(restored.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_user".equals(a.getAuthority())),
                "权限声明应还原为 ROLE_user");
    }

    @Test
    @DisplayName("触发频率熔断时签发返回 null（调用方据此提示稍后再试）")
    void createJwtWhenFrequencyLimited() {
        when(flowUtils.limitOnceUpgradeCheck(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(false);

        assertNull(utils.createJwt(user(), 42, "tester@example.com"));
    }

    @Test
    @DisplayName("换一把密钥签发的令牌验签失败，解析返回 null")
    void resolveForeignToken() {
        JwtUtils other = new JwtUtils();
        other.key = "a-completely-different-secret";
        other.expire = 7;
        other.flowUtils = flowUtils;
        other.template = template;

        String foreign = other.createJwt(user(), 7, "attacker@example.com");
        assertNotNull(foreign);

        assertNull(utils.resolveJwt("Bearer " + foreign), "验签不通过必须视为未登录");
    }

    @Test
    @DisplayName("已过期的令牌解析返回 null")
    void resolveExpiredToken() {
        utils.expire = -1; // 有效期设为负值：签发出来的令牌立即过期
        String token = utils.createJwt(user(), 42, "tester@example.com");
        assertNotNull(token);

        assertNull(utils.resolveJwt("Bearer " + token));
    }

    @Test
    @DisplayName("进入黑名单的令牌解析返回 null（登出 / 封禁即时生效的关键）")
    void resolveBlacklistedToken() {
        String token = utils.createJwt(user(), 42, "tester@example.com");
        String jti = JWT.decode(token).getId();
        when(template.hasKey(Const.JWT_BLACK_LIST + jti)).thenReturn(true);

        assertNull(utils.resolveJwt("Bearer " + token));
    }

    @Test
    @DisplayName("缺少 Bearer 前缀或为 null 时解析返回 null")
    void resolveWithoutBearerPrefix() {
        String token = utils.createJwt(user(), 42, "tester@example.com");

        assertNull(utils.resolveJwt(token), "没有 Bearer 前缀不应被当作有效令牌");
        assertNull(utils.resolveJwt(null));
        assertNull(utils.resolveJwt(""));
    }

    @Test
    @DisplayName("主动失效会把 jti 写进黑名单，TTL 取令牌剩余有效期")
    void invalidateWritesBlacklist() {
        String token = utils.createJwt(user(), 42, "tester@example.com");
        String jti = JWT.decode(token).getId();

        assertTrue(utils.invalidateJwt("Bearer " + token));

        verify(valueOps).set(eq(Const.JWT_BLACK_LIST + jti), eq(""), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("失效一个格式非法的令牌只返回 false，不抛异常")
    void invalidateMalformedToken() {
        assertFalse(utils.invalidateJwt("Bearer not-a-jwt"));
        assertFalse(utils.invalidateJwt(null));
    }
}
