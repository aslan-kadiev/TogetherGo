package at.fhj.togethergo.data;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import at.fhj.togethergo.data.models.ProfileStats;

/**
 * Aggregates the profile stats (quests, steps, streak, mood check-ins) from the current
 * user's Firestore data.
 */
public class ProfileStatsRepository {
    private static final String TAG = "ProfileStatsRepository";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final List<ListenerRegistration> registrations = new ArrayList<>();

    public ProfileStatsRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Observes the profile stats. The values come from three sources (user document, mood
     * entries, daily activity) and are published only once all three have loaded, so badges
     * don't flicker.
     */
    public LiveData<ProfileStats> getProfileStats() {
        MutableLiveData<ProfileStats> statsData = new MutableLiveData<>();
        String userId = auth.getUid();
        if (userId == null) {
            return statsData;
        }

        final ProfileStats stats = new ProfileStats();
        final boolean[] userLoaded = {false};
        final boolean[] activityLoaded = {false};
        final boolean[] moodLoaded = {false};

        final Runnable publish = () -> {
            if (userLoaded[0] && activityLoaded[0] && moodLoaded[0]) {
                statsData.setValue(stats);
            }
        };

        // listen to basic user stats (total quests)
        registrations.add(db.collection("users").document(userId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed for user stats.", e);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        Long total = snapshot.getLong("totalQuests");
                        stats.totalQuests = total != null ? total.intValue() : 0;
                    }
                    userLoaded[0] = true;
                    publish.run();
                }));

        // count mood check-ins to drive the reflection-focused achievements
        registrations.add(db.collection("users").document(userId)
                .collection("mood_entries")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed for mood entries.", e);
                        return;
                    }
                    stats.moodCheckins = snapshots != null ? snapshots.size() : 0;
                    moodLoaded[0] = true;
                    publish.run();
                }));

        // total steps, active days and streak from the daily activity log
        registrations.add(db.collection("users").document(userId)
                .collection("daily_activity")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed for daily activity.", e);
                        return;
                    }

                    long totalSteps = 0;
                    int activeDays = 0;
                    Set<String> activeDates = new HashSet<>();

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Long steps = doc.getLong("steps");
                            int stepCount = steps != null ? steps.intValue() : 0;
                            if (stepCount > 0) {
                                totalSteps += stepCount;
                                activeDays++;
                                activeDates.add(doc.getId());
                            }
                        }
                    }

                    stats.totalSteps = totalSteps;
                    stats.activeDays = activeDays;
                    stats.streak = calculateStreak(activeDates);
                    stats.bestStreak = calculateBestStreak(activeDates);

                    activityLoaded[0] = true;
                    publish.run();
                }));

        return statsData;
    }

    /** Removes the Firestore listeners when the screen is left, so they don't accumulate. */
    public void detach() {
        for (ListenerRegistration registration : registrations) {
            registration.remove();
        }
        registrations.clear();
    }

    /** Counts consecutive active days ending today (or yesterday, if nothing is logged today). */
    private int calculateStreak(Set<String> activeDates) {
        if (activeDates.isEmpty()) {
            return 0;
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cursor = Calendar.getInstance();

        // start from today, or yesterday if nothing logged today yet
        if (!activeDates.contains(format.format(cursor.getTime()))) {
            cursor.add(Calendar.DAY_OF_YEAR, -1);
            if (!activeDates.contains(format.format(cursor.getTime()))) {
                return 0;
            }
        }

        int streak = 0;
        while (activeDates.contains(format.format(cursor.getTime()))) {
            streak++;
            cursor.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    /** Returns the longest streak ever reached, even if it has ended. */
    private int calculateBestStreak(Set<String> activeDates) {
        if (activeDates.isEmpty()) {
            return 0;
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        format.setLenient(false);
        int best = 0;

        for (String date : activeDates) {
            Calendar previous = Calendar.getInstance();
            if (!setDate(previous, format, date)) {
                continue;
            }
            previous.add(Calendar.DAY_OF_YEAR, -1);
            if (activeDates.contains(format.format(previous.getTime()))) {
                continue;
            }

            int streak = 0;
            Calendar cursor = Calendar.getInstance();
            if (!setDate(cursor, format, date)) {
                continue;
            }
            while (activeDates.contains(format.format(cursor.getTime()))) {
                streak++;
                cursor.add(Calendar.DAY_OF_YEAR, 1);
            }
            best = Math.max(best, streak);
        }

        return best;
    }

    private boolean setDate(Calendar calendar, SimpleDateFormat format, String date) {
        try {
            calendar.setTime(format.parse(date));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
