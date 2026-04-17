# Edge-Tele — System Design & Backend Contract
Version 1.0 | April 2026 | Gemma 4 Good Hackathon

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                   Android Device (Offline)               │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐  │
│  │  Camera  │  │   Mic    │  │     Text Field        │  │
│  └────┬─────┘  └────┬─────┘  └──────────┬───────────┘  │
│       │              │                   │              │
│       ▼              ▼                   │              │
│  ┌─────────┐  ┌────────────┐            │              │
│  │  CameraX│  │ Google STT │            │              │
│  │  (JPEG) │  │ (on-device)│            │              │
│  └────┬────┘  └─────┬──────┘            │              │
│       │              │                   │              │
│       └──────────────┴───────────────────┘              │
│                      │                                   │
│                      ▼                                   │
│          ┌───────────────────────┐                       │
│          │   GemmaInferenceEngine│                       │
│          │   (LiteRT-LM, E2B)    │                       │
│          │   <1.5 GB RAM         │                       │
│          └───────────┬───────────┘                       │
│                      │                                   │
│          ┌───────────▼───────────┐                       │
│          │    ConfidenceGate     │                       │
│          │  GREEN >0.8           │                       │
│          │  YELLOW 0.6-0.8       │                       │
│          │  RED    <0.6          │                       │
│          └───────────┬───────────┘                       │
│                      │                                   │
│          ┌───────────▼───────────┐                       │
│          │    PlaybookMatcher    │                       │
│          │  (local JSON cache)   │                       │
│          └───────────┬───────────┘                       │
│                      │                                   │
│          ┌───────────▼───────────┐                       │
│          │   SyncPacketGenerator │                       │
│          │   (SQLite via Room)   │                       │
│          └───────────┬───────────┘                       │
│                      │                                   │
│          ┌───────────▼───────────┐                       │
│          │    SyncUploader       │                       │
│          │  (HTTP when online)   │                       │
│          │  (Share when offline) │                       │
│          └───────────────────────┘                       │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Module Contracts (Backend API Surface)

### 2.1 GemmaInferenceEngine

The single AI boundary. Everything AI-related flows through this interface.

```
Interface: GemmaInferenceEngine
─────────────────────────────────────────────────────────
Input:
  imageBytes   ByteArray?   JPEG bytes from CameraX (nullable)
  transcript   String?      STT output text (nullable)
  notes        String?      Free-text field input (nullable)
  language     Language     Enum: EN | BISLAMA | TOK_PISIN | HAITIAN_CREOLE

Output: ClassificationResult
  category     IncidentCategory   FLOOD_DAMAGE | STRUCTURAL_DAMAGE | 
                                  INJURY | CONTAMINATION | OTHER
  severity     Int                1–5
  confidence   Float              0.0–1.0
  rawResponse  String             Full Gemma JSON output for logging
  processingMs Long               Wall-clock inference time

StateFlow:
  isReady      StateFlow<Boolean>   Model loaded and ready
  isLoading    StateFlow<Boolean>   Inference in progress

Errors:
  InferenceException               Wraps LiteRT errors
  ModelNotReadyException           Called before isReady=true
  InsufficientMemoryException      OOM during inference
─────────────────────────────────────────────────────────
```

### 2.2 ConfidenceGate

Pure logic. No state, no dependencies. Evaluates every output before it reaches the UI.

```
Object: ConfidenceGate
─────────────────────────────────────────────────────────
Constants:
  GREEN_THRESHOLD  = 0.80f
  YELLOW_THRESHOLD = 0.60f

Functions:
  evaluate(confidence: Float) → ConfidenceLevel
    GREEN  if confidence >= 0.80
    YELLOW if confidence >= 0.60
    RED    if confidence < 0.60

  requiresExpertConfirmation(level: ConfidenceLevel) → Boolean
    true if level == YELLOW || level == RED

  badgeColor(level: ConfidenceLevel) → Color
    GREEN  → 0xFF2E7D32
    YELLOW → 0xFFF57F17
    RED    → 0xFFC62828
─────────────────────────────────────────────────────────
```

### 2.3 PlaybookMatcher

Reads from local JSON asset files. Never hits the network.

```
Interface: PlaybookMatcher
─────────────────────────────────────────────────────────
Input:
  category     IncidentCategory
  severity     Int              (filters high-priority actions for sev ≥ 4)
  language     Language

Output: List<PlaybookAction>   max 3 items, ordered by priority
  id           String
  action       String           Localized to requested language
  priority     Int              1 (highest) – 5 (lowest)
  tags         List<String>

Playbook asset path:
  assets/playbooks/{category_lowercase}.json
  e.g. assets/playbooks/flood_damage.json

Error:
  PlaybookNotFoundException    JSON asset missing for category
─────────────────────────────────────────────────────────
```

### 2.4 SyncPacketGenerator

Assembles and persists the full incident record.

```
Interface: SyncPacketGenerator
─────────────────────────────────────────────────────────
Input:
  responderID       String
  classification    ClassificationResult
  actions           List<PlaybookAction>
  photoPath         String?       Local file path on device
  transcript        String?
  notes             String?
  language          Language
  location          LatLng?       GPS if available

Output: SyncPacket (persisted to Room DB)
  id                String        UUID
  responderID       String
  timestampEpochMs  Long
  latitude          Double?
  longitude         Double?
  category          IncidentCategory
  severity          Int
  confidence        Float
  confidenceLevel   ConfidenceLevel
  recommendedActions List<String>
  photoHash         String?       SHA-256 of photo bytes
  voiceTranscript   String?
  textNotes         String?
  language          Language
  appVersion        String
  synced            Boolean       false until uploaded
  syncedAtMs        Long?
─────────────────────────────────────────────────────────
```

### 2.5 SyncUploader

Opportunistic — only fires when connectivity is detected.

```
Interface: SyncUploader
─────────────────────────────────────────────────────────
Trigger:
  ConnectivityManager callback → onNetworkAvailable()
  Manual: user taps "Sync Now"
  WorkManager: periodic attempt every 15 min when online

Input:
  Reads all SyncPackets where synced = false from Room DB

Output:
  HTTP POST /api/v1/incidents (JSON body = SyncPacket)
  On 200: marks packet synced=true, sets syncedAtMs
  On failure: retries with exponential backoff (WorkManager)

Manual export:
  Exports all unsynced packets as JSON file
  Shares via Android share sheet (any messaging app)

Note: No specific backend required. Endpoint is configurable.
      In demo mode, export-only works with no backend at all.
─────────────────────────────────────────────────────────
```

### 2.6 SpeechRecognitionManager

STT pipeline wrapping Google on-device recognition.

```
Interface: SpeechRecognitionManager
─────────────────────────────────────────────────────────
Input:
  language     Language    Sets recognition locale

Output (callback / Flow):
  onPartialResult(text: String)     Live transcription display
  onFinalResult(text: String)       Feed to GemmaInferenceEngine
  onError(error: SttError)          Microphone / language pack / timeout

SttError:
  LANGUAGE_NOT_SUPPORTED
  MICROPHONE_UNAVAILABLE
  TIMEOUT
  RECOGNITION_FAILED

Fallback:
  If language not supported by Google STT → show text input field
  prominently with note "Voice not available for this language"
─────────────────────────────────────────────────────────
```

---

## 3. Data Models

### Incident (Room entity, source of truth)

```
Incident
  id                String      PK, UUID
  responderID       String
  timestampEpochMs  Long
  latitude          Double?
  longitude         Double?
  photoPath         String?     Local file path
  photoHash         String?     SHA-256
  voiceTranscript   String?
  textNotes         String?
  language          String      Language enum name
  category          String      IncidentCategory enum name
  severity          Int
  confidence        Float
  confidenceLevel   String      ConfidenceLevel enum name
  recommendedActions String     JSON array of action strings
  rawGemmaResponse  String      Full model output
  inferenceMs       Long
  appVersion        String
  synced            Boolean     DEFAULT false
  syncedAtMs        Long?
```

### Playbook JSON Schema (per category file)

```json
{
  "category": "flood_damage",
  "version": "1.0",
  "actions": [
    {
      "id": "flood_001",
      "priority": 1,
      "tags": ["evacuation", "immediate"],
      "translations": {
        "en": "Evacuate building via highest available exit route",
        "bi": "Leavem bilding long haeapleis rod",
        "tpi": "Ranawe long haus long antap rot",
        "ht": "Kite bilding nan wout ki pi wo a"
      }
    }
  ]
}
```

---

## 4. Gemma 4 Prompt Contract

Every inference call uses a structured prompt with function calling to guarantee JSON output.

```
System prompt (injected at model load, language-aware):
  "You are an offline disaster response assistant. 
   You help trained field responders classify incidents.
   You are NOT an autonomous decision-maker.
   All outputs are DECISION SUPPORT for trained responders.
   Always respond in valid JSON matching the function schema."

User prompt assembly:
  [IMAGE TOKEN if imageBytes != null]
  "Field report ({language}): {transcript or notes}"
  "Classify this incident."

Function call schema (forces structured output):
  classify_incident(
    category: enum[flood_damage, structural_damage, 
                   injury, contamination, other],
    severity: integer 1-5,
    confidence: float 0.0-1.0,
    reasoning: string (brief, for log)
  )
```

---

## 5. Confidence Gate UI Contract

| Level  | Confidence | Badge color | Banner shown                                      |
|--------|------------|-------------|---------------------------------------------------|
| GREEN  | ≥ 0.80     | #2E7D32     | None                                              |
| YELLOW | 0.60–0.79  | #F57F17     | "Low confidence — seek expert confirmation"       |
| RED    | < 0.60     | #C62828     | "Very low confidence — do not act without expert" |

Every recommendation screen shows:
- Confidence badge (always)
- "Decision support for trained responders" watermark (always)
- Expert confirmation banner (YELLOW + RED only)

---

## 6. Language Codes

| Enum           | Locale tag | STT locale  | Asset key |
|----------------|------------|-------------|-----------|
| EN             | en-US      | en-US       | en        |
| BISLAMA        | bi-VU      | en-VU*      | bi        |
| TOK_PISIN      | tpi-PG     | en-PG*      | tpi       |
| HAITIAN_CREOLE | ht-HT      | fr-HT*      | ht        |

*Fallback to closest supported locale if exact locale not available.

---

## 7. Build Targets

| Dimension   | Value                                |
|-------------|--------------------------------------|
| Min SDK     | 26 (Android 8.0)                     |
| Target SDK  | 35                                   |
| Language    | Kotlin 2.0                           |
| UI          | Jetpack Compose                      |
| DI          | Hilt                                 |
| DB          | Room 2.6                             |
| AI runtime  | LiteRT-LM (formerly TFLite)          |
| Camera      | CameraX                              |
| STT         | Android SpeechRecognizer API         |
| Async       | Kotlin Coroutines + Flow             |
| Background  | WorkManager                          |

---

## 8. Directory Structure

```
app/src/main/
├── kotlin/com/dimaggi/edgetele/
│   ├── EdgeTeleApp.kt           Hilt application class
│   ├── MainActivity.kt          Single activity, Compose host
│   ├── data/
│   │   ├── model/
│   │   │   ├── Incident.kt      Room entity
│   │   │   ├── ClassificationResult.kt
│   │   │   ├── PlaybookAction.kt
│   │   │   ├── SyncPacket.kt
│   │   │   └── enums/
│   │   │       ├── IncidentCategory.kt
│   │   │       ├── Language.kt
│   │   │       └── ConfidenceLevel.kt
│   │   ├── db/
│   │   │   ├── EdgeTeleDatabase.kt
│   │   │   ├── IncidentDao.kt
│   │   │   └── Converters.kt
│   │   └── repository/
│   │       ├── IncidentRepository.kt
│   │       └── PlaybookRepository.kt
│   ├── ai/
│   │   ├── GemmaInferenceEngine.kt  (interface)
│   │   ├── GemmaInferenceEngineImpl.kt (LiteRT-LM impl)
│   │   ├── ConfidenceGate.kt
│   │   └── PlaybookMatcher.kt
│   ├── audio/
│   │   └── SpeechRecognitionManager.kt
│   ├── sync/
│   │   ├── SyncPacketGenerator.kt
│   │   └── SyncUploader.kt
│   ├── di/
│   │   ├── AppModule.kt
│   │   └── AiModule.kt
│   └── ui/
│       ├── theme/
│       │   └── Theme.kt
│       ├── navigation/
│       │   └── EdgeTeleNavGraph.kt
│       ├── screens/
│       │   ├── home/
│       │   │   ├── HomeScreen.kt
│       │   │   └── HomeViewModel.kt
│       │   ├── incident/
│       │   │   ├── NewIncidentScreen.kt
│       │   │   ├── NewIncidentViewModel.kt
│       │   │   ├── ResultScreen.kt
│       │   │   └── ResultViewModel.kt
│       │   └── log/
│       │       ├── IncidentLogScreen.kt
│       │       └── IncidentLogViewModel.kt
│       └── components/
│           ├── ConfidenceBadge.kt
│           ├── DecisionSupportWatermark.kt
│           ├── ExpertConfirmationBanner.kt
│           └── PlaybookActionCard.kt
└── assets/
    └── playbooks/
        ├── flood_damage.json
        ├── structural_damage.json
        ├── injury.json
        ├── contamination.json
        └── other.json
```

---

## 9. Key Design Rules (Non-Negotiable)

1. **No network calls during inference**. GemmaInferenceEngine must be callable with airplane mode on.
2. **Every ClassificationResult must pass through ConfidenceGate** before reaching the UI layer.
3. **Every recommendation screen must render DecisionSupportWatermark**. No exceptions.
4. **PlaybookMatcher reads only from assets/**. No dynamic download of playbooks at runtime.
5. **SyncPackets are never deleted automatically**. Only a manual authorized-user clear action removes them.
6. **Graceful degradation**: if GemmaInferenceEngine throws, fall back to structured form capture (no AI, just data collection).
