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
import com.example.entity.vo.request.TopicTypeCreateVO;
import com.example.entity.vo.request.TopicUpdateVO;
import com.example.entity.PageRestBean;
import com.example.entity.vo.response.CommentVO;
import com.example.entity.vo.response.TopicDetailVO;
import com.example.entity.vo.response.TopicPreviewVO;
import com.example.entity.vo.response.TopicTopVO;
import com.example.entity.vo.response.TopicTypeVO;
import com.example.mapper.AccountDetailsMapper;
import com.example.mapper.AccountMapper;
import com.example.mapper.TopicCommentMapper;
import com.example.mapper.TopicMapper;
import com.example.mapper.TopicTypeMapper;
import com.example.service.NotificationService;
import com.example.service.TopicSearchService;
import com.example.service.TopicService;
import com.example.utils.CacheUtils;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import com.example.utils.ProhibitedUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
    TopicSearchService searchService;

    @Resource
    StringRedisTemplate template;

    @Resource
    NotificationService notificationService;

    @Resource
    ProhibitedUtils prohibitedUtils;

    /** 互动暂存状态：记录某个 type 是否已经安排了延迟落库任务 */
    private final Map<String, Boolean> interactPending = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Override
    public List<TopicType> listTypes() {
        return typeMapper.selectList(null);
    }

    // ==================== 分类管理（管理后台） ====================

    @Override
    public void createTopicType(TopicTypeCreateVO vo) {
        TopicType type = new TopicType();
        BeanUtils.copyProperties(vo, type);
        typeMapper.insert(type);
        // 分类列表没有做缓存（见 typeExists 的说明），所以这里不需要清缓存
    }

    @Override
    public void updateTopicType(TopicTypeVO vo) {
        TopicType type = typeMapper.selectById(vo.getId());
        if (type == null) return;
        BeanUtils.copyProperties(vo, type);
        typeMapper.updateById(type);
    }

    @Override
    public void deleteTopicType(int id) {
        TopicType type = typeMapper.selectById(id);
        if (type == null) return;
        if (typeMapper.deleteById(id) > 0) {
            // 分类没了，这个分类下的帖子就成了「孤儿」——前台按分类翻页永远查不到它们。
            // 所以先把这些帖子逐个删掉（deleteTopic(int) 会顺带清收藏关系和列表缓存）。
            baseMapper.selectList(Wrappers.<Topic>query().eq("type", id))
                    .forEach(topic -> this.deleteTopic(topic.getId()));
        }
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
        // 禁言 / 违禁词都属于「内容是否允许发」，放在限流之前：
        // 内容本身不合法时不应该消耗用户的发帖配额（和阶段 2 分类校验的顺序一致）。
        if (this.isMuted(uid)) {
            return "您已被禁言，暂时无法发帖！";
        }
        if (prohibitedUtils.containsProhibitedWord(vo.getContent())) {
            return "包含违禁词，发文失败！";
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
            // 同步搜索索引，否则新帖要等重启才能被搜到
            searchService.saveIndex(topic);
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
        if (this.isMuted(uid)) {
            return "您已被禁言，暂时无法编辑帖子！";
        }
        if (prohibitedUtils.containsProhibitedWord(vo.getContent())) {
            return "包含违禁词，修改失败！";
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
            // 重新读一次：索引里要的是改完之后的值
            Topic updated = baseMapper.selectById(vo.getId());
            if (updated != null) {
                searchService.saveIndex(updated);
            }
            return null;
        }
        return "文章不存在、无权修改或已被锁定";
    }

    @Override
    public String createComment(int uid, AddCommentVO vo) {
        JSONObject content = JSONObject.parseObject(vo.getContent());
        if (!textLimitCheck(content, 2000)) {
            return "评论内容太多，发表失败！";
        }
        Topic topic = baseMapper.selectById(vo.getTid());
        if (topic == null) {
            return "帖子不存在或已被删除";
        }
        if (this.isMuted(uid)) {
            return "您已被禁言，暂时无法评论！";
        }
        if (prohibitedUtils.containsProhibitedWord(content)) {
            return "包含违禁词，发表失败！";
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
        // 通知放在插入之后：通知只是「附赠品」，它失败不该让已经发出去的评论回滚
        this.sendCommentNotification(uid, vo, topic);
        return null;
    }

    /**
     * 站内通知：评论成功后通知「被回复的人」。
     * - 回复某条评论（quote > 0）→ 通知那条评论的作者；
     * - 直接回复帖子（quote <= 0）→ 通知楼主；
     * - 自己回自己 → 不发通知，避免自己给自己刷通知。
     */
    private void sendCommentNotification(int uid, AddCommentVO vo, Topic topic) {
        Account account = accountMapper.selectById(uid);
        if (account == null) return;
        if (vo.getQuote() > 0) {
            TopicComment quoted = commentMapper.selectById(vo.getQuote());
            if (quoted == null || Objects.equals(quoted.getUid(), uid)) return;
            notificationService.addNotification(
                    quoted.getUid(),
                    "您有新的评论回复",
                    account.getUsername() + " 回复了你的评论，快去看看吧！",
                    "success",
                    "/index/forum/topic/" + quoted.getTid()
            );
        } else {
            if (Objects.equals(topic.getUid(), uid)) return;
            notificationService.addNotification(
                    topic.getUid(),
                    "您有新的帖子回复",
                    account.getUsername() + " 回复了你的帖子「" + topic.getTitle() + "」，快去看看吧！",
                    "success",
                    "/index/forum/topic/" + topic.getId()
            );
        }
    }

    /** 是否被禁言。禁言只拦「写」操作，登录/浏览/点赞都不受影响 */
    private boolean isMuted(int uid) {
        Account account = accountMapper.selectById(uid);
        return account != null && account.isMute();
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
            searchService.deleteIndex(tid);
        }
    }

    // ==================== 帖子管理（管理后台） ====================

    /** 管理员删帖：不校验 uid，但同样要清缓存和收藏关系 */
    @Override
    public void deleteTopic(int id) {
        if (baseMapper.deleteById(id) > 0) {
            cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
            baseMapper.deleteTopicCollect(id);
            searchService.deleteIndex(id);
        }
    }

    @Override
    public void setTopicTop(int tid, boolean top) {
        baseMapper.update(null, Wrappers.<Topic>update()
                .eq("id", tid)
                .set("top", top));
        // 置顶帖列表（top-topic）没有缓存，但首页列表里带 top 字段，必须清
        cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
    }

    @Override
    public void setTopicLocked(int tid, boolean locked) {
        baseMapper.update(null, Wrappers.<Topic>update()
                .eq("id", tid)
                .set("locked", locked));
        // locked 会体现在列表项的「已锁定」角标上
        cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
    }

    @Override
    public void setTopicInvisible(int tid, boolean invisible) {
        baseMapper.update(null, Wrappers.<Topic>update()
                .eq("id", tid)
                .set("invisible", invisible));
        // 隐藏/恢复直接决定帖子在不在列表里，必须清缓存
        cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
        // 搜索结果里也要跟着消失/出现
        Topic topic = baseMapper.selectById(tid);
        if (topic != null) {
            searchService.saveIndex(topic);
        }
    }

    @Override
    public void changeTopicType(int tid, int type) {
        if (!typeExists(type)) return;
        int result = baseMapper.update(null, Wrappers.<Topic>update()
                .eq("id", tid)
                .set("type", type));
        // 修复阶段 2 提到的坑：参考实现写的是 > 1，而 update 成功只会返回 1，
        // 条件永远不成立 -> 改了分类但列表还是旧分类。
        if (result > 0) {
            cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
        }
    }

    /**
     * 关键词搜索。
     *
     * 流程：ES 找出「相关的 id + 命中总数」→ 回 MySQL 取完整数据 → 按 ES 的相关度顺序重排。
     * 不把帖子完整存进 ES，就不会有「索引里的字段和数据库不一致」这类问题。
     */
    @Override
    public PageRestBean<TopicPreviewVO> searchTopic(String keyword, int page) {
        TopicSearchService.SearchHits hits = searchService.search(keyword, page, 10);
        if (hits.ids().isEmpty()) {
            return PageRestBean.success(List.of(), hits.total(), page);
        }

        Map<Integer, Topic> byId = baseMapper.selectBatchIds(hits.ids()).stream()
                .collect(Collectors.toMap(Topic::getId, t -> t));

        List<TopicPreviewVO> list = hits.ids().stream()
                .map(byId::get)
                // 索引可能比数据库慢半拍（比如刚被删掉），过滤掉查不到的
                .filter(Objects::nonNull)
                .map(this::resolveToPreview)
                .toList();

        return PageRestBean.success(list, hits.total(), page);
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

    /**
     * 管理后台的帖子列表。
     *
     * 和用户端的区别：
     * 1. 不过滤 invisible —— 管理员的职责就是看到包含隐藏帖在内的全部内容；
     * 2. 不做缓存 —— 后台是低频操作，且刚改完状态就要看到最新结果，
     *    缓存带来的「改了没变」比那点性能更让人难受；
     * 3. 只 select 必要的列 —— content 是富文本大字段，后台列表用不到，
     *    查出来只会白白增加网络和内存开销。
     */
    @Override
    public JSONObject listAllTopicByPage(int page, int size, String keyword) {
        Page<Topic> topicPage = baseMapper.selectPage(Page.of(page, size), Wrappers.<Topic>query()
                .select("id", "title", "uid", "type", "time", "top", "locked", "invisible")
                .like(keyword != null && !keyword.isBlank(), "title", "%" + keyword + "%")
                .orderByDesc("time"));
        List<TopicPreviewVO> list = topicPage.getRecords().stream()
                .map(this::resolveToPreview)
                .toList();
        JSONObject object = new JSONObject();
        object.put("total", topicPage.getTotal());
        object.put("list", list);
        object.put("page", topicPage.getCurrent());
        return object;
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
        // 顺带把总数也带上：复用现成的 interactCount，两次轻量 count 查询
        vo.setLike(baseMapper.interactCount(tid, "like"));
        vo.setCollect(baseMapper.interactCount(tid, "collect"));
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
    public List<TopicPreviewVO> listTopicByUser(int uid) {
        // 复用列表接口那套转换：带上作者、点赞/收藏计数与摘要图，
        // 前端「我的帖子」就能和广场用同一个卡片组件
        return baseMapper.selectList(Wrappers.<Topic>query()
                        .eq("uid", uid)
                        .orderByDesc("time"))
                .stream()
                .map(this::resolveToPreview)
                .toList();
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
