package com.example.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.Interact;
import com.example.entity.dto.Topic;
import com.example.entity.dto.TopicType;
import com.example.entity.vo.request.AddCommentVO;
import com.example.entity.vo.request.TopicCreateVO;
import com.example.entity.vo.request.TopicTypeCreateVO;
import com.example.entity.vo.request.TopicUpdateVO;
import com.example.entity.vo.response.CommentVO;
import com.example.entity.vo.response.TopicDetailVO;
import com.example.entity.PageRestBean;
import com.example.entity.vo.response.TopicPreviewVO;
import com.example.entity.vo.response.TopicTopVO;
import com.example.entity.vo.response.TopicTypeVO;

import java.util.List;

/**
 * 论坛业务接口。
 *
 * 约定：返回 String 的方法，null = 成功，非 null = 失败原因（由 ControllerUtils 翻译成 RestBean）。
 */
public interface TopicService extends IService<Topic> {

    // ==================== 分类 ====================

    List<TopicType> listTypes();

    void createTopicType(TopicTypeCreateVO vo);

    void updateTopicType(TopicTypeVO vo);

    /** 删除分类时会连带删除该分类下的帖子 */
    void deleteTopicType(int id);

    // ==================== 帖子（用户端） ====================

    String createTopic(int uid, TopicCreateVO vo);

    String updateTopic(int uid, TopicUpdateVO vo);

    List<TopicPreviewVO> listTopicByPage(int pageNumber, int type);

    List<TopicTopVO> listTopTopics();

    TopicDetailVO getTopic(int tid, int uid);

    void interact(Interact interact, boolean state);

    List<TopicPreviewVO> listTopicCollects(int uid);

    /** 关键词搜索（Elasticsearch）；page 从 0 开始 */
    PageRestBean<TopicPreviewVO> searchTopic(String keyword, int page);

    List<TopicPreviewVO> listTopicByUser(int uid);

    String createComment(int uid, AddCommentVO vo);

    List<CommentVO> comments(int tid, int pageNumber);

    void deleteComment(int id, int uid);

    void deleteTopic(int tid, int uid);

    // ==================== 帖子（管理端） ====================

    /** 管理后台列表：包含隐藏帖和锁定帖，可按标题模糊搜索 */
    JSONObject listAllTopicByPage(int page, int size, String keyword);

    /** 管理员删帖：不校验归属 */
    void deleteTopic(int id);

    void setTopicTop(int tid, boolean top);

    void setTopicLocked(int tid, boolean locked);

    void setTopicInvisible(int tid, boolean invisible);

    void changeTopicType(int tid, int type);
}
