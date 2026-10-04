package at.fhj.togethergo.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import at.fhj.togethergo.R;
import at.fhj.togethergo.data.entities.MoodEntryEntity;
import at.fhj.togethergo.databinding.ItemMoodBinding;
import at.fhj.togethergo.databinding.ItemMoodHeaderBinding;

/**
 * RecyclerView adapter for the mood journal. Shows date headers (today, yesterday or a
 * date) and mood entries in a single list.
 */
public class MoodAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ENTRY = 1;

    /** Callback for entry interactions (long press to edit or delete). */
    public interface OnMoodActionListener {
        /** Called when an entry is long-pressed. */
        void onMoodLongClick(MoodEntryEntity mood);
    }

    /** A list row: either a date header or a mood entry. */
    private static class Row {
        final int type;
        final String header;
        final MoodEntryEntity mood;

        Row(String header) {
            this.type = TYPE_HEADER;
            this.header = header;
            this.mood = null;
        }

        Row(MoodEntryEntity mood) {
            this.type = TYPE_ENTRY;
            this.header = null;
            this.mood = mood;
        }
    }

    private List<Row> rows = new ArrayList<>();
    private final Context context;
    private final OnMoodActionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());

    public MoodAdapter(Context context, OnMoodActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    /**
     * Sets the entries and rebuilds the rows with date headers.
     * @param moods mood entries, newest first
     */
    public void setMoods(List<MoodEntryEntity> moods) {
        rows = buildRows(moods);
        notifyDataSetChanged();
    }

    /** Groups the entries under today / yesterday / date headers. */
    private List<Row> buildRows(List<MoodEntryEntity> moods) {
        List<Row> result = new ArrayList<>();
        String currentLabel = null;
        for (MoodEntryEntity mood : moods) {
            String label = dayLabel(mood.timestamp);
            if (!label.equals(currentLabel)) {
                currentLabel = label;
                result.add(new Row(label));
            }
            result.add(new Row(mood));
        }
        return result;
    }

    /** Returns the header label for a timestamp: today, yesterday or a formatted date. */
    private String dayLabel(long timestamp) {
        Calendar entry = Calendar.getInstance();
        entry.setTimeInMillis(timestamp);

        Calendar today = Calendar.getInstance();
        if (isSameDay(entry, today)) {
            return context.getString(R.string.header_today);
        }
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(entry, today)) {
            return context.getString(R.string.header_yesterday);
        }
        return dayFormat.format(new Date(timestamp));
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(ItemMoodHeaderBinding.inflate(inflater, parent, false));
        }
        return new MoodViewHolder(ItemMoodBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.type == TYPE_HEADER) {
            ((HeaderViewHolder) holder).binding.tvHeader.setText(row.header);
            return;
        }

        MoodEntryEntity mood = row.mood;
        MoodViewHolder h = (MoodViewHolder) holder;
        h.binding.tvItemEmoji.setText(getEmoji(mood.moodLevel));
        h.binding.tvItemMoodLabel.setText(getMoodLabel(mood.moodLevel));
        h.binding.tvItemTimestamp.setText(timeFormat.format(new Date(mood.timestamp)));

        // hide the note line entirely when the entry has no comment
        if (TextUtils.isEmpty(mood.note)) {
            h.binding.tvItemNote.setVisibility(View.GONE);
        } else {
            h.binding.tvItemNote.setVisibility(View.VISIBLE);
            h.binding.tvItemNote.setText(mood.note);
        }

        h.binding.getRoot().setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onMoodLongClick(mood);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    /** Returns the emoji for a mood level (1-5). */
    private String getEmoji(int level) {
        switch (level) {
            case 1: return "😭";
            case 2: return "😔";
            case 3: return "😐";
            case 4: return "🙂";
            case 5: return "🤩";
            default: return "😐";
        }
    }

    /** Returns the localized label for a mood level (1-5). */
    private String getMoodLabel(int level) {
        switch (level) {
            case 1: return context.getString(R.string.mood_terrible);
            case 2: return context.getString(R.string.mood_bad);
            case 4: return context.getString(R.string.mood_good);
            case 5: return context.getString(R.string.mood_great);
            default: return context.getString(R.string.mood_okay);
        }
    }

    static class MoodViewHolder extends RecyclerView.ViewHolder {
        final ItemMoodBinding binding;

        MoodViewHolder(@NonNull ItemMoodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final ItemMoodHeaderBinding binding;

        HeaderViewHolder(@NonNull ItemMoodHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
