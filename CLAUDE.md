# KAIRO

## PRODUCT

KAIRO is a voice-first Android daily planner for a college student who also has an internship, freelance clients and content work.

Core loop: shake or tap -> orb greeting + 20-second briefing -> fix the day with one Hinglish sentence -> focus block -> evening debrief.

It also has an alarm system (wake-up alarm and alarms tied to timetable blocks).

## HARD RULES

1. Stack: Kotlin, Jetpack Compose, Material 3, Room, DataStore, WorkManager, Glance, minSdk 26, targetSdk = latest stable. Single Gradle module with packages: `data`, `domain`, `ui`, `service`, `ai`, `alarm`, `util`.
2. The LLM only translates speech into validated JSON commands. All scheduling decisions are made by deterministic Kotlin code in `domain/Scheduler`. The model never writes to the database directly.
3. Every AI-made change is shown as a `PlanDiff` preview with Apply and Undo. Never silently move or delete anything.
4. `FixedBlock`s (lectures) and alarms are immovable by the scheduler.
5. Secrets (Claude, Sarvam, Picovoice keys) never go in the app or git. Only in Cloudflare Worker env or `local.properties` (gitignored).
6. Microphone is used only inside a visible activity. Never start the mic from a background service.
7. Every time-sensitive feature needs a fallback if the OS or OEM blocks it (QS tile, widget, notification action).
8. Privacy: send the model the minimum text needed, strip phone numbers and emails, and provide an AI-off mode.
9. Unit tests for all domain logic. A `@Preview` for every screen composable.
10. Before using any Android API with recent behavior changes (foreground services, exact alarms, full-screen intents, overlays, Live Updates, background activity launch), read the current official Android developer docs and tell me what you verified.

## WORKFLOW

- Do only the step I ask for. Do not build ahead.
- After each step: list files changed, how to run it, how to test it on a real device, and known limitations.
- Keep functions small, name things clearly, comment the why not the what.
- End every step with a suggested git commit message.
