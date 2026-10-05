package com.example.controller.admin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.entity.PageRestBean;
import com.example.entity.RestBean;
import com.example.entity.vo.request.TopicStatusVO;
import com.example.entity.vo.request.TopicTypeCreateVO;
import com.example.entity.vo.response.TopicPreviewVO;
import com.example.entity.vo.response.TopicTypeVO;
import com.example.service.TopicService;
import com.example.utils.ProhibitedUtils;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 · 论坛管理：帖子状态、分类维护、违禁词维护。
 */
@RestController
@RequestMapping("/api/admin/forum")
public class ForumAdminController {

    @Resource
    TopicService service;

    @Resource
    ProhibitedUtils prohibitedUtils;

    @GetMapping("/list")
    public PageRestBean<TopicPreviewVO> list(@RequestParam @Min(value = 0, message = "参数不能为负") int page,
                                             @RequestParam @Min(value = 1, message = "参数必须大于 0") int size,
                                             @RequestParam(required = false) String keyword) {
        JSONObject result = service.listAllTopicByPage(page, size, keyword);
        JSONArray array = result.getJSONArray("list");
        return PageRestBean.success(
                array == null ? List.of() : array.toList(TopicPreviewVO.class),
                result.getIntValue("total"),
                result.getIntValue("page")
        );
    }

    @GetMapping("/delete")
    public RestBean<Void> delete(@RequestParam @Min(value = 1, message = "参数必须大于 0") int tid) {
        service.deleteTopic(tid);
        return RestBean.success();
    }

    @PostMapping("/top")
    public RestBean<Void> setTop(@RequestBody @Valid TopicStatusVO vo) {
        service.setTopicTop(vo.getTid(), vo.isStatus());
        return RestBean.success();
    }

    @PostMapping("/locked")
    public RestBean<Void> setLocked(@RequestBody @Valid TopicStatusVO vo) {
        service.setTopicLocked(vo.getTid(), vo.isStatus());
        return RestBean.success();
    }

    @PostMapping("/invisible")
    public RestBean<Void> setInvisible(@RequestBody @Valid TopicStatusVO vo) {
        service.setTopicInvisible(vo.getTid(), vo.isStatus());
        return RestBean.success();
    }

    // ==================== 违禁词 ====================

    @GetMapping("/prohibited-list")
    public RestBean<List<String>> getProhibitedList() {
        return RestBean.success(prohibitedUtils.getProhibitedWords());
    }

    /** 整体覆盖词表（前端传一个字符串数组） */
    @PostMapping("/prohibited-save")
    public RestBean<Void> saveProhibitedList(@RequestBody List<String> words) {
        prohibitedUtils.setProhibitedWords(words);
        return RestBean.success();
    }

    // ==================== 分类维护 ====================

    @PostMapping("/create-type")
    public RestBean<Void> createType(@RequestBody @Valid TopicTypeCreateVO vo) {
        service.createTopicType(vo);
        return RestBean.success();
    }

    @PostMapping("/update-type")
    public RestBean<Void> updateType(@RequestBody TopicTypeVO vo) {
        service.updateTopicType(vo);
        return RestBean.success();
    }

    @GetMapping("/delete-type")
    public RestBean<Void> deleteType(@RequestParam @Min(value = 1, message = "参数必须大于 0") int tid) {
        service.deleteTopicType(tid);
        return RestBean.success();
    }

    /** 把某个帖子挪到另一个分类下 */
    @GetMapping("/change-topic-type")
    public RestBean<Void> changeTopicType(@RequestParam @Min(value = 1, message = "参数必须大于 0") int tid,
                                          @RequestParam @Min(value = 1, message = "参数必须大于 0") int type) {
        service.changeTopicType(tid, type);
        return RestBean.success();
    }
}
