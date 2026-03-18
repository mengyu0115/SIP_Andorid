package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 会议聊天消息适配器 — 支持发送/接收/系统三种气泡
 */
public class ConferenceChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SYSTEM = 0;
    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    public static class ChatMsg {
        public String sender;
        public String message;
        public long timestamp;
        public boolean isSystem;
        public boolean isMine;

        public ChatMsg(String sender, String message, boolean isSystem, boolean isMine) {
            this.sender = sender;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.isSystem = isSystem;
            this.isMine = isMine;
        }
    }

    private final List<ChatMsg> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    public int getItemViewType(int position) {
        ChatMsg msg = messages.get(position);
        if (msg.isSystem) return TYPE_SYSTEM;
        if (msg.isMine) return TYPE_SENT;
        return TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_SENT:
                return new SentViewHolder(
                        inflater.inflate(R.layout.item_conference_chat_sent, parent, false));
            case TYPE_RECEIVED:
                return new ReceivedViewHolder(
                        inflater.inflate(R.layout.item_conference_chat_received, parent, false));
            default:
                return new SystemViewHolder(
                        inflater.inflate(R.layout.item_conference_chat_system, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMsg msg = messages.get(position);
        String time = timeFormat.format(new Date(msg.timestamp));

        switch (getItemViewType(position)) {
            case TYPE_SENT: {
                SentViewHolder h = (SentViewHolder) holder;
                h.tvMessage.setText(msg.message);
                h.tvTime.setText(time);
                break;
            }
            case TYPE_RECEIVED: {
                ReceivedViewHolder h = (ReceivedViewHolder) holder;
                h.tvSender.setText(msg.sender);
                h.tvMessage.setText(msg.message);
                h.tvTime.setText(time);
                break;
            }
            default: {
                SystemViewHolder h = (SystemViewHolder) holder;
                h.tvMessage.setText(msg.message);
                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMsg msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    // ---- ViewHolders ----

    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;

        SentViewHolder(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvMessage);
            tvTime = v.findViewById(R.id.tvTime);
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvMessage, tvTime;

        ReceivedViewHolder(View v) {
            super(v);
            tvSender = v.findViewById(R.id.tvSender);
            tvMessage = v.findViewById(R.id.tvMessage);
            tvTime = v.findViewById(R.id.tvTime);
        }
    }

    static class SystemViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;

        SystemViewHolder(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvMessage);
        }
    }
}
