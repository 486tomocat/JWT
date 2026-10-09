import {get} from "@/net";

/**
 * 查天气。
 * 不传 city 时后端用配置里的默认城市（application-dev.yml 的 forum.weather.default-city）。
 */
export const apiWeather = (city, success, failure) =>
    get(`/api/weather${city ? '?city=' + encodeURIComponent(city) : ''}`, success, failure)
