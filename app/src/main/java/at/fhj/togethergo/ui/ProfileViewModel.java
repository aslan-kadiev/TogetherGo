package at.fhj.togethergo.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import at.fhj.togethergo.data.ProfileStatsRepository;
import at.fhj.togethergo.data.models.ProfileStats;

/**
 * View model for the profile screen. Exposes the aggregated stats from
 * {@link ProfileStatsRepository}.
 */
public class ProfileViewModel extends ViewModel {
    private final ProfileStatsRepository repository;
    private final LiveData<ProfileStats> profileStats;

    public ProfileViewModel() {
        this.repository = new ProfileStatsRepository();
        this.profileStats = repository.getProfileStats();
    }

    public LiveData<ProfileStats> getProfileStats() {
        return profileStats;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.detach();
    }
}
