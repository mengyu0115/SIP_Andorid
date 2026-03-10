package com.sip.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import org.springframework.web.multipart.MultipartFile;

@Data
@TableName("file_info")
public class FileInfo {
    private Long id;
    private Long userId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private String fileUrl;
    private String md5;
    private LocalDateTime uploadTime;

    public FileInfo() {}

    public FileInfo(MultipartFile file, String fileType, String filePath) {
        this.fileName = file.getOriginalFilename();
        this.fileSize = file.getSize();
        this.fileType = fileType;
        this.filePath = filePath;
        this.uploadTime = LocalDateTime.now();
    }
}
