# Edge-Tele

**Offline AI copilot for disaster field responders.**

Built for the Gemma 4 Good Hackathon (April 2026). Edge-Tele runs entirely on-device — no internet required during incident classification. Field workers capture photos, speak or type field reports, and get structured incident classifications with actionable response playbooks, even in areas with zero connectivity.

---

## What it does

A field responder arrives at a disaster site. They open Edge-Tele, capture a photo and/or record a voice note, and the app:

1. Runs the report through **Gemma 4 E4B** on-device via LiteRT-LM
2. Classifies the incident (flood, structural damage, injury, contamination, or other) with a severity score (1–5)
3. Evaluates confidence and shows a **GREEN / YELLOW / RED** badge — low-confidence results require expert confirmation before acting
4. Surfaces up to 3 prioritised **playbook actions** from local JSON assets, localised into the responder's language
5. Persists the full incident record locally, then **syncs to a backend when connectivity returns** (or exports as JSON via the share sheet)

---

## Key design constraints

- **Fully offline inference** — Gemma 4 E4B runs with airplane mode on, <1.5 GB RAM. Photo classification requires API mode; offline mode classifies from voice and text only.
- **Visible mode indicator** — the app shows 📵 Offline Mode or 🌐 API Mode so the user always knows which inference path is active
- **Decision support only** — every result screen shows a "Decision support for trained responders" watermark; the model never acts autonomously
- **Graceful degradation** — if inference fails, the app falls back to structured data capture with no AI
- **No playbook downloads at runtime** — all response playbooks are bundled as local JSON assets

---

## Tech stack

| Layer | Library |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| AI runtime | MediaPipe Tasks GenAI (LiteRT-LM) |
| DI | Hilt |
| Database | Room 2.6 |
| Camera | CameraX |
| Speech-to-text | Android SpeechRecognizer (on-device) |
| Background sync | WorkManager |
| Async | Kotlin Coroutines + Flow |

Min SDK: 26 (Android 8.0) · Target SDK: 35

---

## Supported languages

| Language | STT fallback locale |
|---|---|
| English | en-US |
| Bislama (Vanuatu) | en-VU |
| Tok Pisin (Papua New Guinea) | en-PG |
| Haitian Creole | fr-HT |

If Google STT does not support the selected language, the app surfaces a prominent text-input field instead.

---

## Incident categories

`FLOOD_DAMAGE` · `STRUCTURAL_DAMAGE` · `INJURY` · `CONTAMINATION` · `OTHER`

Each category has a bundled playbook at `assets/playbooks/{category}.json` with prioritised, localised response actions.

---

## Confidence gate

| Level | Threshold | UI behaviour |
|---|---|---|
| GREEN | ≥ 0.80 | No banner — proceed |
| YELLOW | 0.60 – 0.79 | Banner: "Low confidence — seek expert confirmation" |
| RED | < 0.60 | Banner: "Very low confidence — do not act without expert" |

---

## Build variants

**Debug** — uses `MockGemmaInferenceEngineImpl`, no LiteRT/MediaPipe dependency needed. Lets you build and run the full UI without the model file.

**Release** — uses `GemmaInferenceEngineImpl` backed by MediaPipe Tasks GenAI and the LiteRT GPU delegate. Requires the Gemma 4 E4B model file on-device. Falls back to `GemmaApiInferenceEngineImpl` (Gemma 4 via cloud API) if the model file is absent; the UI shows 🌐 API Mode when this happens. R8 ProGuard rules are bundled in `app/proguard-rules.pro` to preserve MediaPipe and TFLite classes.

```bash
# Debug build (mock AI, no model file needed)
./gradlew assembleDebug

# Release build (real Gemma 4 E4B on-device inference)
./gradlew assembleRelease

# Push model file before first launch (release build)
adb push gemma-4-E4B-it.litertlm /sdcard/Android/data/com.dimaggi.edgetele/files/
```

---

## Sync

Incidents are stored in Room and marked `synced = false` until uploaded. Sync fires automatically when connectivity is detected (via `ConnectivityManager`) and retries with exponential backoff via WorkManager. Users can also tap **Sync Now** or export all unsynced packets as a JSON file via the Android share sheet — useful when the backend is unavailable but another device can relay data.

The backend endpoint is configurable. In demo mode, export-only works with no backend at all.

---

## Project structure

```
app/src/main/kotlin/com/dimaggi/edgetele/
├── ai/               GemmaInferenceEngine, GemmaInferenceEngineImpl, GemmaApiInferenceEngineImpl, ConfidenceGate, ModelInstaller
├── audio/            SpeechRecognitionManager
├── data/
│   ├── db/           Room database, DAO, type converters
│   ├── model/        Incident, ClassificationResult, PlaybookAction, enums
│   └── repository/   IncidentRepository, PlaybookRepository
├── di/               Hilt modules (AppModule, AiModule)
├── sync/             SyncPacketGenerator, SyncUploader
└── ui/
    ├── components/   ConfidenceBadge, DecisionSupportWatermark,
    │                 ExpertConfirmationBanner, PlaybookActionCard
    ├── navigation/   EdgeTeleNavGraph
    ├── screens/      home/, incident/, log/
    └── theme/
app/src/main/assets/playbooks/
    flood_damage.json · structural_damage.json · injury.json
    contamination.json · other.json
```

For full module contracts and data models, see [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md).
