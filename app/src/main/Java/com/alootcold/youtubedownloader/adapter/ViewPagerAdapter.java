package com.alootcold.youtubedownloader.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.alootcold.youtubedownloader.fragment.DownloadingFragment;
import com.alootcold.youtubedownloader.fragment.HistoryFragment;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new DownloadingFragment();
        } else {
            return new HistoryFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2; // 下载列表和历史记录
    }
} 