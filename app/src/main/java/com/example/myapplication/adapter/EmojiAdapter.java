package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 表情面板 Adapter（复用 app_copy EmojiAdapter 设计）
 */
public class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.ViewHolder> {

    public interface OnEmojiClickListener {
        void onEmojiClick(String emoji);
    }

    private final List<String> emojis;
    private final OnEmojiClickListener listener;

    public EmojiAdapter(List<String> emojis, OnEmojiClickListener listener) {
        this.emojis = emojis;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setTextSize(24);
        tv.setGravity(android.view.Gravity.CENTER);
        int size = (int) (40 * parent.getContext().getResources().getDisplayMetrics().density);
        tv.setLayoutParams(new RecyclerView.LayoutParams(size, size));
        tv.setBackground(getSelectableBackground(parent));
        return new ViewHolder(tv);
    }

    private android.graphics.drawable.Drawable getSelectableBackground(ViewGroup parent) {
        int[] attrs = {android.R.attr.selectableItemBackgroundBorderless};
        android.content.res.TypedArray ta = parent.getContext().obtainStyledAttributes(attrs);
        android.graphics.drawable.Drawable drawable = ta.getDrawable(0);
        ta.recycle();
        return drawable;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String emoji = emojis.get(position);
        ((TextView) holder.itemView).setText(emoji);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEmojiClick(emoji);
        });
    }

    @Override
    public int getItemCount() {
        return emojis.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
