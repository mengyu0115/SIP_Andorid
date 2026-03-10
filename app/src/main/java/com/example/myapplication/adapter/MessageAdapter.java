package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 聊天消息列表 Adapter
 *
 * 复用 app_copy MessageAdapter 的三种 viewType 设计（Java 重写）：
 * - TYPE_SENT(1)：我发送的消息，右对齐绿色气泡
 * - TYPE_RECEIVED(2)：对方发送的消息，左对齐灰色气泡
 * - TYPE_TIMESTAMP(3)：时间戳分隔符，居中灰色文字
 *
 * 每隔 5 分钟自动插入时间戳分隔符（同 app_copy 逻辑）
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final long TIME_GAP_MS = 5 * 60 * 1000L;

    private final List<ChatMessage> items = new ArrayList<>();

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
                return new SentViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
            case ChatMessage.TYPE_RECEIVED:
                return new ReceivedViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
            case ChatMessage.TYPE_TIMESTAMP:
                return new TimestampViewHolder(inflater.inflate(R.layout.item_timestamp, parent, false));
            default:
                throw new IllegalArgumentException("Unknown viewType: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = items.get(position);
        if (holder instanceof SentViewHolder) {
            ((SentViewHolder) holder).bind(msg);
        } else if (holder instanceof ReceivedViewHolder) {
            ((ReceivedViewHolder) holder).bind(msg);
        } else if (holder instanceof TimestampViewHolder) {
            ((TimestampViewHolder) holder).bind(msg.getTimestamp());
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * 提交新消息列表（插入时间戳分隔符）
     */
    public void submitList(List<ChatMessage> messages) {
        items.clear();
        items.addAll(insertTimestamps(messages));
        notifyDataSetChanged();
    }

    /**
     * 追加单条消息（自动判断是否插入时间戳）
     */
    public void appendMessage(ChatMessage msg) {
        long lastTime = items.isEmpty() ? 0L
                : items.get(items.size() - 1).getTimestamp();
        if (msg.getTimestamp() - lastTime >= TIME_GAP_MS) {
            items.add(ChatMessage.timestamp(msg.getTimestamp()));
            notifyItemInserted(items.size() - 1);
        }
        items.add(msg);
        notifyItemInserted(items.size() - 1);
    }

    // ===== 内部：插入时间戳 =====

    private List<ChatMessage> insertTimestamps(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        long lastTs = 0L;
        for (ChatMessage msg : messages) {
            if (msg.getTimestamp() - lastTs >= TIME_GAP_MS) {
                result.add(ChatMessage.timestamp(msg.getTimestamp()));
                lastTs = msg.getTimestamp();
            }
            result.add(msg);
        }
        return result;
    }

    // ===== 时间格式化（同 app_copy） =====

    public static String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        long today = todayCal.getTimeInMillis();
        long yesterday = today - 24 * 60 * 60 * 1000L;

        if (timestamp >= today) {
            return "今天 " + formatTime(timestamp);
        } else if (timestamp >= yesterday) {
            return "昨天 " + formatTime(timestamp);
        } else {
            return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
        }
    }

    // ===== ViewHolder =====

    static class SentViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;
        final TextView tvTime;

        SentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        void bind(ChatMessage msg) {
            tvMessage.setText(msg.getContent());
            tvTime.setText(formatTime(msg.getTimestamp()));
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;
        final TextView tvTime;

        ReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        void bind(ChatMessage msg) {
            tvMessage.setText(msg.getContent());
            tvTime.setText(formatTime(msg.getTimestamp()));
        }
    }

    static class TimestampViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTimestamp;

        TimestampViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }

        void bind(long timestamp) {
            tvTimestamp.setText(formatTimestamp(timestamp));
        }
    }
}
