package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.dto.Interact;
import com.example.entity.dto.Topic;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 帖子 Mapper。
 * 除了继承 BaseMapper 的通用 CRUD，还手写了互动（点赞/收藏）相关的批量 SQL。
 *
 * 注意 ${type} 是字符串拼接（表名不能用 #{} 预编译），
 * 所以调用方必须保证 type 只可能是 "like"/"collect"，
 * 绝不能直接把前端传来的值拼进去，否则会有 SQL 注入风险。
 */
@Mapper
public interface TopicMapper extends BaseMapper<Topic> {

    @Insert("""
            <script>
                insert ignore into db_topic_interact_${type} values
                <foreach collection="interacts" item="item" separator=",">
                    (#{item.tid}, #{item.uid}, #{item.time})
                </foreach>
            </script>
            """)
    void addInteract(List<Interact> interacts, String type);

    @Delete("""
            <script>
                delete from db_topic_interact_${type} where
                <foreach collection="interacts" item="item" separator=" or ">
                    (tid = #{item.tid} and uid = #{item.uid})
                </foreach>
            </script>
            """)
    int deleteInteract(List<Interact> interacts, String type);

    @Select("select count(*) from db_topic_interact_${type} where tid = #{tid}")
    int interactCount(int tid, String type);

    @Select("select count(*) from db_topic_interact_${type} where tid = #{tid} and uid = #{uid}")
    int userInteractCount(int tid, int uid, String type);

    @Select("""
            select t.* from db_topic_interact_collect c
            right join db_topic t on c.tid = t.id
            where c.uid = #{uid}
            """)
    List<Topic> collectTopics(int uid);

    @Delete("delete from db_topic_interact_collect where tid = #{tid}")
    int deleteTopicCollect(int tid);
}
