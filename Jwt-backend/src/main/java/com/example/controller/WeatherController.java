package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.vo.response.WeatherVO;
import com.example.service.WeatherService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 天气（数据源 Open-Meteo，无需 API key）。
 *
 * 需要一个默认城市是因为前端首次打开时还不知道该查哪儿 ——
 * 用户切换过城市后，前端会把选择记在 localStorage 里，之后都带上来。
 */
@RestController
@RequestMapping("/api")
public class WeatherController {

    @Value("${forum.weather.default-city}")
    String defaultCity;

    @Resource
    WeatherService weatherService;

    /** 查城市天气；不传 city 就用配置里的默认城市 */
    @GetMapping("/weather")
    public RestBean<WeatherVO> weather(@RequestParam(required = false) String city) {
        String target = (city == null || city.isBlank()) ? defaultCity : city.trim();
        WeatherVO vo = weatherService.query(target);
        if (vo == null) {
            return RestBean.failure(400, "没查到「" + target + "」的天气，换个城市名试试");
        }
        return RestBean.success(vo);
    }
}
