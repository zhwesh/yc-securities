package com.zhwesh.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.zhwesh.properties.AliOssProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 阿里云OSS工具类
 */
@Component
public class AliOssUtil {

    private final AliOssProperties aliOssProperties;

    private final OSS ossClient;

    @Autowired
    public AliOssUtil(AliOssProperties aliOssProperties) {
        this.aliOssProperties = aliOssProperties;
        this.ossClient = new OSSClientBuilder().build(
                aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret());
    }

    /**
     * 上传文件到OSS，返回访问URL
     */
    public String upload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectName = UUID.randomUUID() + extension;
        try {
            ossClient.putObject(aliOssProperties.getBucketName(), objectName, file.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
        return "https://" + aliOssProperties.getBucketName() + "."
                + aliOssProperties.getEndpoint() + "/" + objectName;
    }
}
