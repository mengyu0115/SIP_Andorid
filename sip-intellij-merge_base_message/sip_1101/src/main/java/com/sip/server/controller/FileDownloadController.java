package com.sip.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件下载控制器
 * 提供静态文件访问，支持图片、语音、视频、文件的下载
 * URL格式: /files/{fileType}/{date}/{filename}
 *
 * @author SIP Team
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/files")
public class FileDownloadController {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 初始化时将相对路径转换为绝对路径
     */
    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(uploadDir);
            // 如果是相对路径，转换为绝对路径
            if (!path.isAbsolute()) {
                path = Paths.get(System.getProperty("user.dir"), uploadDir).toAbsolutePath().normalize();
                uploadDir = path.toString();
                log.info("FileDownloadController: 转换相对路径为绝对路径: ./uploads -> {}", uploadDir);
            }
            log.info("FileDownloadController: 文件下载目录: {}", uploadDir);
        } catch (Exception e) {
            log.error("初始化文件下载目录失败", e);
        }
    }

    /**
     * 下载文件
     * URL格式: /files/{fileType}/{date}/{filename}
     * 例如: /files/image/2025-12-19/abc123.jpg
     *
     * @param fileType 文件类型 (image/voice/video/file/other)
     * @param date 日期 (yyyy-MM-dd)
     * @param filename 文件名
     * @return 文件资源
     */
    @GetMapping("/{fileType}/{date}/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileType,
            @PathVariable String date,
            @PathVariable String filename) {

        try {
            // 构建文件路径
            Path filePath = Paths.get(uploadDir, fileType, date, filename);
            File file = filePath.toFile();

            log.info("请求下载文件: {}", filePath);

            // 检查文件是否存在
            if (!file.exists() || !file.isFile()) {
                log.warn("文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // 检查文件是否在允许的目录内（防止路径遍历攻击）
            String canonicalUploadDir = Paths.get(uploadDir).toFile().getCanonicalPath();
            String canonicalFilePath = file.getCanonicalPath();
            if (!canonicalFilePath.startsWith(canonicalUploadDir)) {
                log.error("非法文件访问尝试: {}", filePath);
                return ResponseEntity.badRequest().build();
            }

            // 创建资源
            Resource resource = new FileSystemResource(file);

            // 确定Content-Type
            String contentType = determineContentType(filename);

            // 构建响应
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentLength(file.length())
                    .body(resource);

        } catch (Exception e) {
            log.error("下载文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据文件扩展名确定Content-Type
     *
     * @param filename 文件名
     * @return Content-Type
     */
    private String determineContentType(String filename) {
        String lowerFilename = filename.toLowerCase();

        // 图片类型
        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFilename.endsWith(".png")) {
            return "image/png";
        } else if (lowerFilename.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFilename.endsWith(".bmp")) {
            return "image/bmp";
        } else if (lowerFilename.endsWith(".webp")) {
            return "image/webp";
        }

        // 音频类型
        else if (lowerFilename.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lowerFilename.endsWith(".wav")) {
            return "audio/wav";
        } else if (lowerFilename.endsWith(".m4a")) {
            return "audio/x-m4a";
        } else if (lowerFilename.endsWith(".ogg")) {
            return "audio/ogg";
        }

        // 视频类型
        else if (lowerFilename.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerFilename.endsWith(".avi")) {
            return "video/x-msvideo";
        } else if (lowerFilename.endsWith(".mov")) {
            return "video/quicktime";
        } else if (lowerFilename.endsWith(".mkv")) {
            return "video/x-matroska";
        } else if (lowerFilename.endsWith(".webm")) {
            return "video/webm";
        }

        // 文档类型
        else if (lowerFilename.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFilename.endsWith(".doc")) {
            return "application/msword";
        } else if (lowerFilename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerFilename.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (lowerFilename.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lowerFilename.endsWith(".txt")) {
            return "text/plain";
        }

        // 默认类型
        else {
            return "application/octet-stream";
        }
    }
}
