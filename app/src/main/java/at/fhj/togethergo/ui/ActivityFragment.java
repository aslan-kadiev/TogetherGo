package at.fhj.togethergo.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import java.util.List;
import java.util.Locale;

import at.fhj.togethergo.R;
import at.fhj.togethergo.data.models.DailyActivity;
import at.fhj.togethergo.databinding.FragmentActivityBinding;

/**
 * Shows today's activity (steps, distance, active minutes, calories) and a bar chart of the
 * last seven days.
 */
public class ActivityFragment extends Fragment {
    private FragmentActivityBinding binding;
    private ActivityViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentActivityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ActivityViewModel.class);

        observeData();

        // Activity is opened from Home (not a bottom tab), so give it a clear way back
        binding.btnBack.setOnClickListener(v -> {
            NavController nav = NavHostFragment.findNavController(this);
            if (!nav.popBackStack()) {
                nav.navigate(R.id.navigation_home);
            }
        });
    }

    private void observeData() {
        binding.loadingBox.getRoot().setVisibility(View.VISIBLE);
        viewModel.getTodayActivity().observe(getViewLifecycleOwner(), this::updateTodayUI);
        viewModel.getWeeklyActivity().observe(getViewLifecycleOwner(), this::updateWeeklyUI);
    }

    /** Updates today's metrics, progress bar and status badge. */
    private void updateTodayUI(DailyActivity activity) {
        binding.loadingBox.getRoot().setVisibility(View.GONE);
        if (activity == null) return;

        binding.tvStepsCount.setText(String.valueOf(activity.steps));
        binding.tvStepsGoal.setText(String.format(Locale.ENGLISH, getString(R.string.of_goal), activity.stepGoal));
        binding.tvKm.setText(String.format(Locale.ENGLISH, "%.1f", activity.km));
        binding.tvMin.setText(String.valueOf(activity.activeMinutes));
        binding.tvCal.setText(String.valueOf(activity.calories));

        int progress = (activity.stepGoal > 0) ? (int) ((activity.steps / (float) activity.stepGoal) * 100) : 0;
        binding.progressSteps.setProgress(Math.min(progress, 100));

        // different badge text depending on how far they are
        String badgeText;
        if (activity.steps <= 0) {
            badgeText = getString(R.string.badge_get_moving);
        } else if (progress >= 100) {
            badgeText = getString(R.string.badge_goal_reached);
        } else {
            badgeText = String.format(Locale.ENGLISH, getString(R.string.badge_to_goal), progress);
        }
        binding.tvGoalBadge.setText(badgeText);
    }

    /**
     * Updates the weekly total and the heights of the seven chart bars.
     * @param activities daily activity of the last days, newest first
     */
    private void updateWeeklyUI(List<DailyActivity> activities) {
        if (activities == null || activities.isEmpty()) return;

        long totalSteps = 0;
        for (DailyActivity a : activities) {
            totalSteps += a.steps;
        }
        binding.tvWeeklyTotal.setText(String.format(Locale.ENGLISH, getString(R.string.weekly_total_steps), totalSteps));

        View[] bars = {binding.bar1, binding.bar2, binding.bar3, binding.bar4, binding.bar5, binding.bar6, binding.bar7};
        int maxSteps = 0;
        for (DailyActivity a : activities) {
            if (a.steps > maxSteps) maxSteps = a.steps;
        }

        if (maxSteps == 0) maxSteps = 10000;

        for (int i = 0; i < bars.length; i++) {
            ViewGroup.LayoutParams params = bars[i].getLayoutParams();
            if (i < activities.size()) {
                DailyActivity a = activities.get(activities.size() - 1 - i); // Reversed to show chronological order (left to right)
                int heightPx = (int) ((a.steps / (float) maxSteps) * dpToPx(100));
                params.height = Math.max(heightPx, dpToPx(4)); // Minimum height
            } else {
                // no data for this day yet, keep the bar flat instead of a fake default
                params.height = dpToPx(4);
            }
            bars[i].setLayoutParams(params);
        }
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
