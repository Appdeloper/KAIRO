import type Anthropic from "@anthropic-ai/sdk";
import { BRIEF_SYSTEM } from "./prompt";
import type { BriefRequest, FreeSlot } from "./validate";

export const BRIEF_MODEL = "claude-sonnet-5-5";
export const MAX_BRIEF_WORDS = 120;

export interface Brief {
  greeting: string;
  summary: string;
  bestGap: { startMinute: number; endMinute: number; suggestion: string } | null;
  ifThenPlans: string[];
}

const BRIEF_SCHEMA = {
  type: "object",
  properties: {
    greeting: { type: "string" },
    summary: { type: "string" },
    bestGap: {
      anyOf: [
        {
          type: "object",
          properties: { startMinute: { type: "integer" }, endMinute: { type: "integer" }, suggestion: { type: "string" } },
          required: ["startMinute", "endMinute", "suggestion"],
          additionalProperties: false,
        },
        { type: "null" },
      ],
    },
    ifThenPlans: { type: "array", items: { type: "string" } },
  },
  required: ["greeting", "summary", "bestGap", "ifThenPlans"],
  additionalProperties: false,
} as const;

export class BriefUnavailable extends Error {}

export function buildBriefParams(req: BriefRequest): Anthropic.Beta.MessageCreateParamsNonStreaming {
  return {
    model: BRIEF_MODEL,
    max_tokens: 4096,
    // A short spoken brief needs little deliberation; low effort keeps latency and cost down.
    output_config: { effort: "low", format: { type: "json_schema", schema: BRIEF_SCHEMA } },
    // If a safety classifier declines, retry server-side on Anthropic's recommended model.
    betas: ["server-side-fallback-2026-07-01"],
    fallbacks: "default",
    system: [{ type: "text", text: BRIEF_SYSTEM, cache_control: { type: "ephemeral" } }],
    messages: [{ role: "user", content: `<context>${JSON.stringify(req.context)}</context>` }],
  };
}

export const countWords = (s: string) => s.split(/\s+/).filter((w) => w.length > 0).length;

function briefWords(b: Brief): number {
  return [b.greeting, b.summary, b.bestGap?.suggestion ?? "", ...b.ifThenPlans].reduce((n, s) => n + countWords(s), 0);
}

/**
 * The model's text is never trusted as-is: the gap must be one the device's scheduler actually
 * found, and the word limit is enforced by dropping trailing plans rather than cutting sentences.
 */
export function sanitizeBrief(raw: unknown, freeSlots: FreeSlot[]): Brief {
  const b = raw as Brief;
  if (typeof b?.greeting !== "string" || typeof b.summary !== "string" || !Array.isArray(b.ifThenPlans)) {
    throw new BriefUnavailable("malformed brief");
  }
  const gap = b.bestGap;
  const gapIsReal = gap != null && freeSlots.some((s) => gap.startMinute >= s.startMinute && gap.endMinute <= s.endMinute && gap.endMinute > gap.startMinute);
  const brief: Brief = {
    greeting: b.greeting.trim(),
    summary: b.summary.trim(),
    bestGap: gapIsReal ? { startMinute: gap.startMinute, endMinute: gap.endMinute, suggestion: gap.suggestion.trim() } : null,
    ifThenPlans: b.ifThenPlans.filter((p) => typeof p === "string").map((p) => p.trim()).slice(0, 3),
  };
  while (briefWords(brief) > MAX_BRIEF_WORDS && brief.ifThenPlans.length > 0) brief.ifThenPlans.pop();
  if (briefWords(brief) > MAX_BRIEF_WORDS) throw new BriefUnavailable("brief too long");
  return brief;
}

export async function runBrief(client: Pick<Anthropic, "beta">, req: BriefRequest): Promise<Brief> {
  const message = await client.beta.messages.create(buildBriefParams(req));
  if (message.stop_reason === "refusal" || message.stop_reason === "max_tokens") throw new BriefUnavailable(String(message.stop_reason));
  const text = message.content.filter((b) => b.type === "text").map((b) => (b as { text: string }).text).join("");
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new BriefUnavailable("brief was not JSON");
  }
  return sanitizeBrief(parsed, req.context.freeSlots);
}
