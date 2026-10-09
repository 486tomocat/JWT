package com.example.service.impl;

import com.example.entity.dto.Topic;
import com.example.entity.dto.TopicIndex;
import com.example.mapper.TopicMapper;
import com.example.repository.TopicIndexRepository;
import com.example.service.TopicSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TopicSearchServiceImpl implements TopicSearchService {

    @Resource
    TopicMapper topicMapper;

    @Resource
    TopicIndexRepository repository;

    /**
     * 向量模型（配置里是阿里百炼的 text-embedding-v3，1024 维）。
     * 用来给帖子算语义向量，供 RAG 检索。
     */
    @Resource
    EmbeddingModel embeddingModel;

    /**
     * 应用完全就绪后再重建索引。
     * 用 ApplicationReadyEvent 而不是 @PostConstruct：后者执行时 ES 客户端可能还没准备好。
     *
     * 每次都全量重建（清空 + 从 MySQL 导入）：
     * 对几千条帖子的数据量来说很快，而且永远不会出现「索引和数据库对不上」的疑难杂症。
     * 数据量真大了再改成增量 + 定时补偿。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Override
    public void rebuildIndex() {
        try {
            List<Topic> topics = topicMapper.selectList(null);
            List<TopicIndex> indexes = new ArrayList<>(topics.size());
            int vectorized = 0;
            for (Topic topic : topics) {
                TopicIndex index = toIndex(topic);
                if (fillVector(index)) {
                    vectorized++;
                }
                indexes.add(index);
            }
            repository.deleteAll();
            repository.saveAll(indexes);
            log.info("搜索索引重建完成，共 {} 条（其中 {} 条已生成向量）", indexes.size(), vectorized);
        } catch (Exception e) {
            // ES 没起来不该拖垮整个应用：搜索功能不可用，其它功能照常
            log.error("搜索索引重建失败，搜索功能将不可用：{}", e.getMessage());
        }
    }

    @Override
    public void saveIndex(Topic topic) {
        try {
            TopicIndex index = toIndex(topic);
            fillVector(index);
            repository.save(index);
        } catch (Exception e) {
            log.warn("同步搜索索引失败(id={})：{}", topic.getId(), e.getMessage());
        }
    }

    /**
     * 给索引文档算语义向量（标题 + 摘要）。
     *
     * @return 是否成功；失败时返回 false —— 帖子仍然会被索引（关键词搜索照常可用），
     *         只是 RAG 检索不到它。**不要让向量化失败阻塞发帖流程**。
     */
    private boolean fillVector(TopicIndex index) {
        try {
            String text = (index.getTitle() == null ? "" : index.getTitle())
                    + "\n"
                    + (index.getText() == null ? "" : index.getText());
            float[] embedding = embeddingModel.embed(text);
            List<Float> vector = new ArrayList<>(embedding.length);
            for (float v : embedding) {
                vector.add(v);
            }
            index.setDenseVector(vector);
            return true;
        } catch (Exception e) {
            log.warn("生成向量失败(id={})，该帖将只能被关键词搜到：{}", index.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public void deleteIndex(int id) {
        try {
            repository.deleteById(id);
        } catch (Exception e) {
            log.warn("删除搜索索引失败(id={})：{}", id, e.getMessage());
        }
    }

    @Override
    public SearchHits search(String keyword, int page, int size) {
        // 注意：Spring Data 的页码是 0 基，与论坛接口的 page 语义一致，不用换算
        Page<TopicIndex> result = repository.searchByKeyword(keyword, PageRequest.of(page, size));
        return new SearchHits(
                result.getContent().stream().map(TopicIndex::getId).toList(),
                result.getTotalElements()
        );
    }

    /**
     * 转成索引文档。
     * 正文只索引发帖时生成的摘要（intro），不索引整篇 Delta ——
     * 搜索命中靠标题和摘要足够了，索引能小一个数量级。
     *
     * 这里用 setter 而不是构造器：字段会随功能增加（比如 denseVector），
     * 构造器每次都要重新数参数顺序，容易出错。
     */
    private TopicIndex toIndex(Topic topic) {
        TopicIndex index = new TopicIndex();
        index.setId(topic.getId());
        index.setTitle(topic.getTitle());
        index.setText(topic.getIntro());
        index.setInvisible(topic.getInvisible() == null ? 0 : topic.getInvisible());
        // denseVector 由向量化流程填充，见 syncVector
        return index;
    }
}
