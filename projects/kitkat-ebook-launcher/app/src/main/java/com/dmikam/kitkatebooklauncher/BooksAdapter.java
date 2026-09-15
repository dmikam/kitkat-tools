package com.dmikam.kitkatebooklauncher;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    public BooksAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
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
            holder.tvTitle = convertView.findViewById(R.id.tv_book_title);
            holder.tvAuthor = convertView.findViewById(R.id.tv_book_author);
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

        return convertView;
    }

    private static class ViewHolder {
        TextView tvTitle;
        TextView tvAuthor;
        TextView tvProgress;
        ProgressBar pbProgress;
        TextView tvFavorite;
    }
}
