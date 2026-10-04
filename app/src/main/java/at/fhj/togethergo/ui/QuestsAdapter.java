package at.fhj.togethergo.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import at.fhj.togethergo.R;
import at.fhj.togethergo.data.models.Quest;
import at.fhj.togethergo.databinding.ItemQuestBinding;

/**
 * RecyclerView adapter for the quest list on the home screen. Shows title, description
 * and points, and reports taps on the card and on the complete button.
 */
public class QuestsAdapter extends RecyclerView.Adapter<QuestsAdapter.QuestViewHolder> {

    private List<Quest> quests = new ArrayList<>();
    private final OnQuestClickListener listener;

    /** Callback for quest interactions. */
    public interface OnQuestClickListener {
        /** Called when the complete button is pressed. */
        void onQuestComplete(Quest quest);

        /** Called when the quest card is tapped. */
        void onQuestClick(Quest quest);
    }

    public QuestsAdapter(OnQuestClickListener listener) {
        this.listener = listener;
    }

    /** Replaces the displayed quests. */
    public void setQuests(List<Quest> quests) {
        this.quests = quests;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QuestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQuestBinding binding = ItemQuestBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new QuestViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestViewHolder holder, int position) {
        Quest quest = quests.get(position);
        holder.binding.tvQuestTitle.setText(quest.title);
        holder.binding.tvQuestDescription.setText(quest.description);
        holder.binding.tvQuestPoints.setText(
                holder.binding.getRoot().getContext().getString(R.string.quest_points, quest.points));

        holder.binding.btnCompleteQuest.setEnabled(!quest.isCompleted);
        holder.binding.btnCompleteQuest.setText(quest.isCompleted ? R.string.action_completed : R.string.action_complete);

        holder.binding.btnCompleteQuest.setOnClickListener(v -> {
            if (!quest.isCompleted) {
                listener.onQuestComplete(quest);
            }
        });

        // tap on the card (not the button) opens Activity
        holder.binding.getRoot().setOnClickListener(v -> listener.onQuestClick(quest));
    }

    @Override
    public int getItemCount() {
        return quests.size();
    }

    static class QuestViewHolder extends RecyclerView.ViewHolder {
        final ItemQuestBinding binding;

        QuestViewHolder(@NonNull ItemQuestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
