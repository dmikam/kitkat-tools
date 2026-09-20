package com.dmikam.kitkatebooklauncher;

public class BookItem {
    private final long id;
    private final String title;
    private final String author;
    private final String progress;
    private final int currentPage;
    private final int totalPages;
    private final String location;
    private final String type;
    private final String md5;
    private final long lastAccess;
    private final String tags;
    private final String series;
    private boolean favorite;
    private int rating;

    public BookItem(long id, String title, String author, String progress,
                    int currentPage, int totalPages,
                    String location, String type, String md5,
                    long lastAccess, String tags, String series, int rating, boolean favorite) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.progress = progress;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.location = location;
        this.type = type;
        this.md5 = md5;
        this.lastAccess = lastAccess;
        this.tags = tags;
        this.series = series;
        this.favorite = favorite;
        this.rating = rating;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getProgress() { return progress; }
    public int getCurrentPage() { return currentPage; }
    public int getTotalPages() { return totalPages; }
    public String getLocation() { return location; }
    public String getType() { return type; }
    public String getMd5() { return md5; }
    public long getLastAccess() { return lastAccess; }
    public String getTags() { return tags; }
    public String getSeries() { return series; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    /** Returns progress as 0-100 integer, or -1 if unavailable. */
    public int getProgressPercent() {
        if (totalPages > 0) {
            return Math.min(100, (int) ((currentPage * 100L) / totalPages));
        }
        return -1;
    }
}
