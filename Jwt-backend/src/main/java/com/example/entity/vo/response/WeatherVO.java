package com.example.entity.vo.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 天气（数据来自 Open-Meteo，免费且无需 API key） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherVO {

    /** 城市名（回显用，避免前端自己拼） */
    String city;

    /** 温度（摄氏度），保留一位小数 */
    Double temperature;

    /** 天气描述的中文，例如「多云」「小雨」 */
    String weather;

    /** 原始 WMO 天气码，前端想按它换图标时用 */
    Integer weatherCode;

    /** 风速 km/h */
    Double windSpeed;

    /** 观测时间（Open-Meteo 给的本地时间字符串） */
    String time;
}
