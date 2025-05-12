package com.alootcold.youtubedownloader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alootcold.youtubedownloader.R;
import com.alootcold.youtubedownloader.model.DownloadItem;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class DownloadingAdapter extends RecyclerView.Adapter<DownloadingAdapter.DownloadViewHolder> {

    private final List<DownloadItem> downloadItems = new ArrayList<>();
    private final DownloadItemListener listener;

    public interface DownloadItemListener {
        void onPauseResumeClicked(DownloadItem item);
        void onCancelClicked(DownloadItem item);
    }

    public DownloadingAdapter(DownloadItemListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download, parent, false);
        return new DownloadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
        DownloadItem item = downloadItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return downloadItems.size();
    }

    public void updateDownloadItems(List<DownloadItem> items) {
        if (hasChanged(items)) {
            downloadItems.clear();
            downloadItems.addAll(items);
            notifyDataSetChanged();
        }
    }

    public void updateDownloadProgress(String videoId, int progress, String eta) {
        boolean found = false;
        for (int i = 0; i < downloadItems.size(); i++) {
            DownloadItem item = downloadItems.get(i);
            if (item.getId().equals(videoId)) {
                if (Math.abs(item.getProgress() - progress) >= 1) {
                    item.setProgress(progress);
                    item.setEta(eta);
                    notifyItemChanged(i, new Object[]{"progress"});
                }
                found = true;
                break;
            }
        }
        
        if (!found && downloadItems.isEmpty() && listener instanceof androidx.fragment.app.Fragment) {
            try {
                androidx.fragment.app.Fragment fragment = (androidx.fragment.app.Fragment) listener;
                if (fragment.isAdded() && fragment.getActivity() != null && !fragment.getActivity().isFinishing()) {
                    fragment.requireActivity().runOnUiThread(() -> {
                        try {
                            if (fragment.isAdded() && !fragment.isDetached()) {
                                java.lang.reflect.Method method = listener.getClass().getDeclaredMethod("updateDownloadList");
                                method.setAccessible(true);
                                method.invoke(listener);
                            }
                        } catch (Exception e) {
                            // 只记录日志，不要让异常传播
                            android.util.Log.e("DownloadingAdapter", "Error calling updateDownloadList", e);
                        }
                    });
                }
            } catch (Exception e) {
                // 捕获所有可能的异常，包括IllegalStateException
                android.util.Log.e("DownloadingAdapter", "Error updating download list", e);
            }
        }
    }

    public void removeDownloadItem(String videoId) {
        for (int i = 0; i < downloadItems.size(); i++) {
            if (downloadItems.get(i).getId().equals(videoId)) {
                downloadItems.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void updateDownloadPauseState(String videoId, boolean paused) {
        for (int i = 0; i < downloadItems.size(); i++) {
            DownloadItem item = downloadItems.get(i);
            if (item.getId().equals(videoId)) {
                item.setPaused(paused);
                notifyItemChanged(i);
                break;
            }
        }
    }

    private boolean hasChanged(List<DownloadItem> newItems) {
        if (newItems.size() != downloadItems.size()) {
            return true;
        }
        
        for (int i = 0; i < downloadItems.size(); i++) {
            DownloadItem oldItem = downloadItems.get(i);
            boolean found = false;
            
            for (DownloadItem newItem : newItems) {
                if (oldItem.getId().equals(newItem.getId())) {
                    found = true;
                    if (Math.abs(oldItem.getProgress() - newItem.getProgress()) > 1 || 
                        oldItem.isPaused() != newItem.isPaused()) {
                        return true;
                    }
                }
            }
            
            if (!found) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            DownloadItem item = downloadItems.get(position);
            if (payloads.contains("progress")) {
                holder.updateProgress(item);
            }
        }
    }

    class DownloadViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnailImageView;
        private final TextView titleTextView;
        private final TextView statusTextView;
        private final TextView percentTextView;
        private final ProgressBar progressBar;
        private final Button pauseResumeButton;
        private final Button cancelButton;

        DownloadViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImageView = itemView.findViewById(R.id.thumbnailImageView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
            percentTextView = itemView.findViewById(R.id.percentTextView);
            progressBar = itemView.findViewById(R.id.progressBar);
            pauseResumeButton = itemView.findViewById(R.id.pauseResumeButton);
            cancelButton = itemView.findViewById(R.id.cancelButton);
        }

        void updateProgress(DownloadItem item) {
            int progress = item.getProgress();
            progressBar.setProgress(progress);
            percentTextView.setText(progress + "%");
            
            percentTextView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            
            if (item.isCompleted()) {
                statusTextView.setText(itemView.getContext().getString(R.string.download_complete));
                pauseResumeButton.setEnabled(false);
            } else if (item.isPaused()) {
                statusTextView.setText("已暂停");
            } else {
                String etaText = item.getEta();
                if (etaText != null && !etaText.isEmpty()) {
                    statusTextView.setText(itemView.getContext().getString(R.string.downloading) + " - 剩余时间: " + etaText);
                }
            }
        }

        void bind(DownloadItem item) {
            titleTextView.setText(item.getTitle());
            
            if (item.getThumbnailUrl() != null && !item.getThumbnailUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(item.getThumbnailUrl())
                        .placeholder(R.color.light_gray)
                        .into(thumbnailImageView);
            }

            if (item.isCompleted()) {
                statusTextView.setText(itemView.getContext().getString(R.string.download_complete));
                pauseResumeButton.setText(R.string.pause);
                pauseResumeButton.setEnabled(false);
            } else if (item.isPaused()) {
                statusTextView.setText("已暂停");
                pauseResumeButton.setText(R.string.resume);
                pauseResumeButton.setEnabled(true);
            } else {
                String etaText = item.getEta();
                if (etaText != null && !etaText.isEmpty()) {
                    statusTextView.setText(itemView.getContext().getString(R.string.downloading) + " - 剩余时间: " + etaText);
                } else {
                    statusTextView.setText(R.string.downloading);
                }
                pauseResumeButton.setText(R.string.pause);
                pauseResumeButton.setEnabled(true);
            }

            updateProgress(item);

            pauseResumeButton.setOnClickListener(v -> {
                if (listener != null && !item.isCompleted()) {
                    listener.onPauseResumeClicked(item);
                }
            });

            cancelButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCancelClicked(item);
                }
            });
        }
    }
} 