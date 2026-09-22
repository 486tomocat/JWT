package com.example.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JWT 令牌工具：签发、校验、主动失效（黑名单）。
 */
@Component
public class JwtUtils {

    @Value("${spring.security.jwt.key}")
    String key;

    @Value("${spring.security.jwt.expire}")
    int expire;

    @Value("${spring.security.jwt.limit.base}")
    int limitBase;

    @Value("${spring.security.jwt.limit.upgrade}")
    int limitUpgrade;

    @Value("${spring.security.jwt.limit.frequency}")
    int limitFrequency;

    @Resource
    StringRedisTemplate template;

    @Resource
    FlowUtils flowUtils;

    /**
     * 让令牌失效：解析出 jti，写进黑名单。
     * 黑名单的 TTL 设为该令牌的「剩余有效期」——令牌本身过期后黑名单自动清理，不会无限膨胀。
     */
    public boolean invalidateJwt(String headerToken) {
        String token = this.convertToken(headerToken);
        if (token == null) return false;
        Algorithm algorithm = Algorithm.HMAC256(key);
        JWTVerifier jwtVerifier = JWT.require(algorithm).build();
        try {
            DecodedJWT jwt = jwtVerifier.verify(token);
            return this.deleteToken(jwt.getId(), jwt.getExpiresAt());
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    /** 过期时间：expire 以「天」为单位 */
    public Date expireTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, expire * 24);
        return calendar.getTime();
    }

    /**
     * 签发令牌。返回 null 表示触发了频率熔断（调用方据此提示用户稍后再试）。
     */
    public String createJwt(UserDetails details, int id, String username) {
        if (!this.frequencyCheck(id)) {
            return null;
        }
        Algorithm algorithm = Algorithm.HMAC256(key);
        return JWT.create()
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("id", id)
                .withClaim("name", username)
                .withClaim("authorities", details.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList())
                .withExpiresAt(this.expireTime())
                .withIssuedAt(new Date())
                .sign(algorithm);
    }

    /**
     * 解析并校验令牌：验签 → 查黑名单 → 查是否过期。
     * 任何一步失败都返回 null（调用方视为未登录）。
     */
    public DecodedJWT resolveJwt(String headerToken) {
        String token = this.convertToken(headerToken);
        if (token == null) return null;
        Algorithm algorithm = Algorithm.HMAC256(key);
        JWTVerifier jwtVerifier = JWT.require(algorithm).build();
        try {
            DecodedJWT verify = jwtVerifier.verify(token);
            if (this.isInvalidToken(verify.getId())) return null;
            Date expiresAt = verify.getExpiresAt();
            return new Date().after(expiresAt) ? null : verify;
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    /** 把令牌里的权限声明还原成 Spring Security 的 UserDetails */
    public UserDetails toUser(DecodedJWT jwt) {
        Map<String, Claim> claims = jwt.getClaims();
        return User.withUsername(claims.get("name").asString())
                .password("*******")
                .authorities(claims.get("authorities").asArray(String.class))
                .build();
    }

    /** 取出令牌中的用户 ID */
    public Integer toId(DecodedJWT jwt) {
        return jwt.getClaims().get("id").asInt();
    }

    /** 频率熔断：同一用户在冷却时间内继续申请令牌 → 阶梯式延长封禁 */
    private boolean frequencyCheck(int id) {
        String key = Const.JWT_FREQUENCY + id;
        return flowUtils.limitOnceUpgradeCheck(key, limitFrequency, limitBase, limitUpgrade);
    }

    /** 写入黑名单，TTL = 令牌剩余有效期 */
    private boolean deleteToken(String uuid, Date time) {
        if (this.isInvalidToken(uuid)) return false;
        long expire = Math.max(time.getTime() - System.currentTimeMillis(), 0);
        template.opsForValue().set(Const.JWT_BLACK_LIST + uuid, "", expire, TimeUnit.MILLISECONDS);
        return true;
    }

    /** 注意：hasKey 返回 Boolean 包装类型，直接拆箱可能 NPE，所以用 Boolean.TRUE.equals */
    private boolean isInvalidToken(String uuid) {
        return Boolean.TRUE.equals(template.hasKey(Const.JWT_BLACK_LIST + uuid));
    }

    /** 从 "Bearer xxx" 中截出真正的令牌 */
    private String convertToken(String headerToken) {
        if (headerToken == null || !headerToken.startsWith("Bearer ")) return null;
        return headerToken.substring(7);
    }
}
