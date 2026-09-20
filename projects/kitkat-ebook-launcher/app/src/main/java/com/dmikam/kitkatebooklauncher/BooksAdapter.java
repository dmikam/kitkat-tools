package com.dmikam.kitkatebooklauncher;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.widget.ImageView;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class BooksAdapter extends BaseAdapter {

    public interface FavoriteToggleListener {
        void onFavoriteToggle(BookItem item, int position);
    }

    private final LayoutInflater inflater;
    private final List<BookItem> books = new ArrayList<>();
    private FavoriteToggleListener favoriteListener;
    private final SharedPreferences prefs;

    public BooksAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setFavoriteToggleListener(FavoriteToggleListener listener) {
        this.favoriteListener = listener;
    }

    public void setData(List<BookItem> newBooks) {
        books.clear();
        if (newBooks != null) {
            books.addAll(newBooks);
        }
        notifyDataSetChanged();
    }

    /** Update favorite state of a single item without full reload. */
    public void updateFavorite(int position, boolean favorite) {
        if (position >= 0 && position < books.size()) {
            books.get(position).setFavorite(favorite);
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() { return books.size(); }

    @Override
    public BookItem getItem(int position) { return books.get(position); }

    @Override
    public long getItemId(int position) { return books.get(position).getId(); }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_book, parent, false);
            holder = new ViewHolder();
            holder.ivCover = convertView.findViewById(R.id.iv_list_cover);
            holder.tvTitle = convertView.findViewById(R.id.tv_book_title);
            holder.tvAuthor = convertView.findViewById(R.id.tv_book_author);
            holder.tvRating = convertView.findViewById(R.id.tv_book_rating);
            holder.tvProgress = convertView.findViewById(R.id.tv_book_progress);
            holder.pbProgress = convertView.findViewById(R.id.pb_progress);
            holder.tvFavorite = convertView.findViewById(R.id.tv_favorite);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final BookItem item = getItem(position);
        final int pos = position;

        // Title
        String title = item.getTitle();
        holder.tvTitle.setText(TextUtils.isEmpty(title) ?
                convertView.getContext().getString(R.string.unknown_title) : title);

        // Author
        String author = item.getAuthor();
        holder.tvAuthor.setText(TextUtils.isEmpty(author) ?
                convertView.getContext().getString(R.string.unknown_author) : author);

        // Rating stars (small) — show 1..5 as filled/empty stars next to author
        int rating = item.getRating();
        if (rating <= 0) {
            holder.tvRating.setVisibility(View.GONE);
        } else {
            holder.tvRating.setVisibility(View.VISIBLE);
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                if (i < rating) stars.append('\u2605'); else stars.append('\u2606');
            }
            holder.tvRating.setText(stars.toString());
        }

        // Progress bar + text
        int pct = item.getProgressPercent();
        if (pct >= 0) {
            holder.pbProgress.setVisibility(View.VISIBLE);
            holder.pbProgress.setProgress(pct);
            String prog = item.getProgress();
            holder.tvProgress.setText(TextUtils.isEmpty(prog) ? pct + "%" : prog);
            holder.tvProgress.setVisibility(View.VISIBLE);
        } else {
            holder.pbProgress.setVisibility(View.INVISIBLE);
            holder.tvProgress.setVisibility(View.INVISIBLE);
        }

        // Heart icon (Unicode: filled ♥ U+2665, empty ♡ U+2661)
        holder.tvFavorite.setText(item.isFavorite() ? "\u2665" : "\u2661");

        // Heart click — does NOT propagate to list row click
        holder.tvFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (favoriteListener != null) {
                    favoriteListener.onFavoriteToggle(item, pos);
                }
            }
        });

        // Covers in list: controlled by preference
        boolean showCovers = prefs.getBoolean("pref_show_covers", true);
        if (!showCovers) {
            if (holder.ivCover != null) holder.ivCover.setVisibility(View.GONE);
        } else {
            if (holder.ivCover != null) {
                // Try cache file
                String key = item.getMd5();
                if (key == null) {
                    String loc = item.getLocation();
                    key = Integer.toHexString(loc == null ? 0 : loc.hashCode());
                }
                android.content.Context ctx = inflater.getContext();
                java.io.File cache = new java.io.File(ctx.getCacheDir(), "cover_" + key + ".png");
                if (cache.exists()) {
                    // Efficient decode to thumbnail
                    Bitmap bm = decodeSampledBitmapFromFile(cache.getAbsolutePath(), 72, 96);
                    if (bm != null) {
                        holder.ivCover.setImageBitmap(bm);
                        holder.ivCover.setVisibility(View.VISIBLE);
                    } else {
                        holder.ivCover.setVisibility(View.GONE);
                    }
                } else {
                    // Show small placeholder
                    Bitmap ph = createPlaceholderSmall(item.getTitle());
                    holder.ivCover.setImageBitmap(ph);
                    holder.ivCover.setVisibility(View.VISIBLE);
                }
            }
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView ivCover;
        TextView tvTitle;
        TextView tvAuthor;
        TextView tvRating;
        TextView tvProgress;
        ProgressBar pbProgress;
        TextView tvFavorite;
    }

    private static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, options);
        } catch (Exception e) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private static Bitmap createPlaceholderSmall(String title) {
        int width = 72;
        int height = 96;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int[] palette = new int[] { Color.parseColor("#3F51B5"), Color.parseColor("#009688"), Color.parseColor("#673AB7") };
        int color = palette[Math.abs((title == null ? 0 : title.hashCode())) % palette.length];
        Paint bg = new Paint(); bg.setColor(color);
        canvas.drawRect(0,0,width,height,bg);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE); textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setTextSize(20f);
        String initials = "B";
        if (!TextUtils.isEmpty(title)) {
            String[] parts = title.trim().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) { if (p.length()>0) { sb.append(p.substring(0,1).toUpperCase()); if (sb.length()>=2) break; } }
            if (sb.length()>0) initials = sb.toString();
        }
        canvas.drawText(initials.length()>2?initials.substring(0,2):initials, width/2f, height/2f+6f, textPaint);
        return bmp;
    }
}
