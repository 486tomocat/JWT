package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.Interact;
import com.example.entity.dto.Topic;
import com.example.entity.dto.TopicType;
import com.example.entity.vo.request.AddCommentVO;
import com.example.entity.vo.request.TopicCreateVO;
import com.example.entity.vo.request.TopicUpdateVO;
import com.example.entity.vo.response.CommentVO;
import com.example.entity.vo.response.TopicDetailVO;
import com.example.entity.vo.response.TopicPreviewVO;
import com.example.entity.vo.response.TopicTopVO;

import java.util.List;

/**
 * 论坛业务接口。
 *
 * 约定：返回 String 的方法，null = 成功，非 null = 失败原因（由 ControllerUtils 翻译成 RestBean）。
 */
public interface TopicService extends IService<Topic> {

    List<TopicType> listTypes();

    String createTopic(int uid, TopicCreateVO vo);

    String updateTopic(int uid, TopicUpdateVO vo);

    List<TopicPreviewVO> listTopicByPage(int pageNumber, int type);

    List<TopicTopVO> listTopTopics();

    TopicDetailVO getTopic(int tid, int uid);

    void interact(Interact interact, boolean state);

    List<TopicPreviewVO> listTopicCollects(int uid);

    List<Topic> listTopicByUser(int uid);

    String createComment(int uid, AddCommentVO vo);

    List<CommentVO> comments(int tid, int pageNumber);

    void deleteComment(int id, int uid);

    void deleteTopic(int tid, int uid);
}
