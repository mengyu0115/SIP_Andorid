package com.sip.server.service;

import com.sip.server.entity.FileInfo;
import com.sip.server.mapper.FileInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    @Autowired
    private FileInfoMapper fileInfoMapper;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 初始化时将相对路径转换为绝对路径
     * 避免 Tomcat 工作目录导致的路径问题
     */
    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(uploadDir);
            // 如果是相对路径，转换为绝对路径
            if (!path.isAbsolute()) {
                path = Paths.get(System.getProperty("user.dir"), uploadDir).toAbsolutePath().normalize();
                uploadDir = path.toString();
                log.info("转换相对路径为绝对路径: ./uploads -> {}", uploadDir);
            }

            // 确保上传目录存在
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
                log.info("创建上传根目录: {}", uploadDir);
            }

            log.info("文件上传目录: {}", uploadDir);
        } catch (Exception e) {
            log.error("初始化文件上传目录失败", e);
        }
    }

    public FileInfo upload(MultipartFile file, String fileType, Long userId) {
        try {
            String filePath = saveToLocal(file, fileType);
            FileInfo info = new FileInfo(file, fileType, filePath);

            // ✅ 设置上传者ID
            info.setUserId(userId);

            // 先插入数据库获取ID
            fileInfoMapper.insert(info);

            // 使用生成的ID构建文件访问URL
            String fileUrl = "/files/" + fileType + "/" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "/" +
                Paths.get(filePath).getFileName().toString();
            info.setFileUrl(fileUrl);

            // 更新fileUrl
            fileInfoMapper.updateById(info);

            log.info("文件上传成功: {}, 类型: {}, 大小: {} bytes, URL: {}",
                    file.getOriginalFilename(), fileType, file.getSize(), fileUrl);
            return info;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    public ResponseEntity<Resource> download(Long id) {
        FileInfo info = fileInfoMapper.selectById(id);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(info.getFilePath());
        if (!file.exists()) {
            log.error("文件不存在: {}", info.getFilePath());
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + info.getFileName())
                .body(resource);
    }

    /**
     * 保存文件到本地
     * 文件组织结构: uploadDir/fileType/yyyy-MM-dd/uuid_originalFilename
     */
    private String saveToLocal(MultipartFile file, String fileType) throws IOException {
        // 创建日期目录 (例如: files/image/2025-12-14/)
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String typeDir = fileType != null ? fileType : "other";
        Path directoryPath = Paths.get(uploadDir, typeDir, dateDir);

        // 确保目录存在
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
            log.info("创建上传目录: {}", directoryPath);
        }

        // 生成唯一文件名 (UUID + 原始文件名)
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        // 完整文件路径
        Path filePath = directoryPath.resolve(uniqueFilename);

        // 保存文件
        file.transferTo(filePath.toFile());
        log.info("文件已保存到: {}", filePath);

        return filePath.toString();
    }

    /**
     * 删除文件
     */
    public boolean deleteFile(Long id) {
        FileInfo info = fileInfoMapper.selectById(id);
        if (info == null) {
            return false;
        }

        // 删除物理文件
        File file = new File(info.getFilePath());
        if (file.exists()) {
            file.delete();
            log.info("物理文件已删除: {}", info.getFilePath());
        }

        // 删除数据库记录
        fileInfoMapper.deleteById(id);
        log.info("文件记录已删除: id={}", id);

        return true;
    }
}
