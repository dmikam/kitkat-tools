package com.dmikam.kitkatebooklauncher;

public class BookItem {
    private final long id;
    private final String title;
    private final String author;
    private final String progress;
    private final String location;
    private final String type;
    private final String md5;
    private final long lastAccess;

    public BookItem(long id, String title, String author, String progress, String location, String type, String md5, long lastAccess) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.progress = progress;
        this.location = location;
        this.type = type;
        this.md5 = md5;
        this.lastAccess = lastAccess;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getProgress() {
        return progress;
    }

    public String getLocation() {
        return location;
    }

    public String getType() {
        return type;
    }

    public String getMd5() {
        return md5;
    }

    public long getLastAccess() {
        return lastAccess;
    }
}

