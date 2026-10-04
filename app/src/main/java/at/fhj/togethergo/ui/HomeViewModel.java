package at.fhj.togethergo.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import at.fhj.togethergo.data.QuestsRepository;
import at.fhj.togethergo.data.models.Quest;

/**
 * View model for the home screen. Exposes the available quests and forwards completion
 * to {@link QuestsRepository}.
 */
public class HomeViewModel extends ViewModel {
    private final QuestsRepository repository;
    private final LiveData<List<Quest>> availableQuests;

    public HomeViewModel() {
        this.repository = new QuestsRepository();
        this.availableQuests = repository.getAvailableQuests();
    }

    public LiveData<List<Quest>> getAvailableQuests() {
        return availableQuests;
    }

    /** Completes the given quest and reports the result to {@code listener}. */
    public void completeQuest(Quest quest, QuestsRepository.OnQuestCompletedListener listener) {
        repository.completeQuest(quest, listener);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.detach();
    }
}
