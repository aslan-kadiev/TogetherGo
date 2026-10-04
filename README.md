# TogetherGo

An Android app that helps people with low mood or depression become active again – through small daily quests, a photo journal, a mood diary and shared activities with other users.

TogetherGo is based on **behavioural activation**, an evidence-based approach from depression therapy: instead of tracking fitness, it rewards going outside, noticing the world and checking in with yourself.

> Team project in the **Health Informatics / eHealth** bachelor's programme at **FH JOANNEUM Graz**, Austria (2026).

<!--
## Screenshots
Add 3–4 screenshots to docs/screenshots/ and remove this comment.

<p>
  <img src="docs/screenshots/home.png" width="220" alt="Home">
  <img src="docs/screenshots/map.png" width="220" alt="Map">
  <img src="docs/screenshots/mood.png" width="220" alt="Mood journal">
</p>
-->

## Features

- **Daily quests and levels** – step, distance and calorie goals plus location quests; points, levels (1000 points per level) and achievements.
- **Shared map** – users drop meeting-point markers on a Google Map to invite others to a walk or a coffee. Markers sync in real time and expire automatically.
- **Location quests** – "walk to this spot" quests are completed automatically when the user gets close enough.
- **Step tracking** – a foreground service counts steps all day and derives distance, active minutes and calories.
- **Moments** – a photo journal with a daily prompt ("Photograph something green"); completing a prompt earns points.
- **Mood journal** – quick check-ins with an optional note, a weekly chart and a short reflection.
- **Profile** – statistics, streaks, badges, light/dark theme and language switch (English / German).

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| UI | Fragments, ViewBinding, Navigation Component, Material 3 |
| Architecture | MVVM – ViewModel, LiveData, repositories |
| Local storage | Room |
| Backend | Firebase Authentication, Cloud Firestore (real-time listeners, transactions) |
| Maps & location | Google Maps SDK for Android, LocationManager |
| Background work | Foreground service with the step counter sensor |
| Localization | English, German |
| Min / target SDK | 33 (Android 13) / 36 |

## Architecture

```
app/src/main/java/at/fhj/togethergo
├── ui/          Activities, fragments, adapters and screen view models
├── data/        Repositories (Firestore + Room) and photo prompts
│   ├── dao/       Room DAOs
│   ├── entities/  Room entities and map markers
│   └── models/    Firestore models
├── services/    StepCounter foreground service
├── util/        Image helpers
└── *.java       Application class, theme and locale managers, shared view models
```

Fragments observe `LiveData` from their view models; the view models delegate to repositories, which wrap Firestore listeners and Room DAOs. Quest rewards are written in Firestore transactions, so the same quest is never rewarded twice on one day.

### Firestore data model

```
users/{uid}                        name, email, level, currentPoints, totalQuests
├── daily_activity/{yyyy-MM-dd}    steps, km, activeMinutes, calories, goal flags
├── completed_quests/{questId}     completedAt, points
├── completed_photo_quests/{id}    promptId, dateKey, points
└── mood_entries/{id}              moodLevel, note, timestamp
quests/{id}                        title, description, points, type, location, radius
markers/{id}                       name, position, author, meeting time, expiry time
```

## Getting started

Requirements: a recent version of Android Studio and a device or emulator with Android 13+ (step counting needs a device with a step counter sensor).

1. **Clone** the repository and open it in Android Studio.
2. **Firebase**
   - Create a Firebase project and add an Android app with the package name `at.fhj.togethergo`.
   - Enable **Authentication → Email/Password** and **Cloud Firestore**.
   - Download `google-services.json` and place it in `app/`.
3. **Google Maps** – create an API key with the *Maps SDK for Android* enabled and add it to a `maps.properties` file in the project root:
   ```properties
   MAPS_API=your_google_maps_api_key
   ```
4. **Run** the `app` configuration.

`google-services.json` and `maps.properties` are git-ignored and must never be committed. Without them the project still builds: the app shows a hint instead of the login screen, and the map stays empty.

### Suggested Firestore security rules

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /quests/{id} {
      allow read: if request.auth != null;
    }
    match /markers/{id} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Authors

- **Aslan Kadiev** – [@aslan-kadiev](https://github.com/aslan-kadiev)
- **Simon Schiefecker** – [@Simonides-RK](https://github.com/Simonides-RK)

## License

Created as a university project at FH JOANNEUM. All rights reserved by the authors.
