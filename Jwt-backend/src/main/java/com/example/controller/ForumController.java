package com.example.controller;

import com.example.entity.PageRestBean;
import com.example.entity.RestBean;
import com.example.entity.dto.Interact;
import com.example.entity.vo.request.AddCommentVO;
import com.example.entity.vo.request.TopicCreateVO;
import com.example.entity.vo.request.TopicUpdateVO;
import com.example.entity.vo.response.CommentVO;
import com.example.entity.vo.response.TopicDetailVO;
import com.example.entity.vo.response.TopicPreviewVO;
import com.example.entity.vo.response.TopicTopVO;
import com.example.entity.vo.response.TopicTypeVO;
import com.example.service.TopicService;
import com.example.utils.Const;
import com.example.utils.ControllerUtils;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 论坛相关接口。
 *
 * 所有需要登录的接口都用 @RequestAttribute(Const.ATTR_USER_ID) 拿当前用户 id，
 * 这个属性是 JwtAuthorizeFilter 在校验令牌后挂到 request 上的。
 */
@RestController
@RequestMapping("/api/forum")
public class ForumController {

    @Resource
    TopicService topicService;

    @Resource
    ControllerUtils utils;

    @GetMapping("/types")
    public RestBean<List<TopicTypeVO>> listTypes() {
        return RestBean.success(topicService.listTypes()
                .stream()
                .map(type -> type.asViewObject(TopicTypeVO.class))
                .toList());
    }

    @PostMapping("/create-topic")
    public RestBean<Void> createTopic(@Valid @RequestBody TopicCreateVO vo,
                                      @RequestAttribute(Const.ATTR_USER_ID) int id) {
        return utils.messageHandle(() -> topicService.createTopic(id, vo));
    }

    @GetMapping("/list-topic")
    public RestBean<List<TopicPreviewVO>> listTopic(@RequestParam @Min(value = 0, message = "参数不能为负") int page,
                                                    @RequestParam @Min(value = 0, message = "参数不能为负") int type) {
        return RestBean.success(topicService.listTopicByPage(page + 1, type));
    }

    /**
     * 关键词搜索（Elasticsearch）。
     * page 从 0 开始、每页 10 条，与 /list-topic 一致；响应带 total（来自 ES 的命中数）。
     */
    @GetMapping("/search")
    public PageRestBean<TopicPreviewVO> search(@RequestParam String keyword,
                                               @RequestParam @Min(value = 0, message = "参数不能为负") int page) {
        return topicService.searchTopic(keyword, page);
    }

    @GetMapping("/top-topic")
    public RestBean<List<TopicTopVO>> topTopic() {
        return RestBean.success(topicService.listTopTopics());
    }

    @GetMapping("/topic")
    public RestBean<TopicDetailVO> topic(@RequestParam @Min(value = 0, message = "参数不能为负") int tid,
                                         @RequestAttribute(Const.ATTR_USER_ID) int id) {
        TopicDetailVO topic = topicService.getTopic(tid, id);
        if (topic != null) {
            return RestBean.success(topic);
        } else {
            return RestBean.failure(404, "帖子不存在或已被屏蔽");
        }
    }

    @GetMapping("/interact")
    public RestBean<Void> interact(@RequestParam @Min(value = 0, message = "参数不能为负") int tid,
                                   @RequestParam @Pattern(regexp = "(like|collect)", message = "参数取值不支持") String type,
                                   @RequestParam boolean state,
                                   @RequestAttribute(Const.ATTR_USER_ID) int id) {
        topicService.interact(new Interact(tid, id, new Date(), type), state);
        return RestBean.success();
    }

    @GetMapping("/collects")
    public RestBean<List<TopicPreviewVO>> collects(@RequestAttribute(Const.ATTR_USER_ID) int id) {
        return RestBean.success(topicService.listTopicCollects(id));
    }

    @PostMapping("/update-topic")
    public RestBean<Void> updateTopic(@Valid @RequestBody TopicUpdateVO vo,
                                      @RequestAttribute(Const.ATTR_USER_ID) int id) {
        return utils.messageHandle(() -> topicService.updateTopic(id, vo));
    }

    @PostMapping("/add-comment")
    public RestBean<Void> addComment(@Valid @RequestBody AddCommentVO vo,
                                     @RequestAttribute(Const.ATTR_USER_ID) int id) {
        return utils.messageHandle(() -> topicService.createComment(id, vo));
    }

    @GetMapping("/comments")
    public RestBean<List<CommentVO>> comments(@RequestParam @Min(value = 0, message = "参数不能为负") int tid,
                                              @RequestParam @Min(value = 0, message = "参数不能为负") int page) {
        return RestBean.success(topicService.comments(tid, page + 1));
    }

    @GetMapping("/delete-comment")
    public RestBean<Void> deleteComment(@RequestParam @Min(value = 0, message = "参数不能为负") int id,
                                        @RequestAttribute(Const.ATTR_USER_ID) int uid) {
        topicService.deleteComment(id, uid);
        return RestBean.success();
    }

    /**
     * 某个用户的帖子。
     * 不传 uid = 我的帖子；传了 = 看别人的（个人主页里的「TA 的帖子」用）。
     * 帖子本来就是公开内容，所以这里不做隐私过滤。
     */
    @GetMapping("/user-topic")
    public RestBean<List<TopicPreviewVO>> userTopic(@RequestParam(required = false) Integer uid,
                                                    @RequestAttribute(Const.ATTR_USER_ID) int selfId) {
        return RestBean.success(topicService.listTopicByUser(uid == null ? selfId : uid));
    }

    @GetMapping("/delete-topic")
    public RestBean<Void> deleteTopic(@RequestParam @Min(value = 0, message = "参数不能为负") int tid,
                                      @RequestAttribute(Const.ATTR_USER_ID) int uid) {
        topicService.deleteTopic(tid, uid);
        return RestBean.success();
    }
}
