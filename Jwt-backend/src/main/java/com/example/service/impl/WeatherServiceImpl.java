package com.example.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.entity.vo.response.WeatherVO;
import com.example.service.WeatherService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 天气查询。数据源是 **Open-Meteo** —— 免费、开放、**不需要注册和 API key**。
 *
 * 两步走：
 *   1. geocoding：城市名 → 经纬度（Open-Meteo 只认坐标，不认城市名）
 *   2. forecast：经纬度 → 当前天气
 *
 * 结果在 Redis 缓存 10 分钟：天气变化本来就慢，没必要每次刷新页面都打两次外部接口
 * （Open-Meteo 免费额度很宽松，但礼貌一点没坏处，也能让页面更快）。
 */
@Slf4j
@Service
public class WeatherServiceImpl implements WeatherService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_PREFIX = "weather:";

    @Value("${forum.weather.geocoding-api}")
    String geocodingApi;

    @Value("${forum.weather.forecast-api}")
    String forecastApi;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    // 用 Spring Boot 自带的 RestClient，不引入任何新依赖
    private final RestClient restClient = RestClient.builder()
            .defaultHeader("User-Agent", "jwt-forum/1.0")
            .build();

    @Override
    public WeatherVO query(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        String key = CACHE_PREFIX + city.trim();

        // ① 先看缓存
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return JSONObject.parseObject(cached, WeatherVO.class);
            }
        } catch (Exception e) {
            // Redis 挂了不该让天气不可用，继续走真实请求
            log.warn("读取天气缓存失败：{}", e.getMessage());
        }

        // ② geocoding：城市名 → 经纬度
        JSONObject place = geocode(city.trim());
        if (place == null) {
            return null;
        }
        double latitude = place.getDoubleValue("latitude");
        double longitude = place.getDoubleValue("longitude");
        // 优先用中文名回显（Open-Meteo 在 language=zh 时会返回中文）
        String displayName = place.getString("name") == null ? city.trim() : place.getString("name");

        // ③ forecast：拿当前天气
        WeatherVO vo = fetchCurrent(displayName, latitude, longitude);
        if (vo == null) {
            return null;
        }

        // ④ 写缓存（失败不影响返回）
        try {
            stringRedisTemplate.opsForValue()
                    .set(key, JSONObject.toJSONString(vo), CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入天气缓存失败：{}", e.getMessage());
        }
        return vo;
    }

    /** 城市名 → 经纬度 */
    private JSONObject geocode(String city) {
        try {
            String body = restClient.get()
                    .uri(geocodingApi + "?name={name}&count=1&language=zh&format=json", city)
                    .retrieve()
                    .body(String.class);
            JSONArray results = JSONObject.parseObject(body).getJSONArray("results");
            if (results == null || results.isEmpty()) {
                return null;
            }
            return results.getJSONObject(0);
        } catch (Exception e) {
            log.error("查询城市坐标失败(city={})：{}", city, e.getMessage());
            return null;
        }
    }

    /** 按经纬度取当前天气 */
    private WeatherVO fetchCurrent(String city, double latitude, double longitude) {
        try {
            String body = restClient.get()
                    .uri(forecastApi + "?latitude={lat}&longitude={lon}"
                                    + "&current=temperature_2m,weather_code,wind_speed_10m&timezone=auto",
                            latitude, longitude)
                    .retrieve()
                    .body(String.class);
            JSONObject current = JSONObject.parseObject(body).getJSONObject("current");
            if (current == null) {
                return null;
            }
            int code = current.getIntValue("weather_code");
            return new WeatherVO(
                    city,
                    current.getDouble("temperature_2m"),
                    describe(code),
                    code,
                    current.getDouble("wind_speed_10m"),
                    current.getString("time")
            );
        } catch (Exception e) {
            log.error("查询天气失败(city={})：{}", city, e.getMessage());
            return null;
        }
    }

    /**
     * WMO 天气码 → 中文。
     * Open-Meteo 返回的是 WMO 标准码（数字），前端拿数字没法展示，这里翻译好。
     */
    private String describe(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "晴间多云";
            case 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61 -> "小雨";
            case 63 -> "中雨";
            case 65 -> "大雨";
            case 66, 67 -> "冻雨";
            case 71 -> "小雪";
            case 73 -> "中雪";
            case 75 -> "大雪";
            case 77 -> "雪粒";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷阵雨";
            case 96, 99 -> "雷阵雨伴冰雹";
            default -> "未知";
        };
    }
}
