package com.example.myapplication.fragment;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.ConferenceActivity;
import com.example.myapplication.R;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ServerConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 会议 Tab Fragment
 * 提供创建和加入会议的入口 + 历史会议列表
 */
public class ConferenceFragment extends Fragment {

    private static final String TAG = "ConferenceFragment";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private RecyclerView rvHistory;
    private TextView tvHistoryEmpty;
    private final List<HistoryItem> historyItems = new ArrayList<>();
    private RecyclerView.Adapter<?> historyAdapter;

    private static class HistoryItem {
        long id;
        String title;
        String conferenceCode;
        String status;
        String createdAt;

        HistoryItem(long id, String title, String conferenceCode, String status, String createdAt) {
            this.id = id;
            this.title = title;
            this.conferenceCode = conferenceCode;
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_conference, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnCreateConference).setOnClickListener(v -> showCreateDialog());
        view.findViewById(R.id.btnJoinConference).setOnClickListener(v -> showJoinDialog());

        rvHistory = view.findViewById(R.id.rvConferenceHistory);
        tvHistoryEmpty = view.findViewById(R.id.tvHistoryEmpty);

        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyAdapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_conference_history, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                HistoryItem item = historyItems.get(position);
                TextView tvTitle = holder.itemView.findViewById(R.id.tvHistoryTitle);
                TextView tvCode = holder.itemView.findViewById(R.id.tvHistoryCode);
                TextView tvStatus = holder.itemView.findViewById(R.id.tvHistoryStatus);
                TextView tvTime = holder.itemView.findViewById(R.id.tvHistoryTime);

                tvTitle.setText(item.title);
                tvCode.setText("会议号: " + item.conferenceCode);

                boolean active = "ACTIVE".equals(item.status);
                tvStatus.setText(active ? "进行中" : "已结束");
                tvStatus.setBackgroundColor(active ? 0xFFE8F5E9 : 0xFFEEEEEE);
                tvStatus.setTextColor(active ? 0xFF4CAF50 : 0xFF999999);

                tvTime.setText(item.createdAt != null ? item.createdAt : "");

                if (active) {
                    holder.itemView.setOnClickListener(v ->
                            ConferenceActivity.start(requireContext(), item.id, item.conferenceCode, item.title)
                    );
                } else {
                    holder.itemView.setOnClickListener(null);
                }
            }

            @Override
            public int getItemCount() {
                return historyItems.size();
            }
        };
        rvHistory.setAdapter(historyAdapter);

        loadConferenceHistory();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadConferenceHistory();
    }

    private void showCreateDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("输入会议标题");
        input.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(requireContext())
                .setTitle("创建会议")
                .setView(input)
                .setPositiveButton("创建", (dialog, which) -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(requireContext(), "请输入会议标题", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createConference(title);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showJoinDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("输入6位会议号");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(requireContext())
                .setTitle("加入会议")
                .setView(input)
                .setPositiveButton("加入", (dialog, which) -> {
                    String code = input.getText().toString().trim();
                    if (code.isEmpty()) {
                        Toast.makeText(requireContext(), "请输入会议号", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    joinConference(code);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createConference(String title) {
        executor.execute(() -> {
            try {
                String response = ApiClient.createConference(title);
                if (response == null) {
                    showError("创建会议失败：网络错误");
                    return;
                }

                JSONObject root = new JSONObject(response);
                int code = root.optInt("code", -1);
                if (code == 200 && root.has("data")) {
                    JSONObject data = root.getJSONObject("data");
                    long confId = data.optLong("id", -1);
                    String confCode = data.optString("conferenceCode", "");
                    String confTitle = data.optString("title", title);

                    Log.i(TAG, "Conference created: id=" + confId + ", code=" + confCode);

                    requireActivity().runOnUiThread(() -> {
                        showConferenceCreatedDialog(confId, confCode, confTitle);
                        loadConferenceHistory();
                    });
                } else {
                    showError("创建会议失败: " + root.optString("message", "未知错误"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Create conference failed", e);
                showError("创建会议异常: " + e.getMessage());
            }
        });
    }

    private void showConferenceCreatedDialog(long confId, String confCode, String confTitle) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_conference_created, null);

        TextView tvCode = dialogView.findViewById(R.id.tvDialogConferenceCode);
        tvCode.setText(confCode);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialogView.findViewById(R.id.btnCopyCode).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("会议号", confCode));
            Toast.makeText(requireContext(), "会议号已复制", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnEnterConference).setOnClickListener(v -> {
            dialog.dismiss();
            ConferenceActivity.start(requireContext(), confId, confCode, confTitle);
        });

        dialog.show();
    }

    private void joinConference(String conferenceCode) {
        executor.execute(() -> {
            try {
                String response = ApiClient.joinConference(conferenceCode);
                if (response == null) {
                    showError("加入会议失败：网络错误");
                    return;
                }

                JSONObject root = new JSONObject(response);
                int code = root.optInt("code", -1);
                if (code == 200 && root.has("data")) {
                    JSONObject data = root.getJSONObject("data");
                    long confId = data.optLong("id", -1);
                    String confTitle = data.optString("title", "会议");

                    Log.i(TAG, "Joined conference: id=" + confId);

                    requireActivity().runOnUiThread(() -> {
                        ConferenceActivity.start(requireContext(), confId, conferenceCode, confTitle);
                        loadConferenceHistory();
                    });
                } else {
                    showError("加入会议失败: " + root.optString("message", "未知错误"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Join conference failed", e);
                showError("加入会议异常: " + e.getMessage());
            }
        });
    }

    private void loadConferenceHistory() {
        long userId = ServerConfig.getCurrentUserId();
        if (userId <= 0) return;

        executor.execute(() -> {
            try {
                String response = ApiClient.getUserConferences(userId);
                if (response == null) {
                    Log.w(TAG, "Load conference history: null response");
                    return;
                }

                JSONObject root = new JSONObject(response);
                int code = root.optInt("code", -1);
                if (code == 200 && root.has("data")) {
                    JSONArray arr = root.getJSONArray("data");
                    List<HistoryItem> items = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        items.add(new HistoryItem(
                                obj.optLong("id", -1),
                                obj.optString("title", ""),
                                obj.optString("conferenceCode", ""),
                                obj.optString("status", ""),
                                obj.optString("createdAt", "")
                        ));
                    }

                    if (getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            historyItems.clear();
                            historyItems.addAll(items);
                            historyAdapter.notifyDataSetChanged();
                            tvHistoryEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                            rvHistory.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Load conference history failed", e);
            }
        });
    }

    private void showError(String message) {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            );
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
