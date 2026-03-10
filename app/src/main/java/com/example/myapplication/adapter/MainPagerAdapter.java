package com.example.myapplication.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.myapplication.fragment.ContactsFragment;
import com.example.myapplication.fragment.ConversationFragment;
import com.example.myapplication.fragment.ProfileFragment;

/**
 * 主界面 ViewPager2 适配器
 *
 * 管理三个主页面 Fragment：
 * - 0: ConversationFragment（消息/会话列表）
 * - 1: ContactsFragment（联系人）
 * - 2: ProfileFragment（我的）
 */
public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new ConversationFragment();
            case 1:
                return new ContactsFragment();
            case 2:
                return new ProfileFragment();
            default:
                throw new IllegalArgumentException("Invalid position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
