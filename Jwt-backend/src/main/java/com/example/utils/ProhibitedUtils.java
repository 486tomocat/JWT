package com.example.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 违禁词工具。
 *
 * 设计要点：
 * 1. 词表启动时加载进内存，匹配就是一次 List 遍历 + String.contains，
 *    比每次查库/查 Redis 快得多（违禁词是「高频读、极低频写」）；
 * 2. 用 CopyOnWriteArrayList 保存：后台改词表和用户发帖是两个线程，
 *    COW 保证读线程永远看到的是一个完整快照，不会读到「清空到一半」的列表；
 * 3. 匹配的是富文本里的「文字节点」。图片节点的 insert 是对象而不是字符串，
 *    参考实现用 getString("insert") 会把整个图片对象转成 JSON 再 match，属于误判来源，这里显式跳过；
 * 4. 路径解析见 resolvePath()——这是联调踩坑之后才补上的，很关键。
 */
@Slf4j
@Component
public class ProhibitedUtils {

    @Value("${forum.prohibited-file:./prohibited.json}")
    String filePath;

    /** 解析出来的绝对路径，加载和保存都用它，保证两者指向同一个文件 */
    private Path resolvedPath;

    private final List<String> prohibitedWords = new CopyOnWriteArrayList<>();

    @PostConstruct
    private void init() {
        this.resolvedPath = resolvePath();
        if (!Files.exists(resolvedPath)) {
            // 必须大声报错：词表为空时违禁词校验会「静默失效」，
            // 比抛异常还危险——功能看起来在跑，实际上什么都没拦。
            log.warn("违禁词文件不存在！查找位置：{}", resolvedPath);
            log.warn("违禁词校验将不会生效，请把 prohibited.json 放到上述位置后重启");
            return;
        }
        try {
            JSONArray array = JSONArray.parse(Files.readString(resolvedPath, StandardCharsets.UTF_8));
            prohibitedWords.addAll(array.toList(String.class));
            log.info("违禁词加载完成，共 {} 个 -> {}，来源：{}", prohibitedWords.size(),
                    prohibitedWords, resolvedPath);
        } catch (IOException | RuntimeException e) {
            log.error("违禁词列表读取失败，将以空词表启动：{}", resolvedPath, e);
        }
    }

    /**
     * 解析词表文件位置。
     *
     * 为什么不直接用配置里的 ./prohibited.json：
     * 「当前工作目录」取决于应用是怎么启动的——mvn spring-boot:run 在模块目录、
     * IDE 的 Run Configuration 可能配成项目根目录、打成 jar 又完全不同。
     * 只认一个相对路径，就会出现「文件明明在，启动时却读不到」，
     * 而读不到的后果是违禁词静默失效。所以按「就近 + 兜底」的顺序找：
     *   1. 配置的路径本身（相对当前工作目录）；
     *   2. Jwt-backend/&lt;文件名&gt; —— 兼容「从项目根目录启动」的多模块布局；
     *   3. classpath 里的同名资源 —— 打包成 jar 也能读到；
     *   4. 都不在：返回配置路径的绝对形式，第一次保存词表时创建。
     */
    private Path resolvePath() {
        Path configured = Path.of(filePath);
        if (configured.isAbsolute()) {
            return configured;
        }
        if (Files.exists(configured)) {
            return configured.toAbsolutePath();
        }
        Path moduleScoped = Path.of("Jwt-backend").resolve(configured);
        if (Files.exists(moduleScoped)) {
            return moduleScoped.toAbsolutePath();
        }
        String name = configured.getFileName().toString();
        ClassPathResource resource = new ClassPathResource(name);
        if (resource.exists()) {
            try {
                return Path.of(resource.getURI());
            } catch (Exception e) {
                log.debug("classpath 资源无法转为文件路径（可能被打进 jar）：{}", name);
            }
        }
        return configured.toAbsolutePath();
    }

    /** 判断富文本 Delta（{"ops":[...]}）里是否包含违禁词 */
    public boolean containsProhibitedWord(JSONObject object) {
        if (object == null) return false;
        JSONArray ops = object.getJSONArray("ops");
        if (ops == null) return false;
        for (Object op : ops) {
            Object insert = JSONObject.from(op).get("insert");
            // 只有字符串才是真正的正文；图片等节点是对象，跳过
            if (insert instanceof String text && this.containsProhibitedWord(text)) {
                return true;
            }
        }
        return false;
    }

    /** 判断纯文本是否包含违禁词 */
    public boolean containsProhibitedWord(String text) {
        if (text == null || text.isEmpty()) return false;
        for (String word : prohibitedWords) {
            if (!word.isEmpty() && text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getProhibitedWords() {
        return List.copyOf(prohibitedWords);
    }

    /** 后台保存新词表：先换内存，再写回「启动时解析出来的同一个文件」 */
    public void setProhibitedWords(List<String> list) {
        prohibitedWords.clear();
        if (list != null) {
            // 去重 + 去掉空串：如果词表里混进空字符串，text.contains("") 恒为 true，
            // 整个论坛就再也发不出任何帖子了
            list.stream()
                    .filter(word -> word != null && !word.isBlank())
                    .distinct()
                    .forEach(prohibitedWords::add);
        }
        try {
            Path target = (resolvedPath != null) ? resolvedPath : Path.of(filePath).toAbsolutePath();
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, JSONArray.from(prohibitedWords).toJSONString(), StandardCharsets.UTF_8);
            log.info("违禁词已更新，共 {} 个 -> {}", prohibitedWords.size(), target);
        } catch (IOException e) {
            log.error("违禁词文件写入失败", e);
        }
    }
}
