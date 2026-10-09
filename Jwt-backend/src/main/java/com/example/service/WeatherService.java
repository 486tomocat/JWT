package com.example.service;

import com.example.entity.vo.response.WeatherVO;

public interface WeatherService {

    /**
     * 查询城市当前天气。
     *
     * @param city 城市名（中文或拼音都行，Open-Meteo 的 geocoding 支持）
     * @return 查到返回数据，查不到返回 null（由 Controller 转成友好提示）
     */
    WeatherVO query(String city);
}
