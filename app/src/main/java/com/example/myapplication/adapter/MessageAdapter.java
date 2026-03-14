package com.example.myapplication.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.model.ChatMessage;
import com.example.myapplication.network.ServerConfig;
import com.example.myapplication.utils.FileDownloadManager;
import com.example.myapplication.utils.VoicePlayManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 聊天消息列表 Adapter
 *
 * QQ风格按天分组设计：
 * - TYPE_SENT(1)：我发送的消息，右对齐绿色气泡
 * - TYPE_RECEIVED(2)：对方发送的消息，左对齐灰色气泡
 * - TYPE_DATE_HEADER(3)：日期分组头，居中显示
 *
 * 支持多种消息类型：
 * - 文字消息
 * - 图片消息（可点击查看大图）
 * - 文件消息（可点击下载）
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "MessageAdapter";
    private final List<ChatMessage> items = new ArrayList<>();
    private final Context context;

    public MessageAdapter(Context context) {
        this.context = context;
    }

    /**
     * 构建完整的文件URL（如果不是完整URL则拼接服务器地址）
     */
    private static String buildFullUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return "";
        }
        // 如果已经是完整的 http/https URL，直接返回
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            return fileUrl;
        }
        // 否则拼接服务器地址
        String fullUrl = ServerConfig.getBaseUrl() + fileUrl;
        Log.d(TAG, "构建完整URL: " + fileUrl + " -> " + fullUrl);
        return fullUrl;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getViewType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case ChatMessage.TYPE_SENT:
                return new SentTextViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
            case ChatMessage.TYPE_RECEIVED:
                return new ReceivedTextViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
            case ChatMessage.TYPE_DATE_HEADER:
                return new DateHeaderHolder(inflater.inflate(R.layout.item_date_header, parent, false));
            default:
                throw new IllegalArgumentException("Unknown viewType: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = items.get(position);
        if (holder instanceof SentTextViewHolder) {
            ((SentTextViewHolder) holder).bind(msg);
        } else if (holder instanceof ReceivedTextViewHolder) {
            ((ReceivedTextViewHolder) holder).bind(msg);
        } else if (holder instanceof DateHeaderHolder) {
            ((DateHeaderHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * 提交消息列表（自动按天分组）
     */
    public void submitList(List<ChatMessage> messages) {
        items.clear();
        items.addAll(groupByDate(messages));
        notifyDataSetChanged();
    }

    /**
     * 追加单条消息（自动判断是否需要新的日期分组）
     */
    public void appendMessage(ChatMessage msg) {
        // 获取上一条消息的日期
        long lastTimestamp = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatMessage item = items.get(i);
            if (item.getViewType() != ChatMessage.TYPE_DATE_HEADER) {
                lastTimestamp = item.getTimestamp();
                break;
            }
        }

        // 判断是否需要插入新的日期头
        if (!isSameDay(msg.getTimestamp(), lastTimestamp)) {
            items.add(ChatMessage.dateHeader(msg.getTimestamp()));
            notifyItemInserted(items.size() - 1);
        }

        items.add(msg);
        notifyItemInserted(items.size() - 1);
    }

    /**
     * 按日期分组消息
     */
    private List<ChatMessage> groupByDate(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        long lastDate = 0;

        for (ChatMessage msg : messages) {
            // 获取消息的日期（去掉时分秒）
            long msgDate = getDayStart(msg.getTimestamp());

            // 如果日期不同，插入日期头
            if (msgDate != lastDate) {
                result.add(ChatMessage.dateHeader(msg.getTimestamp()));
                lastDate = msgDate;
            }

            // 添加消息
            result.add(msg);
        }

        return result;
    }

    /**
     * 判断两个时间戳是否是同一天
     */
    private boolean isSameDay(long timestamp1, long timestamp2) {
        if (timestamp2 == 0) return false;
        return getDayStart(timestamp1) == getDayStart(timestamp2);
    }

    /**
     * 获取某天0点的时间戳
     */
    private long getDayStart(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 格式化消息时间：月日时分
     * 例如："03-12 15:30"
     */
    public static String formatDateTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        return sdf.format(new Date(timestamp));
    }

    /**
     * 格式化文件大小
     */
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    // ===== ViewHolder =====

    /**
     * 发送方文字消息 ViewHolder
     */
    static class SentTextViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;
        final TextView tvTime;

        SentTextViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        void bind(ChatMessage msg) {
            if (msg.isImage()) {
                // 图片消息：使用ImageView
                tvMessage.setVisibility(View.GONE);
                ImageView ivImage = itemView.findViewById(R.id.ivMessageImage);
                if (ivImage != null) {
                    ivImage.setVisibility(View.VISIBLE);

                    String fileUrl = msg.getFileUrl();
                    if (fileUrl != null && !fileUrl.isEmpty()) {
                        // 构建完整的图片URL
                        String fullUrl = buildFullUrl(fileUrl);

                        // 有 URL：加载图片
                        Glide.with(itemView.getContext())
                                .load(fullUrl)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_gallery)
                                .into(ivImage);

                        // 点击查看大图
                        ivImage.setOnClickListener(v -> {
                            android.widget.Toast.makeText(
                                    v.getContext(),
                                    "正在下载图片...",
                                    android.widget.Toast.LENGTH_SHORT
                            ).show();
                            FileDownloadManager.getInstance().downloadFile(
                                    fullUrl,
                                    msg.getFileName() != null ? msg.getFileName() : "image.jpg",
                                    new FileDownloadManager.DownloadCallback() {
                                        @Override
                                        public void onProgress(int percent) {
                                            // 可以在这里更新进度
                                        }

                                        @Override
                                        public void onSuccess(File file) {
                                            FileDownloadManager.openFile(v.getContext(), file);
                                        }

                                        @Override
                                        public void onError(String error) {
                                            android.widget.Toast.makeText(
                                                    v.getContext(),
                                                    "下载失败: " + error,
                                                    android.widget.Toast.LENGTH_SHORT
                                            ).show();
                                        }
                                    }
                            );
                        });
                    } else {
                        // 无 URL：显示占位符和文本提示
                        Glide.with(itemView.getContext())
                                .clear(ivImage);
                        ivImage.setImageResource(android.R.drawable.ic_menu_gallery);

                        // 点击显示提示
                        ivImage.setOnClickListener(v -> {
                            android.widget.Toast.makeText(
                                    v.getContext(),
                                    "图片上传中...",
                                    android.widget.Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                }
            } else if (msg.isVoice()) {
                // 语音消息：显示语音时长和播放按钮
                tvMessage.setVisibility(View.VISIBLE);
                String fullUrl = buildFullUrl(msg.getFileUrl());
                boolean isPlaying = VoicePlayManager.getInstance().isPlaying(fullUrl);
                String voiceText = (isPlaying ? "🔊" : "🎤") + " [语音] " + (msg.getDuration() != null ? msg.getDuration() + "秒" : "");
                tvMessage.setText(voiceText);
                tvMessage.setOnClickListener(v -> {
                    VoicePlayManager.getInstance().playUrl(
                            fullUrl,
                            new VoicePlayManager.PlayCallback() {
                                @Override
                                public void onStart() {
                                    // 播放开始，更新显示
                                    tvMessage.setText("🔊 [语音] " + (msg.getDuration() != null ? msg.getDuration() + "秒" : ""));
                                }

                                @Override
                                public void onCompletion() {
                                    // 播放完成，恢复显示
                                    tvMessage.setText("🎤 [语音] " + (msg.getDuration() != null ? msg.getDuration() + "秒" : ""));
                                }

                                @Override
                                public void onError(String error) {
                                    android.widget.Toast.makeText(
                                            v.getContext(),
                                            "播放失败: " + error,
                                            android.widget.Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                    );
                });
            } else if (msg.isVideo()) {
                // 视频消息：显示视频缩略图或文本
                tvMessage.setVisibility(View.VISIBLE);
                String videoText = "🎬 [视频] " + (msg.getFileName() != null ? msg.getFileName() : "");
                tvMessage.setText(videoText);
                tvMessage.setOnClickListener(v -> {
                    String fullUrl = buildFullUrl(msg.getFileUrl());
                    android.widget.Toast.makeText(
                            v.getContext(),
                            "正在下载视频...",
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                    FileDownloadManager.getInstance().downloadFile(
                            fullUrl,
                            msg.getFileName() != null ? msg.getFileName() : "video.mp4",
                            new FileDownloadManager.DownloadCallback() {
                                @Override
                                public void onProgress(int percent) {
                                }

                                @Override
                                public void onSuccess(File file) {
                                    FileDownloadManager.openFile(v.getContext(), file);
                                }

                                @Override
                                public void onError(String error) {
                                    android.widget.Toast.makeText(
                                            v.getContext(),
                                            "下载失败: " + error,
                                            android.widget.Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                    );
                });
            } else if (msg.isFile()) {
                // 文件消息：显示文件信息
                tvMessage.setVisibility(View.GONE);
                View fileContainer = itemView.findViewById(R.id.fileContainer);
                if (fileContainer != null) {
                    fileContainer.setVisibility(View.VISIBLE);
                    TextView tvFileName = fileContainer.findViewById(R.id.tvFileName);
                    TextView tvFileSize = fileContainer.findViewById(R.id.tvFileSize);
                    tvFileName.setText(msg.getFileName());
                    tvFileSize.setText(formatFileSize(msg.getFileSize()));

                    // 点击下载文件
                    fileContainer.setOnClickListener(v -> {
                        String fullUrl = buildFullUrl(msg.getFileUrl());
                        android.widget.Toast.makeText(
                                v.getContext(),
                                "正在下载文件...",
                                android.widget.Toast.LENGTH_SHORT
                        ).show();
                        FileDownloadManager.getInstance().downloadFile(
                                fullUrl,
                                msg.getFileName() != null ? msg.getFileName() : "file",
                                new FileDownloadManager.DownloadCallback() {
                                    @Override
                                    public void onProgress(int percent) {
                                        // 可以在这里更新进度
                                    }

                                    @Override
                                    public void onSuccess(File file) {
                                        FileDownloadManager.openFile(v.getContext(), file);
                                    }

                                    @Override
                                    public void onError(String error) {
                                        android.widget.Toast.makeText(
                                                v.getContext(),
                                                "下载失败: " + error,
                                                android.widget.Toast.LENGTH_SHORT
                                        ).show();
                                    }
                                }
                        );
                    });
                }
            } else {
                // 文字消息：显示文本
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText(msg.getContent());
            }
            tvTime.setText(formatDateTime(msg.getTimestamp()));
        }
    }

    /**
     * 接收方文字消息 ViewHolder
     */
    static class ReceivedTextViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;
        final TextView tvTime;

        ReceivedTextViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        void bind(ChatMessage msg) {
            if (msg.isImage()) {
                // 图片消息
                tvMessage.setVisibility(View.GONE);
                ImageView ivImage = itemView.findViewById(R.id.ivMessageImage);
                if (ivImage != null) {
                    ivImage.setVisibility(View.VISIBLE);

                    String fileUrl = msg.getFileUrl();
                    if (fileUrl != null && !fileUrl.isEmpty()) {
                        // 构建完整的图片URL
                        String fullUrl = buildFullUrl(fileUrl);

                        // 有 URL：加载图片
                        Glide.with(itemView.getContext())
                                .load(fullUrl)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_gallery)
                                .into(ivImage);

                        // 点击查看大图
                        ivImage.setOnClickListener(v -> {
                            android.widget.Toast.makeText(
                                    v.getContext(),
                                    "正在下载图片...",
                                    android.widget.Toast.LENGTH_SHORT
                            ).show();
                            FileDownloadManager.getInstance().downloadFile(
                                    fullUrl,
                                    msg.getFileName() != null ? msg.getFileName() : "image.jpg",
                                    new FileDownloadManager.DownloadCallback() {
                                        @Override
                                        public void onProgress(int percent) {
                                            // 可以在这里更新进度
                                        }

                                        @Override
                                        public void onSuccess(File file) {
                                            FileDownloadManager.openFile(v.getContext(), file);
                                        }

                                        @Override
                                        public void onError(String error) {
                                            android.widget.Toast.makeText(
                                                    v.getContext(),
                                                    "下载失败: " + error,
                                                    android.widget.Toast.LENGTH_SHORT
                                            ).show();
                                        }
                                    }
                            );
                        });
                    } else {
                        // 无 URL：显示占位符和文本提示
                        Glide.with(itemView.getContext())
                                .clear(ivImage);
                        ivImage.setImageResource(android.R.drawable.ic_menu_gallery);

                        // 点击显示提示
                        ivImage.setOnClickListener(v -> {
                            android.widget.Toast.makeText(
                                    v.getContext(),
                                    "图片上传中...",
                                    android.widget.Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                }
            } else if (msg.isVoice()) {
                // 语音消息
                tvMessage.setVisibility(View.VISIBLE);
                String fullUrl = buildFullUrl(msg.getFileUrl());
                boolean isPlaying = VoicePlayManager.getInstance().isPlaying(fullUrl);
                String voiceText = (isPlaying ? "🔊" : "🎤") + " [语音] " + (msg.getDuration() != null ? msg.getDuration() + "秒" : "");
                tvMessage.setText(voiceText);
                tvMessage.setOnClickListener(v -> {
                    VoicePlayManager.getInstance().playUrl(
                            fullUrl,
                            new VoicePlayManager.PlayCallback() {
                                @Override
                                public void onStart() {
                                    // 播放开始，更新显示
                                    tvMessage.setText("🔊 [语音] " + (msg.getDuration() != null ? msg.getDuration() + "秒" : ""));
                                }

                                @Override
                                public void onCompletion() {
                                    // 播放完成，恢复显示
                                    tvMessage.setText("🎤 [语音] " + (msg.getDuration() != null ? msg.getDuration() + "秒" : ""));
                                }

                                @Override
                                public void onError(String error) {
                                    android.widget.Toast.makeText(
                                            v.getContext(),
                                            "播放失败: " + error,
                                            android.widget.Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                    );
                });
            } else if (msg.isVideo()) {
                // 视频消息
                tvMessage.setVisibility(View.VISIBLE);
                String videoText = "🎬 [视频] " + (msg.getFileName() != null ? msg.getFileName() : "");
                tvMessage.setText(videoText);
                tvMessage.setOnClickListener(v -> {
                    String fullUrl = buildFullUrl(msg.getFileUrl());
                    android.widget.Toast.makeText(
                            v.getContext(),
                            "正在下载视频...",
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                    FileDownloadManager.getInstance().downloadFile(
                            fullUrl,
                            msg.getFileName() != null ? msg.getFileName() : "video.mp4",
                            new FileDownloadManager.DownloadCallback() {
                                @Override
                                public void onProgress(int percent) {
                                }

                                @Override
                                public void onSuccess(File file) {
                                    FileDownloadManager.openFile(v.getContext(), file);
                                }

                                @Override
                                public void onError(String error) {
                                    android.widget.Toast.makeText(
                                            v.getContext(),
                                            "下载失败: " + error,
                                            android.widget.Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                    );
                });
            } else if (msg.isFile()) {
                // 文件消息
                tvMessage.setVisibility(View.GONE);
                View fileContainer = itemView.findViewById(R.id.fileContainer);
                if (fileContainer != null) {
                    fileContainer.setVisibility(View.VISIBLE);
                    TextView tvFileName = fileContainer.findViewById(R.id.tvFileName);
                    TextView tvFileSize = fileContainer.findViewById(R.id.tvFileSize);
                    tvFileName.setText(msg.getFileName());
                    tvFileSize.setText(formatFileSize(msg.getFileSize()));

                    // 点击下载文件
                    fileContainer.setOnClickListener(v -> {
                        String fullUrl = buildFullUrl(msg.getFileUrl());
                        android.widget.Toast.makeText(
                                v.getContext(),
                                "正在下载文件...",
                                android.widget.Toast.LENGTH_SHORT
                        ).show();
                        FileDownloadManager.getInstance().downloadFile(
                                fullUrl,
                                msg.getFileName() != null ? msg.getFileName() : "file",
                                new FileDownloadManager.DownloadCallback() {
                                    @Override
                                    public void onProgress(int percent) {
                                        // 可以在这里更新进度
                                    }

                                    @Override
                                    public void onSuccess(File file) {
                                        FileDownloadManager.openFile(v.getContext(), file);
                                    }

                                    @Override
                                    public void onError(String error) {
                                        android.widget.Toast.makeText(
                                                v.getContext(),
                                                "下载失败: " + error,
                                                android.widget.Toast.LENGTH_SHORT
                                        ).show();
                                    }
                                }
                        );
                    });
                }
            } else {
                // 文字消息
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText(msg.getContent());
            }
            tvTime.setText(formatDateTime(msg.getTimestamp()));
        }
    }

    /**
     * 日期分组头 ViewHolder
     */
    static class DateHeaderHolder extends RecyclerView.ViewHolder {
        final TextView tvDateHeader;

        DateHeaderHolder(@NonNull View itemView) {
            super(itemView);
            tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
        }

        void bind(ChatMessage msg) {
            tvDateHeader.setText(msg.getDateText());
        }
    }
}
