# Gemini's Goal — Prioritize App

**Mission**  
Make Prioritize the absolute best ADHD-friendly second brain + priority management tool on Android.  
The app must feel calm, reliable, delightful, and deeply respectful of the user's limited attention and working memory.

**Guiding Principles**
- **ADHD-first**: Minimize cognitive load, externalize memory, support context switching, protect from accidental actions.
- **Reliability > Features**: Capture, prioritization, execution, and reflection loops must just work.
- **Wonderful UX**: Micro-interactions, haptics, animations, visual hierarchy, and feedback should feel premium and calm.
- **Privacy & On-Device**: Everything that can stay local stays local. Cloud is opt-in and transparent.
- **Research-Driven**: Decisions are informed by real ADHD productivity research, best-in-class apps (Things, Todoist, Sunsama, Reclaim, Reflect), and modern on-device AI patterns.
- **Iterative & Honest**: Fix what’s broken first. Add features only when they meaningfully reduce friction or increase clarity.

**Current Target Issues (Branch `GeminisGoal`)**

| # | Title | Type | Priority | Target Status |
|---|-------|------|----------|---------------|
| 1 | voice transcript (recording) not working | Bug | P0 | Fixing recognizer reset & continuous listening |
| 2 | image and audio files not working in brain tab | Bug | P1 | Fixing multimodal image & audio pipeline |
| 3 | marking something as completed still not working | Bug | P0 | Fixing task completion flow & list sync |
| 4 | audio recording should allow for longer pauses | Enhancement | P2 | Increasing silence threshold & pause handling |
| 5 | remove the slide to complete or delete function it is way too sensitive | Enhancement | P1 | Replacing swipe with direct UI actions & Undo |

**How I Will Operate (Standing Mandate)**

1. **Periodically check GitHub issues** (via GitHub tools/cron) and triage/fix newly submitted issues.
2. **Research** best approaches:
   - ADHD productivity patterns & friction points
   - Task management & second-brain UX (mobile + desktop)
   - On-device AI / multimodal interaction best practices
3. **Prioritize ruthlessly**:
   - Core loops first (Capture → Prioritize → Execute → Reflect)
   - Reliability and trust before polish
   - Polish and delight before new major features
4. **Improve UI/UX continuously** — every screen, every interaction.

---

*Branch: GeminisGoal*  
*Date: 2026-08-05*  
*Owner: Gemma (AI Strategic Peer for Jan)*
