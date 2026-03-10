package com.example.myapplication.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 联系人列表 RecyclerView 适配器
 *
 * 对应 PC 端 MainController 中 UserListCell 的渲染逻辑：
 * - 在线用户显示绿色圆点 (#4CAF50)
 * - 离线用户显示红色圆点 (#F44336)
 * - 用户名颜色 #333333
 */
public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {

    /** PC 端 UserListCell 颜色常量 */
    private static final int COLOR_ONLINE = Color.parseColor("#4CAF50");
    private static final int COLOR_OFFLINE = Color.parseColor("#F44336");

    public interface OnItemClickListener {
        void onItemClick(ContactItem item);
    }

    private final List<ContactItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * 提交新数据列表（全量替换）
     * 与 ConversationAdapter.submitList() 保持一致的模式
     */
    public void submitList(List<ContactItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContactItem item = items.get(position);

        // 用户名（对应 PC 端 UserListCell 的 usernameLabel）
        holder.tvContactName.setText(item.getUsername());

        // 在线状态圆点（对应 PC 端 statusDot：绿色在线 / 红色离线）
        // mutate() 避免多个 ViewHolder 共享同一 Drawable 实例导致颜色混乱
        GradientDrawable dot = (GradientDrawable) holder.viewOnlineDot.getBackground().mutate();
        dot.setColor(item.isOnline() ? COLOR_ONLINE : COLOR_OFFLINE);

        // 点击事件（对应 PC 端 friendListView 的 selectedItemProperty → openChat）
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvContactName;
        final View viewOnlineDot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContactName = itemView.findViewById(R.id.tvContactName);
            viewOnlineDot = itemView.findViewById(R.id.viewOnlineDot);
        }
    }
}
