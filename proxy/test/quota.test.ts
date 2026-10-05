import { describe, expect, it } from "vitest";
import { handle } from "../src/index";
import { consumeQuota, quotaDay } from "../src/quota";
import { env, fakeDeps, MemoryKV, parseBody, post, toolMessage } from "./helpers";

describe("quota", () => {
  it("rolls over at local midnight in the configured zone", () => {
    // 19:00 UTC on the 5th is already 00:30 on the 6th in India.
    expect(quotaDay(new Date("2026-10-05T19:00:00Z"), "Asia/Kolkata")).toBe("2026-10-06");
    expect(quotaDay(new Date("2026-10-05T19:00:00Z"), "UTC")).toBe("2026-10-05");
  });

  it("counts per device and per route", async () => {
    const kv = new MemoryKV() as unknown as KVNamespace;
    const now = new Date("2026-10-05T05:00:00Z");
    for (let i = 0; i < 3; i++) expect((await consumeQuota(kv, "dev-a", "brief", 3, now, "UTC")).allowed).toBe(true);
    expect((await consumeQuota(kv, "dev-a", "brief", 3, now, "UTC")).allowed).toBe(false);
    expect((await consumeQuota(kv, "dev-b", "brief", 3, now, "UTC")).allowed).toBe(true);
    expect((await consumeQuota(kv, "dev-a", "parse", 50, now, "UTC")).allowed).toBe(true);
    expect((await consumeQuota(kv, "dev-a", "brief", 3, new Date("2026-10-06T05:00:00Z"), "UTC")).allowed).toBe(true);
  });

  it("returns 429 with a friendly Hinglish message after 50 parses, without calling the model", async () => {
    const e = env();
    const { deps, sent } = fakeDeps(() => toolMessage([{ name: "query_day", input: { date: "today", next_only: false } }]));
    for (let i = 0; i < 50; i++) expect((await handle(post("/parse", parseBody("aaj kya hai")), e, deps)).status).toBe(200);
    const res = await handle(post("/parse", parseBody("aaj kya hai")), e, deps);
    expect(res.status).toBe(429);
    const body = (await res.json()) as { error: string; message: string };
    expect(body.error).toBe("quota_exceeded");
    expect(body.message).toMatch(/quota khatam/);
    expect(sent).toHaveLength(50);
  });

  it("stores only a hash of the device token", async () => {
    const kv = new MemoryKV();
    const { deps } = fakeDeps(() => toolMessage([{ name: "unclear", input: { reason: "ambiguous" } }]));
    await handle(post("/parse", parseBody("hmm")), env(kv), deps);
    const keys = [...kv.store.keys()];
    expect(keys).toHaveLength(1);
    expect(keys[0]).not.toContain("device-token-123456");
  });
});
