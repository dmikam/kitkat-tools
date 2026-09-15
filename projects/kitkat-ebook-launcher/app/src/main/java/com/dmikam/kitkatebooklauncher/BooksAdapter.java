package com.dmikam.kitkatebooklauncher;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class BooksAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<BookItem> books = new ArrayList<>();

    public BooksAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setData(List<BookItem> newBooks) {
        books.clear();
        if (newBooks != null) {
            books.addAll(newBooks);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return books.size();
    }

    @Override
    public BookItem getItem(int position) {
        return books.get(position);
    }

    @Override
    public long getItemId(int position) {
        return books.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_book, parent, false);
            holder = new ViewHolder();
            holder.tvTitle = convertView.findViewById(R.id.tv_book_title);
            holder.tvAuthor = convertView.findViewById(R.id.tv_book_author);
            holder.tvProgress = convertView.findViewById(R.id.tv_book_progress);
            holder.tvType = convertView.findViewById(R.id.tv_book_type);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        BookItem item = getItem(position);

        String title = item.getTitle();
        if (TextUtils.isEmpty(title)) {
            title = convertView.getContext().getString(R.string.unknown_title);
        }
        holder.tvTitle.setText(title);

        String author = item.getAuthor();
        if (TextUtils.isEmpty(author)) {
            author = convertView.getContext().getString(R.string.unknown_author);
        }
        holder.tvAuthor.setText(author);

        String progress = item.getProgress();
        if (!TextUtils.isEmpty(progress)) {
            holder.tvProgress.setText(progress);
            holder.tvProgress.setVisibility(View.VISIBLE);
        } else {
            holder.tvProgress.setVisibility(View.GONE);
        }

        String type = item.getType();
        if (!TextUtils.isEmpty(type)) {
            holder.tvType.setText(type.toUpperCase());
            holder.tvType.setVisibility(View.VISIBLE);
        } else {
            holder.tvType.setVisibility(View.GONE);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView tvTitle;
        TextView tvAuthor;
        TextView tvProgress;
        TextView tvType;
    }
}

