package com.example.controller;

import com.example.entity.RestBean;
import com.example.utils.S3StorageUtils;
import com.example.utils.SnowflakeIdUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 文件上传。目前只开放图片（帖子配图用；将来头像也可复用）。
 *
 * 安全上的三点：
 *   1. 扩展名白名单 —— 挡住 .jsp / .exe 这类文件
 *   2. 单文件 5MB 上限（容器层的上限在 application.yml 的 spring.servlet.multipart 里）
 *   3. 对象名由服务端生成（日期目录 + 雪花 ID），不采用原始文件名 ——
 *      既避免中文名和重名，也杜绝了「文件名里带 ../」这种路径穿越
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
public class FileController {

    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @Resource
    S3StorageUtils storageUtils;

    @Resource
    SnowflakeIdUtils snowflakeIdUtils;

    @PostMapping("/image")
    public RestBean<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return RestBean.failure(400, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            return RestBean.failure(400, "图片不能超过 5MB");
        }
        if (!storageUtils.available()) {
            return RestBean.failure(400, "图片服务未就绪，请联系管理员");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.lastIndexOf('.') >= 0
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXT.contains(ext)) {
            return RestBean.failure(400, "只支持 " + String.join(" / ", ALLOWED_EXT) + " 格式的图片");
        }

        // 按日期分目录，便于日后清理；文件名用雪花 ID，不会重复
        String objectName = LocalDate.now() + "/" + snowflakeIdUtils.nextId() + "." + ext;

        try {
            String url = storageUtils.upload(objectName, file.getInputStream(),
                    file.getSize(), file.getContentType());
            return RestBean.success(url);
        } catch (Exception e) {
            log.error("图片上传失败: {}", e.getMessage());
            return RestBean.failure(400, "图片上传失败，请稍后再试");
        }
    }
}
