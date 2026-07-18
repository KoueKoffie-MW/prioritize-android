# Walkthrough: Google Play Store Assets

All copywriting and graphic design assets required to publish the **Prioritize** app on the Google Play Store have been successfully generated and verified.

---

## 1. Store Copywriting

Here is the finalized copywriting for your Play Store listing. You can copy and paste this directly into the Google Play Console:

### App Title (Max 30 characters)
```text
Prioritize AI: Dynamic Focus
```

### Short Description (Max 80 characters)
```text
Dynamic prioritization, Eisenhower matrix, and offline local AI Second Brain.
```

### Full Description (Max 4000 characters)
```text
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

## 2. Store Marketing Graphics

These graphics are located in your app/DOCS folder:

### App Icon (512x512 PNG)
A premium dark theme checkmark morphing into neural network paths.
![App Icon](file:///y:/AntiGravity/Android_Apps/Prioritize/app/DOCS/app_icon.png)

### Feature Graphic (1024x500 PNG)
High-contrast branding featuring floating 3D Eisenhower quadrant references.
![Feature Graphic](file:///y:/AntiGravity/Android_Apps/Prioritize/app/DOCS/feature_graphic.png)

---

## 3. Play Store Screenshots (1080x2400 PNG)

We booted the emulator, compiled and deployed the app, populated the database with typical tasks, and captured screenshots of the five core navigation tabs:

````carousel
![01 - Focus Dashboard](file:///y:/AntiGravity/Android_Apps/Prioritize/app/DOCS/screenshot_01_focus.png)
<!-- slide -->
![02 - Scratch Pad Inbox](file:///y:/AntiGravity/Android_Apps/Prioritize/app/DOCS/screenshot_02_scratchpad.png)
<!-- slide -->
![03 - Eisenhower Matrix](file:///y:/AntiGravity/Android_Apps/Prioritize/app/DOCS/screenshot_03_matrix.png)
<!-- slide -->
![04 - Horizon Timeline](file:///y:/AntiGravity/Android_Apps/Prioritize/app/DOCS/screenshot_04_horizon.png)
<!-- slide -->
![05 - Second Brain Chat](file:///y:/AntiGravity/Android_Apps/Prioritize/app/DOCS/screenshot_05_brain.png)
````

---

## 4. Technical Checklist Completed

* **Seeded Realistic Data**: Seeded tasks matching engineering routines (Simscape Multibody, CAD parameters, Unreal Engine imports), family routines (Picnic, anniversaries), and server setups.
* **Headless Android Emulator (AVD)**: Started the AVD headlessly (`-no-window` and `-no-audio`) to successfully bypass VM GPU driver limitations.
* **ADB Automation**: Used ADB commands to install the APK, tap bottom navigation tabs programmatically by coordinate calculations, and capture clean UI frames.
* **Code Reverted**: Restored `TaskViewModel.kt` to its original clean state so that mock data seeding is not packaged into your production code.
