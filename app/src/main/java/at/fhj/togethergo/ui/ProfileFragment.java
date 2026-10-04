package at.fhj.togethergo.ui;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import at.fhj.togethergo.LocaleManager;
import at.fhj.togethergo.R;
import at.fhj.togethergo.ThemeManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.Locale;

import at.fhj.togethergo.MainViewModel;
import at.fhj.togethergo.data.models.ProfileStats;
import at.fhj.togethergo.databinding.FragmentProfileBinding;
import android.content.Intent;
import android.widget.Toast;

/**
 * Profile screen: name, level, points, key stats and badges, plus settings for theme,
 * language and logout.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private MainViewModel viewModel;
    private ProfileViewModel profileViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        observeUserData();

        observeStats();

        showMemberSince();
        setupThemeToggle();
        setupLanguageRow();

        binding.btnLogout.setOnClickListener(v -> logout());
    }

    /** Signs the user out and returns to the login screen. */
    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    /** Observes the user profile and updates name, level and points. */
    private void observeUserData() {
        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                binding.tvProfileName.setText(user.name);
                binding.tvLevel.setText(getString(R.string.level_label, user.level));
                binding.tvPoints.setText(getString(R.string.total_points, user.currentPoints));
                binding.tvLevelTitle.setText(titleForLevel(user.level));
                binding.tvStatPoints.setText(String.valueOf(user.currentPoints));
            }
        });
    }

    /** Shows the loading overlay and observes the profile stats. */
    private void observeStats() {
        binding.loadingBox.getRoot().setVisibility(View.VISIBLE);
        profileViewModel.getProfileStats().observe(getViewLifecycleOwner(), stats -> {
            binding.loadingBox.getRoot().setVisibility(View.GONE);
            if (stats != null) {
                bindStats(stats);
            }
        });
    }

    /** Sets up the dark mode switch. */
    private void setupThemeToggle() {
        // reflect the current state, then let the switch override the system setting
        binding.switchDarkMode.setChecked(ThemeManager.isDark(requireContext()));
        binding.switchDarkMode.setOnCheckedChangeListener((button, isChecked) -> {
            if (!button.isPressed()) {
                return; // ignore programmatic changes
            }
            ThemeManager.setMode(requireContext(), isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        });
    }

    /** Sets up the language picker; AppCompat recreates the activity with the new language. */
    private void setupLanguageRow() {
        updateLanguageValue();
        binding.rowLanguage.setOnClickListener(v -> {
            String[] options = {getString(R.string.lang_english), getString(R.string.lang_german)};
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.choose_language)
                    .setItems(options, (dialog, which) ->
                            LocaleManager.setLanguage(which == 1 ? "de" : "en"))
                    .show();
        });
    }

    private void updateLanguageValue() {
        boolean german = "de".equals(LocaleManager.getCurrentLanguage());
        binding.tvLanguageValue.setText(german ? R.string.lang_german : R.string.lang_english);
    }

    /** Shows the year the Firebase account was created. */
    private void showMemberSince() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getMetadata() == null) {
            return;
        }
        Calendar created = Calendar.getInstance();
        created.setTimeInMillis(user.getMetadata().getCreationTimestamp());
        binding.tvMemberSince.setText(getString(R.string.member_since, created.get(Calendar.YEAR)));
    }

    /** Shows the stats and unlocks badges based on progress. */
    private void bindStats(ProfileStats stats) {
        binding.tvStatQuests.setText(String.valueOf(stats.totalQuests));
        binding.tvStatDays.setText(String.valueOf(stats.activeDays));
        binding.tvStatSteps.setText(formatSteps(stats.totalSteps));

        binding.tvStreak.setText(stats.streak > 0
                ? getString(R.string.streak_days, stats.streak)
                : getString(R.string.streak_none));

        // unlock badges depending on real progress
        setBadgeUnlocked(binding.badge1, true, R.string.badge_starter_desc);                        // starter, always on
        setBadgeUnlocked(binding.badge2, stats.totalSteps >= 10000, R.string.badge_walker_desc);   // walker
        setBadgeUnlocked(binding.badge3, stats.bestStreak >= 3, R.string.badge_climber_desc);       // climber
        setBadgeUnlocked(binding.badge4, stats.totalQuests >= 1, R.string.badge_quester_desc);      // quester
        setBadgeUnlocked(binding.badge5, stats.totalSteps >= 50000, R.string.badge_marathoner_desc);   // marathoner
        setBadgeUnlocked(binding.badge6, stats.bestStreak >= 7, R.string.badge_champion_desc);       // champion
        setBadgeUnlocked(binding.badge7, stats.moodCheckins >= 10, R.string.badge_reflective_desc);   // reflective
        setBadgeUnlocked(binding.badge8, stats.totalQuests >= 30, R.string.badge_collector_desc);     // collector
        setBadgeUnlocked(binding.badge9, stats.bestStreak >= 14, R.string.badge_legend_desc);         // legend
    }

    /** Returns the title for the given level. */
    private String titleForLevel(int level) {
        if (level >= 10) return getString(R.string.level_master);
        if (level >= 5) return getString(R.string.level_expert);
        if (level >= 2) return getString(R.string.level_adventurer);
        return getString(R.string.level_explorer);
    }

    /** Formats a step count, using thousands from 1000 steps (e.g. "12.3k"). */
    private String formatSteps(long steps) {
        if (steps >= 1000) {
            return String.format(Locale.ENGLISH, getString(R.string.steps_thousands), steps / 1000.0);
        }
        return String.valueOf(steps);
    }

    /** Shows a badge in colour or greyed out and displays its description on tap. */
    private void setBadgeUnlocked(ImageView badge, boolean unlocked, int descriptionResId) {
        if (unlocked) {
            badge.clearColorFilter();
            badge.setAlpha(1f);
        } else {
            applyGrayscale(badge);
        }
        badge.setOnClickListener(v -> Toast.makeText(requireContext(), descriptionResId, Toast.LENGTH_SHORT).show());
    }

    /** Applies a greyscale filter to show a locked state. */
    private void applyGrayscale(ImageView imageView) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        imageView.setColorFilter(filter);
        imageView.setAlpha(0.5f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
