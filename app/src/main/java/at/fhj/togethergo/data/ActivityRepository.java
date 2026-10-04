package at.fhj.togethergo.data;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import at.fhj.togethergo.data.models.DailyActivity;

/**
 * Reads the user's activity data from Firestore and exposes today's activity and a weekly
 * overview as LiveData.
 */
public class ActivityRepository {
    private static final String TAG = "ActivityRepository";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final List<ListenerRegistration> registrations = new ArrayList<>();

    public ActivityRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Observes the activity of a given day; emits an empty default if there is no entry yet.
     * @param dateStr day in yyyy-MM-dd format
     */
    public LiveData<DailyActivity> getTodayActivity(String dateStr) {
        MutableLiveData<DailyActivity> todayActivityData = new MutableLiveData<>();
        String userId = auth.getUid();

        if (userId == null) return todayActivityData;

        registrations.add(db.collection("users").document(userId)
                .collection("daily_activity").document(dateStr)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed for today activity.", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        todayActivityData.setValue(snapshot.toObject(DailyActivity.class));
                    } else {
                        todayActivityData.setValue(new DailyActivity(0, 5000, 0.0, 0, 0));
                    }
                }));

        return todayActivityData;
    }

    /** Observes the activity of the last seven days (including today), newest first. */
    public LiveData<List<DailyActivity>> getWeeklyActivity() {
        MutableLiveData<List<DailyActivity>> weeklyActivityData = new MutableLiveData<>();
        String userId = auth.getUid();

        if (userId == null) return weeklyActivityData;

        // Calculate the date 6 days ago to get a 7-day window including today
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        cal.add(Calendar.DAY_OF_YEAR, -6);
        String startDate = sdf.format(cal.getTime());

        // We use whereGreaterThanOrEqualTo on the documentId (date string) to avoid
        // needing a composite index for DESCENDING order on a subcollection.
        registrations.add(db.collection("users").document(userId)
                .collection("daily_activity")
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), startDate)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed for weekly activity.", e);
                        return;
                    }

                    if (snapshots != null) {
                        List<DailyActivity> activities = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            activities.add(doc.toObject(DailyActivity.class));
                        }
                        // Snapshots are ordered ASCENDING by documentId by default.
                        // We reverse it to newest-first to match what the UI expects.
                        Collections.reverse(activities);
                        weeklyActivityData.setValue(activities);
                    }
                }));

        return weeklyActivityData;
    }

    /** Removes the Firestore listeners when the screen is left, so they don't accumulate. */
    public void detach() {
        for (ListenerRegistration registration : registrations) {
            registration.remove();
        }
        registrations.clear();
    }
}
