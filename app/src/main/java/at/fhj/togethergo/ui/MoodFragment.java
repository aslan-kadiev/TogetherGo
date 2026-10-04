package at.fhj.togethergo.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import at.fhj.togethergo.MoodViewModel;
import at.fhj.togethergo.R;
import at.fhj.togethergo.data.entities.MoodEntryEntity;
import at.fhj.togethergo.databinding.FragmentMoodBinding;

/**
 * Mood journal.
 * <p>
 * The user picks their current mood, adds an optional note and sees the history, a short reflection
 * and a weekly chart. Existing entries can be edited or deleted.
 */
public class MoodFragment extends Fragment {

    private FragmentMoodBinding binding;
    private MoodViewModel viewModel;
    private MoodAdapter adapter;
    private View[] selectorContainers;
    private int selectedMoodLevel = 3; // Default to neutral (Okay)
    private String editingId = null;   // non-null while editing an existing entry

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMoodBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MoodViewModel.class);

        setupMoodSelectors();
        setupRecyclerView();

        binding.btnSaveMood.setOnClickListener(v -> saveMood());
    }

    /** Wires the five mood selectors to the selection logic. */
    private void setupMoodSelectors() {
        selectorContainers = new View[]{
                binding.containerTerrible,
                binding.containerBad,
                binding.containerOkay,
                binding.containerGood,
                binding.containerGreat
        };

        for (int i = 0; i < selectorContainers.length; i++) {
            final int level = i + 1;
            selectorContainers[i].setOnClickListener(v -> {
                selectedMoodLevel = level;
                updateSelectorUI();
            });
        }

        updateSelectorUI();
    }

    /** Highlights the selected mood and dims the others with a short animation. */
    private void updateSelectorUI() {
        for (int i = 0; i < selectorContainers.length; i++) {
            boolean isSelected = (i + 1 == selectedMoodLevel);

            float targetScale = isSelected ? 1.15f : 0.95f;
            float targetAlpha = isSelected ? 1.0f : 0.35f;

            selectorContainers[i].animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .alpha(targetAlpha)
                    .setDuration(200)
                    .start();
        }
    }

    /**
     * Sets up the history list and observes the mood history (empty state, reflection,
     * weekly chart).
     */
    private void setupRecyclerView() {
        adapter = new MoodAdapter(requireContext(), this::showEntryOptions);
        binding.recyclerMoods.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerMoods.setAdapter(adapter);

        binding.loadingBox.getRoot().setVisibility(View.VISIBLE);
        viewModel.getMoodHistory().observe(getViewLifecycleOwner(), moods -> {
            binding.loadingBox.getRoot().setVisibility(View.GONE);
            if (moods != null) {
                adapter.setMoods(moods);
                updateEmptyState(moods);
                updateReflection(moods);
                updateWeeklyChart(moods);
            }
        });
    }

    /** Shows either the history list or the empty state. */
    private void updateEmptyState(List<MoodEntryEntity> moods) {
        boolean empty = moods.isEmpty();
        binding.tvEmptyMoods.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerMoods.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** Computes a short reflection from the average of the last (up to seven) check-ins. */
    private void updateReflection(List<MoodEntryEntity> moods) {
        if (moods.isEmpty()) {
            binding.cardReflection.setVisibility(View.GONE);
            return;
        }

        int sample = Math.min(moods.size(), 7);
        int sum = 0;
        for (int i = 0; i < sample; i++) {
            sum += moods.get(i).moodLevel;
        }
        int average = Math.round(sum / (float) sample);

        binding.cardReflection.setVisibility(View.VISIBLE);
        binding.tvReflectionEmoji.setText(reflectionEmoji(average));
        binding.tvReflectionText.setText(reflectionText(average, sample));
    }

    /** Draws the average mood of the last seven days as a bar chart, with today on the right. */
    private void updateWeeklyChart(List<MoodEntryEntity> moods) {
        if (moods.isEmpty()) {
            binding.cardWeeklyMood.setVisibility(View.GONE);
            return;
        }
        binding.cardWeeklyMood.setVisibility(View.VISIBLE);

        View[] bars = {binding.moodBar1, binding.moodBar2, binding.moodBar3, binding.moodBar4,
                binding.moodBar5, binding.moodBar6, binding.moodBar7};
        android.widget.TextView[] labels = {binding.moodDay1, binding.moodDay2, binding.moodDay3,
                binding.moodDay4, binding.moodDay5, binding.moodDay6, binding.moodDay7};
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());

        int maxBarPx = dpToPx(90);

        for (int i = 0; i < 7; i++) {
            // bar i maps to (6 - i) days ago, so today sits on the right
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_YEAR, -(6 - i));

            int sum = 0;
            int count = 0;
            for (MoodEntryEntity mood : moods) {
                Calendar entry = Calendar.getInstance();
                entry.setTimeInMillis(mood.timestamp);
                if (entry.get(Calendar.YEAR) == day.get(Calendar.YEAR)
                        && entry.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)) {
                    sum += mood.moodLevel;
                    count++;
                }
            }

            ViewGroup.LayoutParams params = bars[i].getLayoutParams();
            if (count > 0) {
                float avg = sum / (float) count;
                params.height = Math.max((int) ((avg / 5f) * maxBarPx), dpToPx(6));
                bars[i].setBackgroundTintList(ColorStateList.valueOf(moodColor(Math.round(avg))));
            } else {
                params.height = dpToPx(4);
                bars[i].setBackgroundTintList(ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bar_empty)));
            }
            bars[i].setLayoutParams(params);
            labels[i].setText(dayFormat.format(day.getTime()));
        }
    }

    /** Returns the colour for a mood level, from red (bad) to green (very good). */
    private int moodColor(int level) {
        switch (level) {
            case 1: return Color.parseColor("#EF4444");
            case 2: return Color.parseColor("#F97316");
            case 3: return Color.parseColor("#F59E0B");
            case 4: return Color.parseColor("#22C55E");
            case 5: return Color.parseColor("#10B981");
            default: return Color.parseColor("#F59E0B");
        }
    }

    /** Shows an edit/delete menu for an entry. */
    private void showEntryOptions(MoodEntryEntity mood) {
        new AlertDialog.Builder(requireContext())
                .setTitle(new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(new Date(mood.timestamp)))
                .setItems(new CharSequence[]{getString(R.string.action_edit), getString(R.string.action_delete)}, (dialog, which) -> {
                    if (which == 0) {
                        startEditing(mood);
                    } else {
                        confirmDelete(mood);
                    }
                })
                .show();
    }

    /** Loads an existing entry into the form for editing. */
    private void startEditing(MoodEntryEntity mood) {
        editingId = mood.firestoreId;
        selectedMoodLevel = mood.moodLevel;
        binding.etNote.setText(mood.note);
        binding.etNote.requestFocus();
        binding.btnSaveMood.setText(R.string.update_checkin);
        updateSelectorUI();
    }

    /** Asks for confirmation before deleting an entry. */
    private void confirmDelete(MoodEntryEntity mood) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_checkin_title)
                .setMessage(R.string.delete_checkin_msg)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    viewModel.deleteMood(mood.firestoreId);
                    if (mood.firestoreId != null && mood.firestoreId.equals(editingId)) {
                        resetForm();
                    }
                    Toast.makeText(requireContext(), R.string.checkin_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Returns the emoji for the average mood shown in the reflection. */
    private String reflectionEmoji(int level) {
        switch (level) {
            case 1: return "😭";
            case 2: return "😔";
            case 3: return "😐";
            case 4: return "🙂";
            case 5: return "🤩";
            default: return "😐";
        }
    }

    /**
     * Builds the reflection text from the trend (low/medium/high) and the number of
     * check-ins.
     */
    private String reflectionText(int level, int sample) {
        String trend;
        switch (level) {
            case 1:
            case 2:
                trend = getString(R.string.reflection_low);
                break;
            case 3:
                trend = getString(R.string.reflection_mid);
                break;
            default:
                trend = getString(R.string.reflection_high);
                break;
        }
        return getResources().getQuantityString(R.plurals.reflection_summary, sample, trend, sample);
    }

    /** Saves the form: updates the edited entry or creates a new one, then resets the form. */
    private void saveMood() {
        String note = binding.etNote.getText().toString().trim();

        if (editingId != null) {
            viewModel.updateMood(editingId, selectedMoodLevel, note);
            Toast.makeText(requireContext(), R.string.checkin_updated, Toast.LENGTH_SHORT).show();
        } else {
            viewModel.addMood(selectedMoodLevel, note);
            Toast.makeText(requireContext(), R.string.checkin_saved, Toast.LENGTH_SHORT).show();
        }

        resetForm();
    }

    private void resetForm() {
        editingId = null;
        binding.etNote.setText("");
        binding.btnSaveMood.setText(R.string.save_checkin);
        selectedMoodLevel = 3;
        updateSelectorUI();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
