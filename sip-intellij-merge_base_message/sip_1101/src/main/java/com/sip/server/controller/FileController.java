package com.sip.server.controller;

import com.sip.server.response.ApiResponse;
import com.sip.server.entity.FileInfo;
import com.sip.server.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件控制器
 * 提供文件上传和根据ID下载的接口
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public ApiResponse<FileInfo> uploadFile(@RequestParam("file") MultipartFile file,
                                            @RequestParam(required = false) String fileType,
                                            @RequestParam Long userId) {
        return ApiResponse.success(fileService.upload(file, fileType, userId));
    }

    /**
     * 根据ID下载文件
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        return fileService.download(id);
    }
}
