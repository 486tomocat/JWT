package com.example.entity;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 分页响应体。
 *
 * 为什么不直接复用 {@link RestBean}：分页接口除了 code/message 还固定要带
 * total（总条数）和 page（当前页），塞进业务 VO 里会让 VO 语义不纯。
 * 单独抽一个泛型分页响应，管理后台的列表接口就能统一成一种形状。
 *
 * 返回结构与 {@link RestBean} 保持一致（code / data / message），
 * 前端那套 axios 拦截器不用改。
 */
public record PageRestBean<T>(int code, PageList<T> data, String message) {

    /** 真正承载列表的那一层，对应 JSON 里的 data.list / data.total / data.page */
    public record PageList<T>(List<T> list, long total, long page) {}

    public static <T> PageRestBean<T> success(List<T> list, long total, long page) {
        return new PageRestBean<>(200, new PageList<>(list, total, page), "请求成功");
    }

    public static <T> PageRestBean<T> success(Page<T> page) {
        return success(page.getRecords(), page.getTotal(), page.getCurrent());
    }

    public static <T> PageRestBean<T> failure(int code, String message) {
        return new PageRestBean<>(code, null, message);
    }
}
