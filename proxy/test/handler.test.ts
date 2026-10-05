import Anthropic from "@anthropic-ai/sdk";
import { describe, expect, it } from "vitest";
import { handle } from "../src/index";
import { sanitizeBrief } from "../src/brief";
import { TOOLS } from "../src/tools";
import { context, env, fakeDeps, parseBody, post, toolMessage } from "./helpers";

describe("/parse", () => {
  it("rejects unknown device tokens before touching quota or the model", async () => {
    const { deps, sent } = fakeDeps(() => toolMessage([]));
    const res = await handle(post("/parse", { ...parseBody("x"), deviceToken: "not-a-real-token" }), env(), deps);
    expect(res.status).toBe(401);
    expect(sent).toHaveLength(0);
  });

  it("returns every tool call, e.g. two commands from one sentence", async () => {
    const calls = [
      { name: "add_task", input: { title: "Client call", date: "tomorrow", start_minute: 1080, duration_minutes: null, role: "Client", deadline: null, priority: null } },
      { name: "skip_block", input: { target: "Gym", date: "today" } },
    ];
    const { deps } = fakeDeps(() => toolMessage(calls));
    const res = await handle(post("/parse", parseBody("kal 6 baje client call add kar aur gym skip kar")), env(), deps);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ calls });
  });

  it("sends a cached static prefix, forced tool use, and only stripped text", async () => {
    const { deps, sent } = fakeDeps(() => toolMessage([{ name: "unclear", input: { reason: "ambiguous" } }]));
    await handle(post("/parse", parseBody("call rohan 9876543210 or rohan@mail.com")), env(), deps);
    const params = sent[0] as Anthropic.MessageCreateParams;
    expect(params.model).toBe("claude-haiku-4-5");
    expect(params.tool_choice).toEqual({ type: "any" });
    expect((params.system as Anthropic.TextBlockParam[])[0].cache_control).toEqual({ type: "ephemeral" });
    const serialized = JSON.stringify(params);
    expect(serialized).not.toContain("9876543210");
    expect(serialized).not.toContain("rohan@mail.com");
    expect(serialized).toContain("[phone]");
  });

  it("maps refusals, prose-only replies and foreign tools to unclear", async () => {
    for (const reply of [
      toolMessage([], "refusal"),
      toolMessage([]),
      toolMessage([{ name: "delete_everything", input: {} }]),
    ]) {
      const { deps } = fakeDeps(() => reply);
      const body = await (await handle(post("/parse", parseBody("x")), env(), deps)).json();
      expect(body).toEqual({ calls: [{ name: "unclear", input: { reason: "ambiguous" } }] });
    }
  });

  it("turns upstream failures into status codes the app can fall back on", async () => {
    const timeout = fakeDeps(() => {
      throw new Anthropic.APIConnectionTimeoutError();
    });
    expect((await handle(post("/parse", parseBody("x")), env(), timeout.deps)).status).toBe(504);
  });

  it("rejects malformed bodies and other routes", async () => {
    const { deps } = fakeDeps(() => toolMessage([]));
    expect((await handle(post("/parse", { text: 5 }), env(), deps)).status).toBe(400);
    expect((await handle(post("/admin", {}), env(), deps)).status).toBe(404);
    expect((await handle(new Request("https://kairo.example/parse"), env(), deps)).status).toBe(405);
  });
});

describe("tool schemas", () => {
  it("are strict, closed objects with every property required", () => {
    const check = (schema: Record<string, unknown>, path: string) => {
      if (schema.type === "object") {
        expect(schema.additionalProperties, path).toBe(false);
        expect(new Set(schema.required as string[]), path).toEqual(new Set(Object.keys(schema.properties as object)));
        for (const [k, v] of Object.entries(schema.properties as Record<string, Record<string, unknown>>)) check(v, `${path}.${k}`);
      }
      if (schema.type === "array") check(schema.items as Record<string, unknown>, `${path}[]`);
      for (const alt of (schema.anyOf as Record<string, unknown>[] | undefined) ?? []) check(alt, path);
    };
    expect(TOOLS.map((t) => t.name).sort()).toEqual(
      ["add_task", "brain_dump", "complete_task", "move_block", "query_day", "skip_block", "unclear"],
    );
    for (const tool of TOOLS) {
      expect(tool.strict, tool.name).toBe(true);
      check(tool.input_schema as unknown as Record<string, unknown>, tool.name);
    }
  });
});

describe("/brief", () => {
  const brief = {
    greeting: "Good morning, Aarav.",
    summary: "DBMS lecture at three, gym in the evening.",
    bestGap: { startMinute: 610, endMinute: 890, suggestion: "Knock out the DBMS assignment." },
    ifThenPlans: ["If the lecture runs late, then move gym by thirty minutes."],
  };
  const textMessage = (text: string) => ({ content: [{ type: "text", text }], stop_reason: "end_turn" });

  it("returns the validated brief and uses Sonnet with structured output and fallbacks", async () => {
    const { deps, sent } = fakeDeps(() => textMessage(JSON.stringify(brief)));
    const res = await handle(post("/brief", { deviceToken: "device-token-123456", context }), env(), deps);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual(brief);
    const params = sent[0] as Record<string, any>;
    expect(params.model).toBe("claude-sonnet-5-5");
    expect(params.output_config.format.type).toBe("json_schema");
    expect(params.fallbacks).toBe("default");
    expect(params.tool_choice).toBeUndefined();
  });

  it("drops a gap that isn't one of the device's free slots, and trims to 120 words", () => {
    const long = { ...brief, bestGap: { ...brief.bestGap, startMinute: 900 }, ifThenPlans: Array(3).fill("word ".repeat(45)) };
    const result = sanitizeBrief(long, context.freeSlots);
    expect(result.bestGap).toBeNull();
    expect(result.ifThenPlans.length).toBeLessThan(3);
  });

  it("allows 3 briefs a day", async () => {
    const e = env();
    const { deps } = fakeDeps(() => textMessage(JSON.stringify(brief)));
    for (let i = 0; i < 3; i++) expect((await handle(post("/brief", { deviceToken: "device-token-123456", context }), e, deps)).status).toBe(200);
    expect((await handle(post("/brief", { deviceToken: "device-token-123456", context }), e, deps)).status).toBe(429);
  });
});
