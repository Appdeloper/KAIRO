import type Anthropic from "@anthropic-ai/sdk";
import type { Deps } from "../src/index";
import type { Env } from "../src/env";

/** In-memory stand-in for a Workers KV namespace (only what the quota code uses). */
export class MemoryKV {
  readonly store = new Map<string, string>();
  async get(key: string) {
    return this.store.get(key) ?? null;
  }
  async put(key: string, value: string) {
    this.store.set(key, value);
  }
}

export const TOKEN = "device-token-123456";

export function env(kv = new MemoryKV()): Env {
  return {
    ANTHROPIC_API_KEY: "test-key-not-real",
    DEVICE_TOKENS: `other-token-000000, ${TOKEN}`,
    QUOTA: kv as unknown as KVNamespace,
    QUOTA_TZ: "Asia/Kolkata",
  };
}

export const context = {
  firstName: "Aarav",
  localDate: "2026-10-05",
  localTime: "09:00",
  roles: ["College", "Intern", "Client", "Content"],
  items: [
    { title: "DBMS lecture", role: "College", day: "today", startMinute: 900, endMinute: 960, kind: "lecture" },
    { title: "Gym", role: null, day: "today", startMinute: 1080, endMinute: 1140, kind: "task" },
  ],
  freeSlots: [{ startMinute: 610, endMinute: 890 }],
};

export function parseBody(text: string, extra: Record<string, unknown> = {}) {
  return { deviceToken: TOKEN, text, context, ...extra };
}

export function post(path: string, body: unknown): Request {
  return new Request(`https://kairo.example${path}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function toolMessage(calls: Array<{ name: string; input: unknown }>, stop: Anthropic.StopReason = "tool_use"): Anthropic.Message {
  return {
    id: "msg_test",
    type: "message",
    role: "assistant",
    model: "claude-haiku-4-5",
    stop_reason: stop,
    stop_sequence: null,
    content: calls.map((c, i) => ({ type: "tool_use", id: `toolu_${i}`, name: c.name, input: c.input, caller: { type: "direct" } })),
    usage: { input_tokens: 1, output_tokens: 1 },
  } as unknown as Anthropic.Message;
}

/** Records every request the handler sends upstream so tests can inspect what the model would see. */
export function fakeDeps(reply: () => unknown, now = new Date("2026-10-05T03:30:00Z")) {
  const sent: unknown[] = [];
  const create = async (params: unknown) => {
    sent.push(params);
    return reply();
  };
  const deps: Deps = {
    anthropic: () => ({ messages: { create }, beta: { messages: { create } } }) as unknown as Anthropic,
    now: () => now,
  };
  return { deps, sent };
}
