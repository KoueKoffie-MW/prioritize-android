# Grok's Goal — Prioritize App

**Mission**  
Make Prioritize the best possible ADHD-friendly second brain + priority management tool on Android.  
The app must feel calm, reliable, delightful, and deeply respectful of the user's limited attention and working memory.

**Guiding Principles**
- **ADHD-first**: Minimize cognitive load, externalize memory, support context switching, protect from accidental actions.
- **Reliability > Features**: Capture, prioritization, execution, and reflection loops must just work.
- **Wonderful UX**: Micro-interactions, haptics, animations, visual hierarchy, and feedback should feel premium and calm.
- **Privacy & On-Device**: Everything that can stay local stays local. Cloud is opt-in and transparent.
- **Research-Driven**: Decisions are informed by real ADHD productivity research, best-in-class apps (Things, Todoist, Sunsama, Reclaim, Reflect, etc.), and modern on-device AI patterns.
- **Iterative & Honest**: Fix what’s broken first. Add features only when they meaningfully reduce friction or increase clarity.

**Current Open Issues (as of branch creation)**

| # | Title | Type | Priority (Grok) | Notes |
|---|-------|------|------------------|-------|
| 1 | voice transcript (recording) not working | Bug | P0 | Core capture method |
| 2 | image and audio files not working in brain tab | Bug | P1 | Second brain attachment reliability |
| 3 | marking something as completed still not working | Bug | P0 | Fundamental execution loop |
| 4 | audio recording should allow for longer pauses | Enhancement | P2 | Related to #1 |
| 5 | remove the slide to complete or delete function it is way too sensitive | Enhancement | P1 | Accidental data loss risk |

**How I Will Operate (Ongoing Mandate)**

1. **Periodically check GitHub issues** (manually or via tools) and triage/fix them.
2. **Research** best approaches:
   - ADHD productivity patterns & friction points
   - Excellent task management & second-brain UX (mobile + desktop)
   - On-device AI / multimodal interaction best practices
   - Gesture, voice, and attachment UX patterns
   - Calm technology & attention restoration design
3. **Prioritize ruthlessly**:
   - Core loops first (Capture → Prioritize → Execute → Reflect)
   - Reliability and trust before polish
   - Polish and delight before new major features
4. **Improve UI/UX continuously** — every screen, every interaction, every micro-moment.
5. **Document decisions** in this file or linked docs.
6. **Propose + implement** fixes and improvements on this branch (`GroksGoal`).

**Immediate Focus Areas (Starting Now)**

- Fix the three P0/P1 bugs (#1, #3, #2)
- Redesign or replace the swipe-to-complete/delete gesture (issue #5)
- Improve voice capture reliability and natural pause handling
- Make attachments (image + audio) first-class and trustworthy in the Brain
- Audit and elevate overall micro-interactions, haptics, loading states, and empty states

**Success Metrics (for me as agent)**
- Users can reliably capture thoughts (voice/text/image) without friction or loss.
- Users can trust that marking something complete actually works and feels good.
- The Brain feels like a true, low-friction second brain.
- Opening the app feels calming and clarifying rather than overwhelming.
- New users can become productive quickly; power users feel the app "gets" them.

This is now my standing goal. I will keep returning to it, checking issues, researching, and shipping improvements on the `GroksGoal` branch.

---

*Branch created: GroksGoal*  
*Date: 2026-08-05*  
*Owner: Grok (via Eben agent for Jan)*

---

## Research Notes (Living Section)

### ADHD Productivity Research & Inspirations
- [ ] Things 3 / Todoist / Sunsama / Reclaim.ai / Linear
- [ ] "Getting Things Done" + modern interpretations for ADHD
- [ ] "The Productivity Project", "Deep Work", "Indistractable"
- [ ] Voice UI patterns that work for neurodivergent users
- [ ] Attachment & context capture in second-brain tools (Reflect, Logseq, Capacities, Tana)

### UI/UX Targets
- Calm, high-contrast but low-stimulation dark theme
- Excellent use of haptics
- Clear visual distinction between "thinking" and "acting" modes
- Fast, predictable gestures with strong confirmation for destructive actions
- Rich but not noisy attachment previews

### On-Device AI
- Low latency feedback
- Clear "thinking" visibility (without being annoying)
- Graceful degradation when models are unavailable
- Good multimodal handling (image + audio + text)

Add findings here as research happens.
## Progress Log (GroksGoal)

### 2026-08-05 — Session 1 (Autonomous)
- Created branch + GROKS_GOAL.md with mission.
- **Fixed swipe gesture sensitivity** (issues #5 + helps #3):
  - Raised swipe threshold from 60% to 75% for complete action.
  - Made delete swipe delegate to parent for future confirmation + undo.
  - Added clear comments explaining ADHD considerations.
- Committed on GroksGoal.
- Research applied: NN/g and accessibility sources confirm high swipe sensitivity is a major source of frustration and accidental data loss, especially for ADHD users.

Next autonomous actions (in priority order):
1. Add proper undo Snackbar for complete + delete in FocusListScreen.
2. Harden toggleTaskCompletion (better logging, immediate local feedback if needed).
3. Move to voice transcription reliability (#1 + #4).
4. Fix attachment persistence/display in Brain (#2).
5. Broader UI/UX polish and research.


### 2026-08-05 — Session 1 continued
- **Added full undo for completion** (handleCompleteTask + Snackbar):
  - When you swipe or checkbox complete a task, you now get an immediate "marked complete — Undo" snackbar.
  - Symmetric to the existing delete undo.
  - This should finally make "marking as completed" feel trustworthy and reversible.
- Both delete and complete now have easy recovery paths.
- This is a major win for core execution loop reliability and ADHD-friendly safety.

Next: Voice capture reliability (#1 + #4).


### Later in session
- **Voice capture resilience**:
  - Improved onError handling in BrainScreen: now salvages partialTranscriptText and opens the review dialog instead of dropping the recording.
  - This should make "voice transcript not working" much less common.
- Still to do in this area: Further tuning, better visual states, possibly better language handling.

Next autonomous target: Image/audio attachments in Brain tab (#2) + broader attachment UX.


### Later attachment work
- Added existence guards in image and audio rendering in ChatMessageBubble / AudioPlayerWidget.
- Clear "unavailable" messaging instead of blank or crash.
- Combined with earlier copy improvements, attachments should now be far more reliable.


**Commit/Push Policy (as requested):**  
After every meaningful change (fix, research finding, or UX tweak), I commit locally with a clear message and immediately attempt `git push origin GroksGoal`.  
Local commits are always done; remote push may retry on timeout.


### Recent autonomous progress (continuous commits + pushes)
- Restored + enhanced completion with handleCompleteTask (undo) + added haptic feedback on mark complete.
- Attachment preview now shows file sizes (KB) for images and audio → more transparent and trustworthy.
- All changes committed locally and pushed to origin/GroksGoal.

Still targeting the original 5 issues with iterative hardening + delight.

