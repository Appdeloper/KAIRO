# KAIRO proxy (Cloudflare Worker)

The only place KAIRO talks to Claude. The Android app sends text and a minimal day summary here;
the Worker strips phone numbers and emails, checks the device token and daily quota, calls the
Anthropic Messages API, and returns tool calls. **The Anthropic API key lives only in this Worker's
secrets**, never in the app or in git (CLAUDE.md rule 5).

| Route | Model | Returns |
|---|---|---|
| `POST /parse` | `claude-haiku-4-5`, strict tools, forced tool call | `{"calls":[{"name":"add_task","input":{...}}, ...]}` |
| `POST /brief` | `claude-sonnet-5-5`, structured JSON output | `{"greeting","summary","bestGap","ifThenPlans":[]}` |

Status codes the app relies on: `400` invalid body, `401` unknown device token, `429` daily quota
used up (body has a Hinglish `message` to show), `502/503/504` upstream problems (the app falls
back to its offline parser).

Request and response bodies are never logged; logs contain only route, status and latency.

## One-time setup

```bash
cd proxy
npm ci                                   # installs wrangler, the Anthropic SDK and vitest
npx wrangler login                       # opens a browser to authorise your Cloudflare account

# 1. KV namespace for the per-device daily quotas.
npx wrangler kv namespace create QUOTA
#    Copy the printed id into wrangler.jsonc -> kv_namespaces[0].id

# 2. Secrets (each command prompts for the value; nothing is written to disk or git).
npx wrangler secret put ANTHROPIC_API_KEY    # your key from console.anthropic.com
npx wrangler secret put DEVICE_TOKENS        # e.g. a long random string per phone, comma-separated
#    Generate a token with:  openssl rand -hex 24

# 3. Deploy.
npx wrangler deploy
#    Prints your URL, e.g. https://kairo-proxy.<your-subdomain>.workers.dev
```

Quota limits and the time zone the quota day uses are plain vars in `wrangler.jsonc`
(`PARSE_DAILY_LIMIT` 50, `BRIEF_DAILY_LIMIT` 3, `QUOTA_TZ` Asia/Kolkata).

## Test it

```bash
URL=https://kairo-proxy.<your-subdomain>.workers.dev
TOKEN=<one of your DEVICE_TOKENS>

curl -s "$URL/parse" -H 'content-type: application/json' -d @- <<JSON
{
  "deviceToken": "$TOKEN",
  "text": "kal 6 baje client call add kar aur gym skip kar",
  "context": {
    "firstName": "Aarav",
    "localDate": "2026-10-05",
    "localTime": "09:00",
    "roles": ["College", "Intern", "Client", "Content"],
    "items": [
      {"title": "DBMS lecture", "role": "College", "day": "today", "startMinute": 900, "endMinute": 960, "kind": "lecture"},
      {"title": "Gym", "role": null, "day": "today", "startMinute": 1080, "endMinute": 1140, "kind": "task"}
    ]
  }
}
JSON
```

Expected shape (two calls):

```json
{"calls":[
  {"name":"add_task","input":{"title":"Client call","date":"tomorrow","start_minute":1080,"duration_minutes":null,"role":"Client","deadline":null,"priority":null}},
  {"name":"skip_block","input":{"target":"Gym","date":"today"}}
]}
```

Local development: put `ANTHROPIC_API_KEY=...` and `DEVICE_TOKENS=...` in `proxy/.dev.vars`
(gitignored) and run `npm run dev`; it serves on `http://localhost:8787`.

## Tests

```bash
npm test            # vitest: validation, quota, PII stripping, routing, schema strictness
npm run typecheck
```

The tests never call Anthropic; the client is faked.

## Notes

- **Prompt caching**: the static system prompt plus the tool definitions carry a `cache_control`
  breakpoint. Haiku 4.5 only caches prefixes of at least 4,096 tokens; this prefix is currently
  about 3,000, so requests run uncached until it grows. Check `usage.cache_read_input_tokens` if
  you extend the examples.
- `/parse` gives the model 3.5 s with no retries so the phone (4 s timeout) still gets an answer or
  a clean failure in time to fall back offline.
- Quotas use KV, which is eventually consistent: two requests in the same instant near the limit
  can both pass. That's acceptable for a cost guard.
- `.npmrc` sets `legacy-peer-deps` to avoid an npm 10.9 resolver crash with wrangler's peer
  dependency; `npm ci` with the committed lockfile is the reliable install.
