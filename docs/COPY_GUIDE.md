# KAIRO copy guide

Every user-facing string lives in `app/src/main/res/values/strings.xml`. Kotlin only holds text in
`@Preview` functions and the sample-week data (which is user content, not UI).

## Voice
- **Calm, friendly, brief.** KAIRO talks like a helpful friend, not a system. "Your day is wide open",
  not "No entries found".
- **Plain words.** Say *timetable item*, *lane*, *alarm*, *check-in*. Never *block*, *role*, *parser*,
  *heartbeat*, *exact alarm*, *payload*.
- **Hinglish where the user speaks.** Command hints, the briefing input and voice errors use Hinglish
  ("Bolo ya type karo…", "Kuch suna nahi, dobara bolo"). Settings and explanations stay in simple English.
- **Honest.** If the phone may block something (shake, Live Updates), say so and give the fallback.

## Buttons
- Start with a verb: *Apply changes*, *Add task*, *Fix it*, *Turn it back on*, *Load sample week*,
  *Reset everything*. Exceptions: *Cancel*, *Close*, *Done*, *Next*, *Back*.
- A destructive confirmation names what happens (*Reset everything*); the safe choice is explicit
  (*Keep my data*).

## States
| State | Pattern | Example |
|---|---|---|
| Empty | What's missing + one action | "No tasks yet · Add what's on your mind…" + *Add task* |
| Loading | What KAIRO is doing | "Getting your day ready…" |
| Error | What happened + what still works | "Aaj ka AI quota khatam. Offline mode se kaam chal raha hai." |
| Permission | Why it's needed, in one line | "So you can talk to KAIRO. It only listens while the briefing is open." |
| Confirmation | Result in a few words | "Done. Your plan is updated." + *Undo* |

## Command hints
`R.array.command_hints` rotates on the Today command bar every 4 s. Keep exactly three, each a command
the offline parser understands, so trying the hint always works.
