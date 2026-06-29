# Math Alarm Clock 🧮⏰

Math Alarm is an Android application designed to kickstart your morning. The core premise is simple: **the alarm won't stop ringing until you solve a mathematical challenge**. Once you prove you are awake by solving the problem, the app rewards you with a summarized digest of the top five latest AI-related news stories curated by Google Gemini.

---

## 🚀 Key Features

*   **Unstoppable Alarm**: Once triggered, the alarm plays an ongoing loop of your default ringtone and vibrates. It runs as a foreground service (`mediaPlayback` type) to ensure Android does not kill it.
*   **Math Challenge**: Supports addition (+), subtraction (-), multiplication (×), and division (÷). The problem is randomly generated and must be answered correctly to silence the alarm.
*   **Gemini AI Morning News**: Dismissing the alarm immediately triggers the Google Gemini model to fetch and summarize the top five most recent advancements in artificial intelligence. 
*   **Elegant Layout**:
    *   **Title**: Catchy headlines (e.g., "Google Announces Gemini 1.5 Pro").
    *   **Subtitle**: High-level subtitle describing the context.
    *   **Source Summary**: An easy-to-read, concise summary that explicitly cites the news publication or company announcement (e.g., "TechCrunch", "Google DeepMind's official press release") from which the story was sourced.
    *   **Direct Access Links**: Direct links to read the complete article on the web.
*   **Local Alarm Persistence**: Store and schedule multiple custom recurring alarms using an integrated local Room Database.
*   **Developer Testing Banner**: Includes an in-app trigger tool to test the alarm flow, math challenge, and Gemini news retrieval in 5 seconds flat without waiting.

---

## 🛠️ Architecture & Tech Stack

This project is built using modern Android development practices and components:

*   **Language**: [Kotlin](https://kotlinlang.org/)
*   **UI Framework**: [Jetpack Compose](https://developer.android.com/compose) with modern Material Design 3 (M3) components, responsive dark/light color schemes, and custom animations.
*   **Database**: [Room SQLite Database](https://developer.android.com/training/data-storage/room) for lightweight offline persistence of your scheduled alarms.
*   **Networking**: [Retrofit 2](https://square.github.io/retrofit/) & [Moshi Converter](https://github.com/square/moshi) for robust, type-safe API calls to the Google Gemini endpoint.
*   **AI Engine**: [Google Gemini 3.5 Flash](https://deepmind.google/technologies/gemini/) configured with a precise JSON response schema to fetch current, high-fidelity AI news.
*   **Alarm Scheduling**: Android `AlarmManager` with exact execution scheduling (`setExactAndAllowWhileIdle`) and broadcast receivers.

---

## ⚙️ Configuration & Secrets Management

To run the AI news summary feature successfully, the application requires a Google Gemini API Key.

1.  Obtain an API key from Google AI Studio.
2.  Add your API key to your environment. At runtime, the app pulls the secret using `BuildConfig.GEMINI_API_KEY`.
3.  If no API key is provided, the application gracefully falls back to a curated offline set of AI news articles so you can still preview the layout seamlessly.

---

## 📦 CI/CD (GitHub Actions)

This repository includes a pre-configured continuous integration pipeline in `.github/workflows/android.yml`.

Whenever you push to the `main`, `master`, or `developer` branches:
1.  **Automated Build**: GitHub Actions sets up JDK 17, caches Gradle, and compiles the source code.
2.  **Artifact Generation**: Builds the standard debug APK (`app-debug.apk`).
3.  **Downloadable APK**: You can access and download the compiled APK file directly from the **Artifacts** section of the completed GitHub Action run.

### Running Locally

To build the APK manually on your machine, run the following command in the root folder:

```bash
./gradlew assembleDebug
```

The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`
