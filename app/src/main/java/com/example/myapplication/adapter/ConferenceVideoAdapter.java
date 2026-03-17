package com.example.myapplication.adapter;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 会议视频网格适配器
 */
public class ConferenceVideoAdapter extends RecyclerView.Adapter<ConferenceVideoAdapter.ViewHolder> {

    public static class Participant {
        public long userId;
        public String username;
        public boolean isLocal;
        public boolean videoEnabled;
        public boolean audioEnabled;
        public Bitmap lastFrame;

        public Participant(long userId, String username, boolean isLocal) {
            this.userId = userId;
            this.username = username;
            this.isLocal = isLocal;
            this.videoEnabled = false;
            this.audioEnabled = false;
        }
    }

    private final List<Participant> participants = new ArrayList<>();
    private OnParticipantClickListener clickListener;

    public interface OnParticipantClickListener {
        void onParticipantClick(Participant participant);
    }

    public void setOnParticipantClickListener(OnParticipantClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conference_video, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Participant p = participants.get(position);

        String label = p.isLocal ? p.username + " (我)" : p.username;
        holder.tvUsername.setText(label);

        if (p.lastFrame != null && p.videoEnabled) {
            holder.ivVideo.setImageBitmap(p.lastFrame);
            holder.ivVideo.setVisibility(View.VISIBLE);
            holder.placeholderLayout.setVisibility(View.GONE);
        } else {
            holder.ivVideo.setVisibility(View.GONE);
            holder.placeholderLayout.setVisibility(View.VISIBLE);
        }

        holder.tvAudioStatus.setText(p.audioEnabled ? "" : "🔇");

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onParticipantClick(p);
            }
        });
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    public void addParticipant(Participant p) {
        participants.add(p);
        notifyItemInserted(participants.size() - 1);
    }

    public void removeParticipant(long userId) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).userId == userId) {
                participants.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public Participant findParticipant(long userId) {
        for (Participant p : participants) {
            if (p.userId == userId) return p;
        }
        return null;
    }

    public int findParticipantIndex(long userId) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).userId == userId) return i;
        }
        return -1;
    }

    public void updateVideoFrame(long userId, Bitmap frame) {
        int idx = findParticipantIndex(userId);
        if (idx >= 0) {
            participants.get(idx).lastFrame = frame;
            participants.get(idx).videoEnabled = true;
            notifyItemChanged(idx);
        }
    }

    public void updateMediaStatus(long userId, boolean audioEnabled, boolean videoEnabled) {
        int idx = findParticipantIndex(userId);
        if (idx >= 0) {
            Participant p = participants.get(idx);
            p.audioEnabled = audioEnabled;
            p.videoEnabled = videoEnabled;
            if (!videoEnabled) p.lastFrame = null;
            notifyItemChanged(idx);
        }
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public List<Participant> getParticipants() {
        return new ArrayList<>(participants);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivVideo;
        LinearLayout placeholderLayout;
        TextView tvUsername;
        TextView tvAudioStatus;
        TextView tvVideoStatus;

        ViewHolder(View itemView) {
            super(itemView);
            ivVideo = itemView.findViewById(R.id.ivVideo);
            placeholderLayout = itemView.findViewById(R.id.placeholderLayout);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvAudioStatus = itemView.findViewById(R.id.tvAudioStatus);
            tvVideoStatus = itemView.findViewById(R.id.tvVideoStatus);
        }
    }
}
