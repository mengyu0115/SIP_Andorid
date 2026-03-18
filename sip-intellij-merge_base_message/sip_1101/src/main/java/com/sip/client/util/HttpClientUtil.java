package com.sip.client.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP客户端工具类
 * 用于客户端发起HTTP请求（文件上传、聊天记录加载等）
 */
@Slf4j
public class HttpClientUtil {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static String baseUrl = "http://10.29.209.85:8081";
    private static String authToken = null;

    /**
     * 设置Base URL
     */
    public static void setBaseUrl(String url) {
        baseUrl = url;
    }

    /**
     * 获取Base URL
     */
    public static String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置认证Token
     */
    public static void setAuthToken(String token) {
        authToken = token;
    }

    /**
     * 获取认证Token
     */
    public static String getAuthToken() {
        return authToken;
    }

    /**
     * GET请求
     */
    public static <T> T get(String path, Class<T> responseType) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(30));

        if (authToken != null) {
            requestBuilder.header("Authorization", "Bearer " + authToken);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("GET {} - Status: {}", path, response.statusCode());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP错误: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), responseType);
    }

    /**
     * GET请求（返回原始字符串响应）
     */
    public static String getRaw(String path) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(30));

        if (authToken != null) {
            requestBuilder.header("Authorization", "Bearer " + authToken);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("GET {} - Status: {}", path, response.statusCode());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP错误: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    /**
     * POST请求（JSON）
     */
    public static <T> T post(String path, Object requestBody, Class<T> responseType) throws IOException, InterruptedException {
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30));

        if (authToken != null) {
            requestBuilder.header("Authorization", "Bearer " + authToken);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("POST {} - Status: {}", path, response.statusCode());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP错误: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), responseType);
    }

    /**
     * 上传文件（multipart/form-data）
     */
    public static <T> T uploadFile(String path, File file, Map<String, String> formData, Class<T> responseType)
            throws IOException, InterruptedException {

        String boundary = "----Boundary" + UUID.randomUUID().toString().replaceAll("-", "");
        byte[] multipartBody = buildMultipartBody(file, formData, boundary);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .timeout(Duration.ofSeconds(60)); // 文件上传超时时间更长

        if (authToken != null) {
            requestBuilder.header("Authorization", "Bearer " + authToken);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("上传文件 {} - Status: {}, 文件: {}", path, response.statusCode(), file.getName());

        if (response.statusCode() != 200) {
            log.error("上传失败响应: {}", response.body());
            throw new IOException("文件上传失败: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), responseType);
    }

    /**
     * 构建multipart/form-data请求体
     */
    private static byte[] buildMultipartBody(File file, Map<String, String> formData, String boundary) throws IOException {
        List<byte[]> parts = new ArrayList<>();
        String lineSeparator = "\r\n";

        // 添加表单字段
        if (formData != null) {
            for (Map.Entry<String, String> entry : formData.entrySet()) {
                StringBuilder part = new StringBuilder();
                part.append("--").append(boundary).append(lineSeparator);
                part.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"").append(lineSeparator);
                part.append(lineSeparator);
                part.append(entry.getValue()).append(lineSeparator);
                parts.add(part.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        // 添加文件字段
        StringBuilder filePart = new StringBuilder();
        filePart.append("--").append(boundary).append(lineSeparator);
        filePart.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(file.getName()).append("\"").append(lineSeparator);

        // 根据文件扩展名设置Content-Type
        String contentType = getContentType(file.getName());
        filePart.append("Content-Type: ").append(contentType).append(lineSeparator);
        filePart.append(lineSeparator);
        parts.add(filePart.toString().getBytes(StandardCharsets.UTF_8));

        // 添加文件内容
        byte[] fileContent = Files.readAllBytes(file.toPath());
        parts.add(fileContent);
        parts.add(lineSeparator.getBytes(StandardCharsets.UTF_8));

        // 添加结束边界
        String endBoundary = "--" + boundary + "--" + lineSeparator;
        parts.add(endBoundary.getBytes(StandardCharsets.UTF_8));

        // 合并所有部分
        int totalLength = parts.stream().mapToInt(p -> p.length).sum();
        byte[] result = new byte[totalLength];
        int pos = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, pos, part.length);
            pos += part.length;
        }

        return result;
    }

    /**
     * 根据文件名获取Content-Type
     */
    private static String getContentType(String filename) {
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFilename.endsWith(".png")) {
            return "image/png";
        } else if (lowerFilename.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFilename.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lowerFilename.endsWith(".wav")) {
            return "audio/wav";
        } else if (lowerFilename.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerFilename.endsWith(".avi")) {
            return "video/x-msvideo";
        } else {
            return "application/octet-stream";
        }
    }

    /**
     * 下载文件
     */
    public static File downloadFile(String path, File saveToFile) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(60));

        if (authToken != null) {
            requestBuilder.header("Authorization", "Bearer " + authToken);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        log.info("下载文件 {} - Status: {}", path, response.statusCode());

        if (response.statusCode() != 200) {
            throw new IOException("文件下载失败: " + response.statusCode());
        }

        Files.write(saveToFile.toPath(), response.body());
        log.info("文件已保存到: {}", saveToFile.getAbsolutePath());

        return saveToFile;
    }
}
