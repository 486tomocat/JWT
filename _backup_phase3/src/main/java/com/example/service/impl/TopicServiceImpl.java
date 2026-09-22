package com.example.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.Account;
import com.example.entity.dto.AccountDetails;
import com.example.entity.dto.Interact;
import com.example.entity.dto.Topic;
import com.example.entity.dto.TopicComment;
import com.example.entity.dto.TopicType;
import com.example.entity.vo.request.AddCommentVO;
import com.example.entity.vo.request.TopicCreateVO;
import com.example.entity.vo.request.TopicUpdateVO;
import com.example.entity.vo.response.CommentVO;
import com.example.entity.vo.response.TopicDetailVO;
import com.example.entity.vo.response.TopicPreviewVO;
import com.example.entity.vo.response.TopicTopVO;
import com.example.mapper.AccountDetailsMapper;
import com.example.mapper.AccountMapper;
import com.example.mapper.TopicCommentMapper;
import com.example.mapper.TopicMapper;
import com.example.mapper.TopicTypeMapper;
import com.example.service.TopicService;
import com.example.utils.CacheUtils;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements TopicService {

    @Resource
    TopicTypeMapper typeMapper;

    @Resource
    TopicCommentMapper commentMapper;

    @Resource
    AccountMapper accountMapper;

    @Resource
    AccountDetailsMapper accountDetailsMapper;

    @Resource
    FlowUtils flowUtils;

    @Resource
    CacheUtils cacheUtils;

    @Resource
    StringRedisTemplate template;

    /** 互动暂存状态：记录某个 type 是否已经安排了延迟落库任务 */
    private final Map<String, Boolean> interactPending = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Override
    public List<TopicType> listTypes() {
        return typeMapper.selectList(null);
    }

    /**
     * 校验分类是否存在。
     * 这里每次都查一次库，而不是像参考实现那样在启动时缓存到 Set：
     * 缓存 Set 会导致后台新增分类后必须重启才能发帖。
     * 发帖本身有频率限制，这点查询开销可以接受。
     */
    private boolean typeExists(int type) {
        return typeMapper.exists(Wrappers.<TopicType>query().eq("id", type));
    }

    @Override
    public String createTopic(int uid, TopicCreateVO vo) {
        if (!textLimitCheck(vo.getContent(), 20000)) {
            return "文章内容太多，发文失败！";
        }
        if (!typeExists(vo.getType())) {
            return "文章类型非法！";
        }
        // 每个用户每小时最多发 3 篇
        String key = Const.FORUM_TOPIC_CREATE_COUNTER + uid;
        if (!flowUtils.limitPeriodCounterCheck(key, 3, 3600)) {
            return "发文频繁，请稍后再试！";
        }
        Topic topic = new Topic();
        BeanUtils.copyProperties(vo, topic);
        topic.setContent(vo.getContent().toJSONString());
        topic.setUid(uid);
        topic.setTime(new Date());
        topic.createIntro();
        if (this.save(topic)) {
            // 数据变了，把列表缓存整体失效（懒加载，下次访问重建）
            cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
            return null;
        }
        return "内部错误，请联系管理员！";
    }

    @Override
    public String updateTopic(int uid, TopicUpdateVO vo) {
        if (!textLimitCheck(vo.getContent(), 20000)) {
            return "文章内容太多，发文失败！";
        }
        if (!typeExists(vo.getType())) {
            return "文章类型非法！";
        }
        int result = baseMapper.update(null, Wrappers.<Topic>update()
                .eq("uid", uid)
                .eq("id", vo.getId())
                .eq("locked", 0)
                .set("title", vo.getTitle())
                .set("content", vo.getContent().toJSONString())
                .set("type", vo.getType())
                .set("intro", Topic.recreateIntro(vo.getContent())));
        if (result > 0) {
            cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
            return null;
        }
        return "文章不存在、无权修改或已被锁定";
    }

    @Override
    public String createComment(int uid, AddCommentVO vo) {
        if (!textLimitCheck(JSONObject.parseObject(vo.getContent()), 2000)) {
            return "评论内容太多，发表失败！";
        }
        // 每个用户每分钟最多 2 条评论
        String key = Const.FORUM_TOPIC_COMMENT_COUNTER + uid;
        if (!flowUtils.limitPeriodCounterCheck(key, 2, 60)) {
            return "发表评论频繁，请稍后再试！";
        }
        TopicComment comment = new TopicComment();
        comment.setUid(uid);
        BeanUtils.copyProperties(vo, comment);
        comment.setTime(new Date());
        commentMapper.insert(comment);
        return null;
    }

    @Override
    public List<CommentVO> comments(int tid, int pageNumber) {
        Page<TopicComment> page = Page.of(pageNumber, 10);
        commentMapper.selectPage(page, Wrappers.<TopicComment>query()
                .eq("tid", tid)
                .orderByAsc("time"));
        return page.getRecords().stream().map(dto -> {
            CommentVO vo = new CommentVO();
            BeanUtils.copyProperties(dto, vo);
            // 楼中楼：把被引用评论的富文本转成纯文本展示
            if (dto.getQuote() != null && dto.getQuote() > 0) {
                TopicComment quoted = commentMapper.selectById(dto.getQuote());
                if (quoted != null) {
                    JSONObject object = JSONObject.parseObject(quoted.getContent());
                    StringBuilder builder = new StringBuilder();
                    this.shortContent(object.getJSONArray("ops"), builder, ignore -> {});
                    vo.setQuote(builder.toString());
                } else {
                    vo.setQuote("此评论已被删除");
                }
            }
            CommentVO.User user = new CommentVO.User();
            this.fillUserDetails(user, dto.getUid());
            vo.setUser(user);
            return vo;
        }).toList();
    }

    @Override
    public void deleteComment(int id, int uid) {
        // 带上 uid 条件，防止删掉别人的评论（水平越权）
        commentMapper.delete(Wrappers.<TopicComment>query().eq("id", id).eq("uid", uid));
    }

    @Override
    public void deleteTopic(int tid, int uid) {
        int result = baseMapper.delete(Wrappers.<Topic>query()
                .eq("id", tid)
                .eq("uid", uid));
        if (result > 0) {
            cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
            // 顺手清掉收藏关系，避免留下脏数据
            baseMapper.deleteTopicCollect(tid);
        }
    }

    @Override
    public List<TopicPreviewVO> listTopicCollects(int uid) {
        return baseMapper.collectTopics(uid).stream().map(topic -> {
            TopicPreviewVO vo = new TopicPreviewVO();
            BeanUtils.copyProperties(topic, vo);
            return vo;
        }).toList();
    }

    @Override
    public List<TopicPreviewVO> listTopicByPage(int pageNumber, int type) {
        String key = Const.FORUM_TOPIC_PREVIEW_CACHE + pageNumber + ":" + type;
        List<TopicPreviewVO> list = cacheUtils.takeListFromCache(key, TopicPreviewVO.class);
        if (list != null) {
            return list;
        }
        Page<Topic> page = Page.of(pageNumber, 10);
        if (type == 0) {
            baseMapper.selectPage(page, Wrappers.<Topic>query()
                    .eq("invisible", 0)
                    .orderByDesc("time"));
        } else {
            baseMapper.selectPage(page, Wrappers.<Topic>query()
                    .eq("type", type)
                    .eq("invisible", 0)
                    .orderByDesc("time"));
        }
        List<Topic> topics = page.getRecords();
        if (topics.isEmpty()) {
            // 空页不缓存，且返回空列表而不是 null，避免前端拿到 null 报错
            return List.of();
        }
        list = topics.stream().map(this::resolveToPreview).toList();
        cacheUtils.saveListToCache(key, list, 60);
        return list;
    }

    @Override
    public List<TopicTopVO> listTopTopics() {
        List<Topic> topics = baseMapper.selectList(Wrappers.<Topic>query()
                .select("id", "title", "time")
                .eq("top", 1)
                .eq("invisible", 0));
        return topics.stream().map(topic -> {
            TopicTopVO vo = new TopicTopVO();
            BeanUtils.copyProperties(topic, vo);
            return vo;
        }).toList();
    }

    @Override
    public TopicDetailVO getTopic(int tid, int uid) {
        Topic topic = baseMapper.selectById(tid);
        // 参考实现这里少了判空，访问不存在的帖子会 500，这里补上返回 404
        if (topic == null) {
            return null;
        }
        // 被隐藏的帖子只有作者本人能看
        if (topic.getInvisible() != null && topic.getInvisible() == 1 && !topic.getUid().equals(uid)) {
            return null;
        }
        TopicDetailVO vo = new TopicDetailVO();
        BeanUtils.copyProperties(topic, vo);
        vo.setInteract(new TopicDetailVO.Interact(
                hasInteract(tid, uid, "like"),
                hasInteract(tid, uid, "collect")));
        TopicDetailVO.User user = new TopicDetailVO.User();
        this.fillUserDetails(user, topic.getUid());
        vo.setUser(user);
        vo.setComments(commentMapper.selectCount(Wrappers.<TopicComment>query().eq("tid", tid)));
        return vo;
    }

    /**
     * 点赞/收藏：不直接写数据库，而是先写 Redis Hash，
     * 3 秒内的多次操作合并成一次批量 SQL，起到削峰作用。
     */
    @Override
    public void interact(Interact interact, boolean state) {
        String type = interact.getType();
        template.opsForHash().put(type, interact.toKey(), Boolean.toString(state));
        this.saveInteractSchedule(type);
    }

    @Override
    public List<Topic> listTopicByUser(int uid) {
        return baseMapper.selectList(Wrappers.<Topic>query().eq("uid", uid));
    }

    /** 判断当前用户是否已点赞/收藏：先看 Redis 暂存，没有再查库 */
    private boolean hasInteract(int tid, int uid, String type) {
        String key = tid + ":" + uid;
        Object cached = template.opsForHash().get(type, key);
        if (cached != null) {
            return Boolean.parseBoolean(cached.toString());
        }
        return baseMapper.userInteractCount(tid, uid, type) > 0;
    }

    /** 每个 type 只安排一个延迟任务，避免短时间大量点赞生成大量定时任务 */
    private void saveInteractSchedule(String type) {
        if (!interactPending.getOrDefault(type, false)) {
            interactPending.put(type, true);
            scheduler.schedule(() -> {
                try {
                    this.saveInteract(type);
                } finally {
                    interactPending.put(type, false);
                }
            }, 3, TimeUnit.SECONDS);
        }
    }

    /** 把 Redis Hash 里的状态批量同步到数据库 */
    private void saveInteract(String type) {
        synchronized (type.intern()) {
            Map<Object, Object> entries = template.opsForHash().entries(type);
            if (entries.isEmpty()) {
                return;
            }
            List<Interact> check = new LinkedList<>();
            List<Interact> uncheck = new LinkedList<>();
            entries.forEach((k, v) -> {
                Interact interact = Interact.parseInteract(k.toString(), type);
                if (Boolean.parseBoolean(v.toString())) {
                    check.add(interact);
                } else {
                    uncheck.add(interact);
                }
            });
            if (!check.isEmpty()) {
                baseMapper.addInteract(check, type);
            }
            if (!uncheck.isEmpty()) {
                baseMapper.deleteInteract(uncheck, type);
            }
            template.delete(type);
        }
    }

    /** 从账号表 + 资料表拼出用户信息，填充到 VO 上 */
    private <T> T fillUserDetails(T target, int uid) {
        Account account = accountMapper.selectById(uid);
        if (account != null) {
            BeanUtils.copyProperties(account, target);
        }
        AccountDetails details = accountDetailsMapper.selectById(uid);
        if (details != null) {
            BeanUtils.copyProperties(details, target);
        }
        return target;
    }

    /**
     * 把帖子转成列表项。
     * 注意：这里对每个帖子都查了账号和两次互动计数，典型的 N+1；
     * 列表只有 10 条时还能接受，帖子多了应改成批量 IN 查询。
     */
    private TopicPreviewVO resolveToPreview(Topic topic) {
        TopicPreviewVO vo = new TopicPreviewVO();
        Account account = accountMapper.selectById(topic.getUid());
        if (account != null) {
            BeanUtils.copyProperties(account, vo);
        }
        BeanUtils.copyProperties(topic, vo);
        vo.setLike(baseMapper.interactCount(topic.getId(), "like"));
        vo.setCollect(baseMapper.interactCount(topic.getId(), "collect"));
        List<String> images = new ArrayList<>();
        StringBuilder previewText = new StringBuilder();
        if (topic.getContent() != null) {
            JSONArray ops = JSONObject.parseObject(topic.getContent()).getJSONArray("ops");
            this.shortContent(ops, previewText, obj -> images.add(obj.toString()));
        }
        vo.setText(previewText.length() > 300 ? previewText.substring(0, 300) : previewText.toString());
        vo.setImages(images);
        return vo;
    }

    /** 遍历 Delta ops，抽出纯文本；遇到图片则交给 imageHandler 收集 */
    private void shortContent(JSONArray ops, StringBuilder previewText, Consumer<Object> imageHandler) {
        for (Object op : ops) {
            Object insert = JSONObject.from(op).get("insert");
            if (insert instanceof String text) {
                if (previewText.length() >= 300) continue;
                previewText.append(text);
            } else if (insert instanceof Map<?, ?> map) {
                Object image = map.get("image");
                if (image != null) {
                    imageHandler.accept(image);
                }
            }
        }
    }

    /** 统计富文本的纯文本长度，超过上限就拒绝 */
    private boolean textLimitCheck(JSONObject object, int max) {
        if (object == null || object.getJSONArray("ops") == null) {
            return false;
        }
        long length = 0;
        for (Object op : object.getJSONArray("ops")) {
            String insert = JSONObject.from(op).getString("insert");
            if (insert != null) {
                length += insert.length();
            }
            if (length > max) {
                return false;
            }
        }
        return true;
    }
}
