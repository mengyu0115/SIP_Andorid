package com.example.myapplication.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文件下载管理器
 * 用于在 App 内下载图片、文件等
 */
public class FileDownloadManager {
    private static final String TAG = "FileDownloadManager";
    private static FileDownloadManager instance;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private Context context;  // 移到这里，作为实例字段

    public interface DownloadCallback {
        void onProgress(int percent);
        void onSuccess(File file);
        void onError(String error);
    }

    private FileDownloadManager() {}

    public static synchronized FileDownloadManager getInstance() {
        if (instance == null) {
            instance = new FileDownloadManager();
        }
        return instance;
    }

    /**
     * 下载文件
     * @param fileUrl 文件 URL
     * @param fileName 原始文件名（用于获取扩展名）
     * @param callback 下载回调
     */
    public void downloadFile(String fileUrl, String fileName, DownloadCallback callback) {
        executor.execute(() -> {
            File outputFile = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            HttpURLConnection conn = null;

            try {
                URL url = new URL(fileUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(60_000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    callback.onError("服务器返回错误: " + responseCode);
                    return;
                }

                int contentLength = conn.getContentLength();
                inputStream = conn.getInputStream();

                // 确定输出目录和文件名
                String extension = getFileExtension(fileName, conn.getContentType());
                String outputFileName = generateOutputFileName(fileName, extension);

                File downloadDir = getDownloadDirectory();
                if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                    callback.onError("无法创建下载目录");
                    return;
                }

                outputFile = new File(downloadDir, outputFileName);

                // 如果文件已存在，添加时间戳避免冲突
                if (outputFile.exists()) {
                    String nameWithoutExt = outputFileName.substring(0, outputFileName.lastIndexOf('.'));
                    String ext = outputFileName.substring(outputFileName.lastIndexOf('.'));
                    outputFileName = nameWithoutExt + "_" + System.currentTimeMillis() + ext;
                    outputFile = new File(downloadDir, outputFileName);
                }

                outputStream = new FileOutputStream(outputFile);

                byte[] buffer = new byte[4096];
                long totalBytesRead = 0;
                int bytesRead;
                int lastPercent = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (contentLength > 0) {
                        int percent = (int) ((totalBytesRead * 100) / contentLength);
                        if (percent > lastPercent) {
                            lastPercent = percent;
                            final int currentPercent = percent;
                            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(() -> callback.onProgress(currentPercent));
                        }
                    }
                }

                outputStream.flush();

                final File finalOutputFile = outputFile;
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onSuccess(finalOutputFile));

            } catch (Exception e) {
                Log.e(TAG, "下载失败", e);
                callback.onError("下载失败: " + e.getMessage());
            } finally {
                // 确保资源被正确关闭
                try {
                    if (outputStream != null) outputStream.close();
                    if (inputStream != null) inputStream.close();
                    if (conn != null) conn.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "关闭资源失败", e);
                }
            }
        });
    }

    /**
     * 打开文件
     */
    public static void openFile(Context context, File file) {
        try {
            if (!file.exists()) {
                android.widget.Toast.makeText(context, "文件不存在", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        file
                );
            } else {
                uri = Uri.fromFile(file);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mimeType = getMimeType(file.getName());
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // 检查是否有应用可以处理这个文件
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                android.widget.Toast.makeText(context, "没有找到可以打开此文件的应用", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "打开文件失败", e);
            android.widget.Toast.makeText(context, "无法打开文件: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 获取下载目录
     */
    private File getDownloadDirectory() {
        File downloadDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用应用专属目录，不需要权限
            downloadDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SIP_Messenger");
        } else {
            // Android 9 以下使用公共下载目录
            downloadDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "SIP_Messenger");
        }
        return downloadDir;
    }

    /**
     * 初始化上下文（必须在首次使用前调用）
     */
    public void init(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 生成输出文件名
     */
    private String generateOutputFileName(String originalName, String extension) {
        if (originalName != null && !originalName.isEmpty()) {
            // 移除可能包含的路径
            int lastSlash = originalName.lastIndexOf('/');
            if (lastSlash >= 0) {
                originalName = originalName.substring(lastSlash + 1);
            }
            // 确保有扩展名
            if (!originalName.contains(".")) {
                return originalName + "." + extension;
            }
            return originalName;
        }
        return "file_" + System.currentTimeMillis() + "." + extension;
    }

    /**
     * 获取文件扩展名（从文件名或 Content-Type）
     */
    private String getFileExtension(String fileName, String contentType) {
        // 首先尝试从文件名获取
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1);
        }

        // 从 Content-Type 获取
        if (contentType != null) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);
            if (extension != null) {
                return extension;
            }

            // 常见类型映射
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
            if (contentType.contains("png")) return "png";
            if (contentType.contains("gif")) return "gif";
            if (contentType.contains("webp")) return "webp";
            if (contentType.contains("mp4")) return "mp4";
            if (contentType.contains("webm")) return "webm";
            if (contentType.contains("mpeg") || contentType.contains("mp3")) return "mp3";
            if (contentType.contains("m4a")) return "m4a";
            if (contentType.contains("wav")) return "wav";
            if (contentType.contains("pdf")) return "pdf";
            if (contentType.contains("document") || contentType.contains("word")) return "doc";
            if (contentType.contains("sheet") || contentType.contains("excel")) return "xls";
            if (contentType.contains("presentation") || contentType.contains("powerpoint")) return "ppt";
        }

        return "bin";
    }

    /**
     * 获取 MIME 类型
     */
    private static String getMimeType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String type = map.getMimeTypeFromExtension(extension);
        return type != null ? type : "*/*";
    }
}
