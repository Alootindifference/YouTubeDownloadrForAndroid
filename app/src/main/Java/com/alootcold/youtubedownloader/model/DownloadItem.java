package com.alootcold.youtubedownloader.model;

import java.io.Serializable;

public class DownloadItem implements Serializable {
    private String id;
    private String url;
    private String title;
    private String thumbnailUrl;
    private int progress;
    private String eta;
    private boolean completed;
    private boolean paused;
    private long downloadDate;
    private String format;
    private String status;

    public DownloadItem(String id, String url, String title, String thumbnailUrl) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.progress = 0;
        this.eta = "";
        this.completed = false;
        this.paused = false;
        this.downloadDate = 0;
        this.format = "best";
        this.status = "";
    }

    public DownloadItem(String url, String format, String title, String status, boolean isDownload) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.url = url;
        this.format = format;
        this.title = title;
        this.thumbnailUrl = "";
        this.progress = 0;
        this.eta = "";
        this.completed = false;
        this.paused = false;
        this.downloadDate = 0;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    /**
     * 设置下载进度
     * @param progress 下载进度，0-100
     */
    public void setProgress(int progress) {
        // 确保进度值在0-100之间
        this.progress = Math.min(100, Math.max(0, progress));
    }

    /**
     * 获取下载进度
     * @return 下载进度，0-100
     */
    public int getProgress() {
        // 确保返回的进度值在0-100之间
        return Math.min(100, Math.max(0, progress));
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public long getDownloadDate() {
        return downloadDate;
    }

    public void setDownloadDate(long downloadDate) {
        this.downloadDate = downloadDate;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownloadItem that = (DownloadItem) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
} 