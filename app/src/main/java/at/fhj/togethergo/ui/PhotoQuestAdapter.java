package at.fhj.togethergo.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import at.fhj.togethergo.R;
import at.fhj.togethergo.data.models.PhotoPrompt;
import at.fhj.togethergo.databinding.ItemPhotoQuestBinding;

/**
 * RecyclerView adapter for the extra photo quests. Prompts already completed today are
 * dimmed.
 */
public class PhotoQuestAdapter extends RecyclerView.Adapter<PhotoQuestAdapter.QuestViewHolder> {

    /** Callback for taps on a photo quest. */
    public interface OnQuestClickListener {
        /** Called when an open photo quest is tapped. */
        void onQuestClick(PhotoPrompt prompt);
    }

    private List<PhotoPrompt> prompts = new ArrayList<>();
    private Set<String> completedIds = new HashSet<>();
    private final OnQuestClickListener listener;

    public PhotoQuestAdapter(OnQuestClickListener listener) {
        this.listener = listener;
    }

    /** Sets the prompts to show and the ids of those already completed today. */
    public void submit(List<PhotoPrompt> prompts, Set<String> completedIds) {
        this.prompts = prompts != null ? prompts : new ArrayList<>();
        this.completedIds = completedIds != null ? completedIds : new HashSet<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QuestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPhotoQuestBinding binding = ItemPhotoQuestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new QuestViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestViewHolder holder, int position) {
        PhotoPrompt prompt = prompts.get(position);
        Context context = holder.binding.getRoot().getContext();

        holder.binding.tvQuestEmoji.setText(prompt.emoji);
        holder.binding.tvQuestTitle.setText(prompt.titleRes);
        holder.binding.tvQuestPoints.setText(context.getString(R.string.quest_points, prompt.points));

        boolean done = completedIds.contains(prompt.id);
        holder.binding.tvQuestState.setText(done ? "✓" : "📷");
        holder.binding.getRoot().setAlpha(done ? 0.5f : 1f);
        holder.binding.getRoot().setClickable(!done);
        holder.binding.getRoot().setOnClickListener(done ? null : v -> {
            if (listener != null) {
                listener.onQuestClick(prompt);
            }
        });
    }

    @Override
    public int getItemCount() {
        return prompts.size();
    }

    static class QuestViewHolder extends RecyclerView.ViewHolder {
        final ItemPhotoQuestBinding binding;

        QuestViewHolder(@NonNull ItemPhotoQuestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
