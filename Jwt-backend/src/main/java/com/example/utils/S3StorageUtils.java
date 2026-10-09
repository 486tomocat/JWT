package com.example.utils;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 对象存储封装（S3 兼容，图片上传）。
 *
 * 启动时会自动确保桶存在并设为「公开读」—— 省得每次换环境都要手工去控制台建桶。
 * 设置公开读是必须的：否则前端 <img src="..."> 拿不到图（会被 403 拒绝）。
 */
@Slf4j
@Component
public class S3StorageUtils {

    @Value("${storage.endpoint}")
    String endpoint;

    @Value("${storage.bucket}")
    String bucket;

    @Value("${storage.access-key}")
    String accessKey;

    @Value("${storage.secret-key}")
    String secretKey;

    private MinioClient client;

    @PostConstruct
    public void init() {
        // 凭据没配（比如别人克隆仓库后没建 local 文件）就直接跳过，
        // 只让图片功能不可用，不要拖垮整个应用启动
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.warn("未配置 MinIO 凭据（minio.access-key / secret-key），图片上传功能不可用");
            return;
        }
        try {
            client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            ensureBucket();
        } catch (Exception e) {
            log.error("MinIO 初始化失败，图片上传功能不可用：{}", e.getMessage());
        }
    }

    /** 桶不存在就创建，并把读权限设为公开 */
    private void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("MinIO 桶 {} 不存在，已自动创建", bucket);
        }
        client.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(bucket)
                .config(publicReadPolicy())
                .build());
        log.info("MinIO 就绪：endpoint={} bucket={}", endpoint, bucket);
    }

    /** 只读策略：允许任何人 GetObject（上传/删除仍然需要凭据） */
    private String publicReadPolicy() {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(bucket);
    }

    public boolean available() {
        return client != null;
    }

    /**
     * 上传并返回可直接访问的 URL。
     *
     * @param objectName  对象名（调用方用「日期/雪花ID.扩展名」，避免中文名与重名）
     * @param stream      内容流
     * @param size        字节数（-1 表示未知，但已知时传准确值能省一次缓冲）
     * @param contentType MIME 类型
     */
    public String upload(String objectName, InputStream stream, long size, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(stream, size, -1)
                .contentType(contentType == null ? "application/octet-stream" : contentType)
                .build());
        return endpoint + "/" + bucket + "/" + objectName;
    }
}