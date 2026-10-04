package at.fhj.togethergo.data;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.fhj.togethergo.data.entities.MoodEntryEntity;

/**
 * Stores mood check-ins in Firestore so the history survives a reinstall and follows the
 * user across devices.
 */
public class MoodRepository {
    private static final String TAG = "MoodRepository";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final List<ListenerRegistration> registrations = new ArrayList<>();

    public MoodRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    public LiveData<List<MoodEntryEntity>> getMoodHistory() {
        MutableLiveData<List<MoodEntryEntity>> history = new MutableLiveData<>();
        String userId = auth.getUid();
        if (userId == null) {
            return history;
        }

        registrations.add(db.collection("users").document(userId)
                .collection("mood_entries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed for mood entries.", e);
                        // show the friendly empty state instead of a blank screen on first error
                        if (history.getValue() == null) {
                            history.setValue(new ArrayList<>());
                        }
                        return;
                    }

                    List<MoodEntryEntity> moods = new ArrayList<>();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Long level = doc.getLong("moodLevel");
                            Long ts = doc.getLong("timestamp");
                            String note = doc.getString("note");
                            MoodEntryEntity entry = new MoodEntryEntity(
                                    level != null ? level.intValue() : 3,
                                    note != null ? note : "",
                                    ts != null ? ts : 0L
                            );
                            entry.firestoreId = doc.getId();
                            moods.add(entry);
                        }
                    }
                    history.setValue(moods);
                }));

        return history;
    }

    /** Removes the Firestore listener when the screen is gone, so listeners don't accumulate. */
    public void detach() {
        for (ListenerRegistration registration : registrations) {
            registration.remove();
        }
        registrations.clear();
    }

    public void addMood(int level, String note, long timestamp) {
        String userId = auth.getUid();
        if (userId == null) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("moodLevel", level);
        data.put("note", note);
        data.put("timestamp", timestamp);

        db.collection("users").document(userId)
                .collection("mood_entries")
                .add(data)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save mood.", e));
    }

    public void updateMood(String firestoreId, int level, String note) {
        String userId = auth.getUid();
        if (userId == null || firestoreId == null) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("moodLevel", level);
        data.put("note", note);

        db.collection("users").document(userId)
                .collection("mood_entries").document(firestoreId)
                .update(data)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update mood.", e));
    }

    public void deleteMood(String firestoreId) {
        String userId = auth.getUid();
        if (userId == null || firestoreId == null) {
            return;
        }

        db.collection("users").document(userId)
                .collection("mood_entries").document(firestoreId)
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete mood.", e));
    }
}
