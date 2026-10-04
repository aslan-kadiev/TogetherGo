package at.fhj.togethergo.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;
import java.util.Locale;

import at.fhj.togethergo.MainViewModel;
import at.fhj.togethergo.MoodViewModel;
import at.fhj.togethergo.R;
import at.fhj.togethergo.services.StepCounter;
import at.fhj.togethergo.data.PhotoPrompts;
import at.fhj.togethergo.data.QuestsRepository;
import at.fhj.togethergo.data.models.PhotoPrompt;
import at.fhj.togethergo.data.models.ProfileStats;
import at.fhj.togethergo.data.models.Quest;
import at.fhj.togethergo.databinding.FragmentHomeBinding;

/**
 * Home screen: greeting, level progress, the daily photo prompt, available quests and a
 * highlight card.
 * <p>
 * The highlight card deliberately favours behavioural activation (going out, capturing a moment,
 * checking in) over fitness metrics. The fragment binds to {@link StepCounter} to keep the step and
 * distance display up to date.
 */
public class HomeFragment extends Fragment {

    private static final int stepThreshold = 500;

    private FragmentHomeBinding binding;
    private MainViewModel viewModel;
    private HomeViewModel homeViewModel;
    private ProfileViewModel profileViewModel;
    private MomentsViewModel momentsViewModel;
    private MoodViewModel moodViewModel;
    private QuestsAdapter questsAdapter;
    private StepCounter stepCounterService;
    private boolean isBound = false;

    // latest values feeding the behavioural-activation highlight card
    private ProfileStats latestStats;
    private int momentCount = 0;
    private int moodCount = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Refreshes the step display every three seconds while the screen is visible. */
    private final Runnable updateStepsRunnable = new Runnable() {
        @Override
        public void run() {
            updateStepDisplay();
            handler.postDelayed(this, 3000);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StepCounter.LocalBinder binder = (StepCounter.LocalBinder) service;
            stepCounterService = binder.getService();
            isBound = true;
            updateStepDisplay();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            stepCounterService = null;
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        // Moments + Mood drive the highlight card, so the most prominent achievement
        // celebrates showing up and noticing the world, not step counts
        momentsViewModel = new ViewModelProvider(this).get(MomentsViewModel.class);
        moodViewModel = new ViewModelProvider(requireActivity()).get(MoodViewModel.class);

        binding.tvSubtitle.setText(greeting());

        bindTodaysMoment();
        setupQuestsRecyclerView();
        observeViewModel();
        setupNavigation();
    }

    /**
     * Shows the daily photo prompt (the same as on the Moments tab) as a gentle nudge to
     * go outside.
     */
    private void bindTodaysMoment() {
        PhotoPrompt prompt = PhotoPrompts.today();
        binding.tvMomentPromptEmoji.setText(prompt.emoji);
        binding.tvMomentPromptTitle.setText(prompt.titleRes);
        binding.tvMomentPromptDesc.setText(prompt.descRes);
        binding.tvMomentPromptPoints.setText(getString(R.string.quest_points, prompt.points));
    }

    /** Sets up the quest list and its click handling. */
    private void setupQuestsRecyclerView() {
        questsAdapter = new QuestsAdapter(new QuestsAdapter.OnQuestClickListener() {
            @Override
            public void onQuestComplete(Quest quest) {
                homeViewModel.completeQuest(quest, new QuestsRepository.OnQuestCompletedListener() {
                    @Override
                    public void onSuccess() {
                        showCompletionFeedback(quest.points);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Snackbar.make(binding.getRoot(), getString(R.string.quest_complete_failed), Snackbar.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onQuestClick(Quest quest) {
                openActivityScreen();
            }
        });
        binding.rvQuests.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvQuests.setAdapter(questsAdapter);
    }

    /**
     * Binds all data sources to the UI: user, profile stats, moments, mood entries and quests.
     * Hides the loading overlay once the user data has arrived.
     */
    private void observeViewModel() {
        binding.loadingBox.getRoot().setVisibility(View.VISIBLE);
        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            binding.loadingBox.getRoot().setVisibility(View.GONE);
            if (user != null) {
                binding.tvWelcome.setText(getString(R.string.welcome_back, user.name));
                binding.tvPoints.setText(String.valueOf(user.currentPoints));
                binding.tvLevelLabel.setText(getString(R.string.level_label, user.level));
                binding.tvLevelTitleHome.setText(titleForLevel(user.level));

                // 1000 points per level
                int progressInLevel = user.currentPoints % 1000;
                int percent = progressInLevel / 10;
                binding.tvProgressLabel.setText(getString(R.string.progress_to_level, user.level + 1));
                binding.tvProgressPercent.setText(String.format(Locale.ENGLISH, "%d%%", percent));
                binding.pbLevelProgress.setProgress(percent);
            }
        });

        profileViewModel.getProfileStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats != null) {
                latestStats = stats;
                bindHighlight();
            }
        });

        // counts of captured moments and mood check-ins feed the highlight card
        momentsViewModel.getMoments().observe(getViewLifecycleOwner(), moments -> {
            momentCount = moments != null ? moments.size() : 0;
            bindHighlight();
        });

        moodViewModel.getMoodHistory().observe(getViewLifecycleOwner(), moods -> {
            moodCount = moods != null ? moods.size() : 0;
            bindHighlight();
        });

        homeViewModel.getAvailableQuests().observe(getViewLifecycleOwner(), quests -> {
            if (quests != null) {
                questsAdapter.setQuests(quests);
            }
        });

        binding.pbQuest.setMax(stepThreshold);
    }

    /** Wires the home cards to their destinations (map, mood, activity, moments). */
    private void setupNavigation() {
        binding.cardExploreMap.setOnClickListener(v -> navigateTo(R.id.navigation_map));
        binding.cardLogMood.setOnClickListener(v -> navigateTo(R.id.navigation_mood));
        binding.cardActivity.setOnClickListener(v -> openActivityScreen());

        // step cards open the Activity detail screen
        binding.cardTodaysQuest.setOnClickListener(v -> openActivityScreen());
        binding.cardQuestKm.setOnClickListener(v -> openActivityScreen());
        // the moment card opens the Moments tab where the photo can be captured
        binding.cardTodaysMoment.setOnClickListener(v -> navigateTo(R.id.navigation_moments));
    }

    /**
     * Updates the step and distance display. Goal completion (points and quest count) is
     * handled by {@link StepCounter}, so it also works while this screen is closed.
     */
    private void updateStepDisplay() {
        if (binding != null) {
            int currentSteps = getSteps();
            binding.tvQuestProgressText.setText(currentSteps >= stepThreshold
                    ? getString(R.string.steps_progress_done, currentSteps)
                    : getString(R.string.steps_progress, currentSteps));
            binding.pbQuest.setProgress(currentSteps);

            // 1 km quest, progress bar works in meters
            double km = getKm();
            int metres = (int) Math.round(km * 1000);
            binding.tvQuestKmText.setText(metres >= 1000
                    ? String.format(Locale.ENGLISH, getString(R.string.km_progress_done), km)
                    : String.format(Locale.ENGLISH, getString(R.string.km_progress), km));
            binding.pbQuestKm.setProgress(Math.min(metres, 1000));
        }
    }

    /** Shows a green confirmation with the points earned. */
    private void showCompletionFeedback(int points) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), getString(R.string.quest_completed_feedback, points), Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark, null));
        snackbar.show();
    }

    /** Returns a greeting that matches the time of day. */
    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.greeting_morning);
        if (hour < 18) return getString(R.string.greeting_afternoon);
        return getString(R.string.greeting_evening);
    }

    /** Returns the title for the given level. */
    private String titleForLevel(int level) {
        if (level >= 10) return getString(R.string.level_master);
        if (level >= 5) return getString(R.string.level_expert);
        if (level >= 2) return getString(R.string.level_adventurer);
        return getString(R.string.level_explorer);
    }

    /**
     * Highlights the most meaningful recent achievement. The order deliberately prefers
     * behavioural activation (going out, noticing, checking in) over fitness metrics.
     */
    private void bindHighlight() {
        int totalQuests = latestStats != null ? latestStats.totalQuests : 0;
        int bestStreak = latestStats != null ? latestStats.bestStreak : 0;

        String title;
        String desc;
        int badge;

        if (bestStreak >= 3) {
            title = getString(R.string.ach_streak_title, bestStreak);
            desc = getString(R.string.ach_streak_desc);
            badge = bestStreak;
        } else if (momentCount >= 5) {
            title = getString(R.string.ach_moments_collector);
            desc = getString(R.string.ach_moments_collector_desc);
            badge = momentCount;
        } else if (moodCount >= 3) {
            title = getString(R.string.ach_checkin_title);
            desc = getString(R.string.ach_checkin_desc);
            badge = moodCount;
        } else if (momentCount >= 1) {
            title = getString(R.string.ach_first_moment);
            desc = getString(R.string.ach_first_moment_desc);
            badge = momentCount;
        } else if (totalQuests >= 1) {
            title = getString(R.string.ach_quest_complete);
            desc = getResources().getQuantityString(R.plurals.ach_quests_done, totalQuests, totalQuests);
            badge = totalQuests;
        } else {
            title = getString(R.string.ach_getting_started);
            desc = getString(R.string.ach_getting_started_desc);
            badge = 0;
        }

        binding.tvAchievementTitle.setText(title);
        binding.tvAchievementDesc.setText(desc);
        binding.tvAchievementBadge.setText(String.valueOf(badge));
    }

    /** Switches to the given destination via the bottom navigation. */
    private void navigateTo(int destinationId) {
        if (getActivity() != null) {
            BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(destinationId);
            }
        }
    }

    /** Opens the activity screen as a detail destination. */
    private void openActivityScreen() {
        View root = getView();
        if (root != null) {
            NavOptions options = new NavOptions.Builder().setLaunchSingleTop(true).build();
            Navigation.findNavController(root).navigate(R.id.navigation_activity, null, options);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getContext(), StepCounter.class);
        getContext().bindService(intent, connection, Context.BIND_AUTO_CREATE);
        handler.post(updateStepsRunnable);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isBound) {
            getContext().unbindService(connection);
            isBound = false;
        }
        handler.removeCallbacks(updateStepsRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public int getSteps() {
        if (isBound && stepCounterService != null) {
            // daily total, same source as km/calories below, so the cards stay in sync
            return stepCounterService.getDailySteps();
        }
        return 0;
    }

    public double getKm() {
        if (isBound && stepCounterService != null) {
            return stepCounterService.getKm();
        }
        return 0.0;
    }
}
