package com.example.utils;

import com.example.entity.RestBean;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Controller 层通用工具。
 *
 * Service 层约定：返回 null 表示成功，返回字符串表示失败原因。
 * 这样 Service 不需要依赖任何 Web 类型，由这里统一翻译成 RestBean。
 */
@Component
public class ControllerUtils {

    public <T> RestBean<T> messageHandle(Supplier<String> action) {
        String message = action.get();
        return message == null ? RestBean.success() : RestBean.failure(400, message);
    }
}
