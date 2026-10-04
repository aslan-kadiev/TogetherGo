package at.fhj.togethergo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import at.fhj.togethergo.data.MoodRepository;
import at.fhj.togethergo.data.entities.MoodEntryEntity;

/**
 * View model for the mood journal. The history is loaded from Firestore so it survives
 * reinstalls; new entries are mirrored to Room.
 */
public class MoodViewModel extends ViewModel {
    private final MoodRepository moodRepository;
    private final LiveData<List<MoodEntryEntity>> moodHistory;

    public MoodViewModel() {
        this.moodRepository = new MoodRepository();
        this.moodHistory = moodRepository.getMoodHistory();
    }

    public LiveData<List<MoodEntryEntity>> getMoodHistory() {
        return moodHistory;
    }

    /**
     * Saves a new mood entry to Firestore and Room.
     * @param level mood from 1 (very bad) to 5 (very good)
     * @param note  optional note
     */
    public void addMood(int level, String note) {
        long timestamp = System.currentTimeMillis();
        moodRepository.addMood(level, note, timestamp);
    }

    /** Updates an existing mood entry in Firestore. */
    public void updateMood(String firestoreId, int level, String note) {
        moodRepository.updateMood(firestoreId, level, note);
    }

    /** Deletes a mood entry from Firestore. */
    public void deleteMood(String firestoreId) {
        moodRepository.deleteMood(firestoreId);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        moodRepository.detach();
    }
}
