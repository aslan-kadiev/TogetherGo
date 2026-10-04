package at.fhj.togethergo.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.firebase.auth.FirebaseAuth;

import at.fhj.togethergo.R;
import at.fhj.togethergo.databinding.ActivityMainBinding;

/**
 * Main activity hosting the bottom navigation. Ensures a user is signed in, sets up
 * navigation and requests the activity recognition permission once per process to start
 * the step counter.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;

    // request permissions only once per app process; recreating the activity (e.g. on a
    // theme or language change) must not pop the permission dialog again
    private static boolean permissionFlowStarted = false;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean activityRecognitionGranted = result.getOrDefault(android.Manifest.permission.ACTIVITY_RECOGNITION, false);

                if (activityRecognitionGranted != null && activityRecognitionGranted) {
                    startStepCounter();
                } else {
                    Toast.makeText(this, R.string.activity_recognition_needed, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initNav();

        // Only run the permission flow once per process. On a config-change recreation
        // we just make sure the step counter is running if the permission is already there.
        if (!permissionFlowStarted) {
            permissionFlowStarted = true;
            checkPermissionsAndStart();
        } else if (hasActivityRecognitionPermission()) {
            startStepCounter();
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /** Connects the bottom navigation bar to the navigation graph. */
    private void initNav() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            return navController.navigateUp() || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }

    /**
     * Returns whether the activity recognition permission is granted (always true below
     * Android 10).
     */
    private boolean hasActivityRecognitionPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Starts the step counter, requesting the required permissions first if needed
     * (plus notifications on Android 13+).
     */
    private void checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(new String[]{
                    android.Manifest.permission.ACTIVITY_RECOGNITION,
                    android.Manifest.permission.POST_NOTIFICATIONS
            });
        } else {
            startStepCounter();
        }
    }

    /** Starts {@link at.fhj.togethergo.services.StepCounter} as a foreground service. */
    private void startStepCounter() {
        Intent intent = new Intent(this, at.fhj.togethergo.services.StepCounter.class);
        startForegroundService(intent);
    }
}
