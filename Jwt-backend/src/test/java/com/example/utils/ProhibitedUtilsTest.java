package com.example.utils;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProhibitedUtils 单元测试。
 *
 * 词表放在 JUnit 的临时目录里，用反射手动触发 @PostConstruct 的 init()，
 * 不依赖 Spring 容器，也不会碰到项目里真实的 prohibited.json。
 */
class ProhibitedUtilsTest {

    @TempDir
    Path tempDir;

    private Path wordFile;
    private ProhibitedUtils utils;

    @BeforeEach
    void setUp() throws Exception {
        wordFile = tempDir.resolve("prohibited.json");
        Files.writeString(wordFile, "[\"违禁词A\",\"违禁词B\"]", StandardCharsets.UTF_8);

        utils = new ProhibitedUtils();
        utils.filePath = wordFile.toAbsolutePath().toString();

        Method init = ProhibitedUtils.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(utils);
    }

    @Test
    @DisplayName("启动时从文件加载词表")
    void loadsWordsFromFile() {
        assertEquals(2, utils.getProhibitedWords().size());
        assertTrue(utils.getProhibitedWords().contains("违禁词A"));
    }

    @Test
    @DisplayName("纯文本命中违禁词")
    void detectsProhibitedWordInText() {
        assertTrue(utils.containsProhibitedWord("这句话里包含违禁词A，应该被拦截"));
        assertTrue(utils.containsProhibitedWord("以违禁词B结尾"));
    }

    @Test
    @DisplayName("正常文本不误判")
    void doesNotFlagCleanText() {
        assertFalse(utils.containsProhibitedWord("这是一条完全正常的帖子内容"));
    }

    @Test
    @DisplayName("null 与空串不误判")
    void handlesNullAndEmpty() {
        assertFalse(utils.containsProhibitedWord((String) null));
        assertFalse(utils.containsProhibitedWord(""));
    }

    @Test
    @DisplayName("富文本 Delta：文字节点中的违禁词能被识别")
    void detectsInRichTextDelta() {
        JSONObject delta = JSONObject.parseObject("{\"ops\":[{\"insert\":\"正文里含违禁词B\"}]}");
        assertTrue(utils.containsProhibitedWord(delta));
    }

    @Test
    @DisplayName("富文本 Delta：图片节点不参与匹配，避免把图片地址当正文误判")
    void ignoresImageNodesInDelta() {
        JSONObject onlyImage = JSONObject.parseObject(
                "{\"ops\":[{\"insert\":{\"image\":\"http://cdn.example.com/违禁词A.png\"}}]}");

        assertFalse(utils.containsProhibitedWord(onlyImage),
                "图片节点的 insert 是对象而非文本，不应被当成正文匹配");
    }

    @Test
    @DisplayName("富文本 Delta：文字与图片混排时，仍能识别文字里的违禁词")
    void detectsTextAmongImageNodes() {
        JSONObject mixed = JSONObject.parseObject(
                "{\"ops\":[{\"insert\":{\"image\":\"http://cdn.example.com/1.png\"}},{\"insert\":\"违禁词A\"}]}");
        assertTrue(utils.containsProhibitedWord(mixed));
    }

    @Test
    @DisplayName("保存词表时过滤空串与重复项——空串会让 contains(\"\") 恒为真，导致全站发不出帖")
    void setWordsFiltersBlankAndDuplicates() {
        utils.setProhibitedWords(Arrays.asList("违禁词C", "", "   ", "违禁词C", null));

        assertEquals(1, utils.getProhibitedWords().size());
        assertTrue(utils.containsProhibitedWord("包含违禁词C"));
        assertFalse(utils.containsProhibitedWord("这条内容里什么都没有"),
                "过滤掉空串后，任意文本都不应该被误判");
    }

    @Test
    @DisplayName("热更新词表立即生效，并回写到启动时解析出的同一个文件")
    void hotUpdateTakesEffectAndPersists() throws Exception {
        utils.setProhibitedWords(List.of("新违禁词"));

        assertTrue(utils.containsProhibitedWord("这句话含新违禁词"));
        assertFalse(utils.containsProhibitedWord("包含违禁词A"), "旧词表应已被整体替换");

        String persisted = Files.readString(wordFile, StandardCharsets.UTF_8);
        assertTrue(persisted.contains("新违禁词"), "新词表应回写到文件，重启后仍生效");
    }
}
