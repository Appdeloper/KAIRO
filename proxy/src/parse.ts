import type Anthropic from "@anthropic-ai/sdk";
import { PARSE_SYSTEM } from "./prompt";
import { TOOL_NAMES, TOOLS } from "./tools";
import type { ParseRequest } from "./validate";

export const PARSE_MODEL = "claude-haiku-4-5";

export interface ToolCall {
  name: string;
  input: unknown;
}

export interface ParseResponse {
  calls: ToolCall[];
}

const UNCLEAR: ToolCall = { name: "unclear", input: { reason: "ambiguous" } };

export function buildParseParams(req: ParseRequest): Anthropic.MessageCreateParamsNonStreaming {
  return {
    model: PARSE_MODEL,
    max_tokens: 1024,
    // Tools and system are static; the breakpoint caches both. Haiku 4.5 needs a prefix of at
    // least 4096 tokens before a cache entry is written, so short prefixes simply run uncached.
    system: [{ type: "text", text: PARSE_SYSTEM, cache_control: { type: "ephemeral" } }],
    tools: TOOLS,
    // Haiku 4.5 still supports forcing a tool call; prose replies are never useful here.
    tool_choice: { type: "any" },
    messages: [
      {
        role: "user",
        // Volatile data goes after the cached prefix.
        content: `<context>${JSON.stringify(req.context)}</context>\n<request>${req.text}</request>`,
      },
    ],
  };
}

/** Keeps only our own tools; anything else (or nothing) is reported as unclear, never guessed. */
export function extractCalls(message: Anthropic.Message): ParseResponse {
  if (message.stop_reason === "refusal" || message.stop_reason === "max_tokens") return { calls: [UNCLEAR] };
  const calls = message.content
    .filter((b): b is Anthropic.ToolUseBlock => b.type === "tool_use")
    .filter((b) => TOOL_NAMES.has(b.name))
    .map((b) => ({ name: b.name, input: b.input }));
  return { calls: calls.length > 0 ? calls : [UNCLEAR] };
}

export async function runParse(client: Pick<Anthropic, "messages">, req: ParseRequest): Promise<ParseResponse> {
  const message = await client.messages.create(buildParseParams(req));
  return extractCalls(message);
}
