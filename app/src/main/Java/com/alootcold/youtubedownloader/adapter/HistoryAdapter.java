package com.alootcold.youtubedownloader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alootcold.youtubedownloader.R;
import com.alootcold.youtubedownloader.model.DownloadItem;
import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<DownloadItem> historyItems = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        DownloadItem item = historyItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return historyItems.size();
    }

    public void updateHistoryItems(List<DownloadItem> items) {
        historyItems.clear();
        historyItems.addAll(items);
        notifyDataSetChanged();
    }

    class HistoryViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnailImageView;
        private final TextView titleTextView;
        private final TextView statusTextView;
        private final TextView dateTextView;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImageView = itemView.findViewById(R.id.thumbnailImageView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
        }

        void bind(DownloadItem item) {
            titleTextView.setText(item.getTitle());
            
            if (item.getThumbnailUrl() != null && !item.getThumbnailUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(item.getThumbnailUrl())
                        .placeholder(R.color.light_gray)
                        .into(thumbnailImageView);
            }

            statusTextView.setText(R.string.download_complete);
            
            if (item.getDownloadDate() > 0) {
                dateTextView.setText(dateFormat.format(new Date(item.getDownloadDate())));
            } else {
                dateTextView.setText("");
            }
        }
    }
} 