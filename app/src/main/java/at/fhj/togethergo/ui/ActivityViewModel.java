package at.fhj.togethergo.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import at.fhj.togethergo.data.ActivityRepository;
import at.fhj.togethergo.data.models.DailyActivity;

/**
 * View model for the activity screen. Exposes today's activity and the weekly overview
 * from {@link ActivityRepository}.
 */
public class ActivityViewModel extends ViewModel {
    private final ActivityRepository repository;
    private final LiveData<DailyActivity> todayActivity;
    private final LiveData<List<DailyActivity>> weeklyActivity;

    public ActivityViewModel() {
        this.repository = new ActivityRepository();
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        this.todayActivity = repository.getTodayActivity(todayStr);
        this.weeklyActivity = repository.getWeeklyActivity();
    }

    public LiveData<DailyActivity> getTodayActivity() {
        return todayActivity;
    }

    public LiveData<List<DailyActivity>> getWeeklyActivity() {
        return weeklyActivity;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.detach();
    }
}
