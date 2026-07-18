# Implementation Plan: Google Play Store Assets for Prioritize

This plan outlines the steps and content required to prepare the **Prioritize** Android application for Google Play Store publication. 

---

## User Review Required

Please review the drafted App Store listings, proposed graphic design themes, and planned screenshot list. 

> [!IMPORTANT]
> Since the app integrates **LiteRT-LM (Gemma 4)** to run a local on-device LLM (requiring model file downloads), our store description must clearly explain that AI features run **100% locally and offline for absolute privacy**, which is a powerful selling point.

---

## Proposed Store Wording

Here is the draft copywriting for the Play Store listing.

### 1. App Title (Max 30 chars)
`Prioritize AI: Dynamic Focus`

### 2. Short Description (Max 80 chars)
`Dynamic prioritization, Eisenhower matrix, and offline local AI Second Brain.`

### 3. Full Description (Max 4000 chars)
```
Meet Prioritize, your privacy-first task manager and cognitive external brain. 

Prioritize does not just list your tasks; it organizes them dynamically using a smart prioritization scoring system. By focusing your attention on what matters most, Prioritize helps you bypass analysis paralysis and make execution friction-free.

LOCAL, OFFLINE COGNITION (YOUR SECOND BRAIN)
• On-Device AI: Powered by an integrated, local LLM (Gemma), all your data remains 100% on your device. No cloud storage, no data sharing, and zero network lag.
• Scratch Pad Capture: Dump raw thoughts, flight logs, or unstructured notes in the Scratch Pad. The offline AI automatically parses your text, extracts tasks, and structures them into actionable items.
• Dynamic Subtask Breakdown: Overwhelmed by a large project? Ask the Second Brain to break down complex tasks into bite-sized, logical steps instantly.
• Brain Chat: Talk directly to your local assistant to brainstorm ideas, review past logs, or refine your workflow.

INTELLIGENT DYNAMIC SCORING
• Focus Dashboard: Cuts out the noise by placing your Top 3 Priorities front and center.
• Dynamic Priority Sorting: Tasks are dynamically scored based on urgency, importance, due dates, and context, rather than simple date sorting.
• Eisenhower Matrix: Instantly visualize your workload in a 2x2 grid—Do First (Urgent & Important), Schedule, Delegate, and Eliminate.
• Horizon View: Keep an eye on what lies ahead with a clean, structured timeline for the upcoming days and weeks.

DESIGNED FOR FOCUS
• Beautiful dark mode interface designed to reduce visual fatigue.
• 100% offline functionality. Keep your planning completely secure, private, and always available.
• Native Android performance built on Jetpack Compose and Room database.

Build systems that work. Get Prioritize today and take control of your focus.
```

---

## Media Assets Plan

We will create high-quality assets to wow potential users, avoiding plain styles.

### 1. App Icon (`app_icon.png` - 512x512)
* **Concept**: A sleek, dark-themed modern icon. A stylized, layered neon purple-to-cyan checklist checkmark merging with a glowing network/neural node structure. This represents the junction of tasks (checklists) and cognitive intelligence (the Second Brain).
* **Style**: Dark background (`#0F0F1A`), smooth gradients, glassmorphism highlights.

### 2. Feature Graphic (`feature_graphic.png` - 1024x500)
* **Concept**: A vibrant, modern design featuring the app name "Prioritize" in premium bold typography (Outfit/Inter style) in the center, flanked by floating translucent 3D quadrants of the Eisenhower Matrix (glowing soft red, purple, and teal) with subtle network nodes connecting them.
* **Style**: High contrast dark background, neon accents, depth through shadow layers.

### 3. Screenshots (Phone Layout - 1080x2400)
We will boot the emulator, install the app, insert mock tasks, and capture:
1. **Focus Dashboard Screen** (`screenshot_01_focus.png`): Displaying top 3 priorities, such as engineering/life tasks, to showcase the primary workflow.
2. **Scratch Pad Screen** (`screenshot_02_scratchpad.png`): Displaying unstructured text being parsed into tasks.
3. **Eisenhower Matrix Screen** (`screenshot_03_matrix.png`): Displaying the 2x2 grid with colored quadrants and task counts.
4. **Horizon Screen** (`screenshot_04_horizon.png`): Showing the timeline view.
5. **Brain Profile Screen** (`screenshot_05_brain.png`): Showing the "Your Second Brain" chat interface and local model status.

---

## Proposed Changes / Technical Steps

We will automate the build, deployment, mock data generation, and screenshot capture.

### Setup & Build
- Verify the Android Emulator (AVD) is created and start it.
- Compile the debug APK using `./gradlew assembleDebug`.
- Install the APK on the running emulator.

### Mock Data Insertion
- To make screenshots look realistic, we will programmatically write a SQL script or run seed data in the app's SQLite database on the device.
- Alternatively, we can inject mock tasks using Android's content provider, ADB commands, or by temporarily seeding tasks in the Jetpack Compose repository/database directly in code, compiling, taking screenshots, and then reverting the code.
  > [!TIP]
  > Seeding in Kotlin code (e.g., in `MainActivity` or `TaskViewModel` initializer) is the most robust way because it bypasses raw DB manipulation and ensures Room's schemas are respected. We can add a temporary `viewModel.seedMockData()` method, run it once, take screenshots, and then revert the changes.

### Screenshot Generation
- Use `android screen capture` to pull PNGs of the running emulator.
- Save PNGs in the artifacts directory.
- Generate high-quality Store marketing graphics using `generate_image`.

---

## Verification Plan

### Automated Steps
- Execute `./gradlew assembleDebug` to verify it compiles.
- Run `android emulator list` and `adb devices` to check emulator status.
- Take snapshots of the screens.

### Manual Verification
- Review screenshots in the artifacts directory to ensure no emulator status bar anomalies and high quality.
