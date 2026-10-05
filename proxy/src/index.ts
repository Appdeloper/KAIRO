import Anthropic from "@anthropic-ai/sdk";
import { hashToken, isAuthorized } from "./auth";
import { BriefUnavailable, runBrief } from "./brief";
import type { Env } from "./env";
import { runParse } from "./parse";
import { consumeQuota, limitFor, QUOTA_MESSAGES, type Route } from "./quota";
import { validateBriefRequest, validateParseRequest, type Validated } from "./validate";

export interface Deps {
  anthropic(env: Env): Pick<Anthropic, "messages" | "beta">;
  now(): Date;
}

/** The phone gives up after 4 s, so the upstream call must finish (or fail) well before that. */
const PARSE_TIMEOUT_MS = 3_500;
const BRIEF_TIMEOUT_MS = 20_000;

export const defaultDeps: Deps = {
  anthropic: (env) =>
    new Anthropic({
      apiKey: env.ANTHROPIC_API_KEY,
      // No retries: the app falls back to its offline parser faster than a retry would finish.
      maxRetries: 0,
      timeout: PARSE_TIMEOUT_MS,
    }),
  now: () => new Date(),
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8" } });

/** Logs shape only. Request and response bodies are never logged (they contain the user's day). */
function logOutcome(route: string, status: number, startedAt: number) {
  console.log(JSON.stringify({ route, status, ms: Date.now() - startedAt }));
}

async function readJson(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    return undefined;
  }
}

async function guard<T extends { deviceToken: string }>(
  route: Route,
  request: Request,
  env: Env,
  deps: Deps,
  validate: (body: unknown) => Validated<T>,
): Promise<{ value: T } | { response: Response }> {
  const result = validate(await readJson(request));
  if (!result.ok) return { response: json(400, { error: "invalid_request", detail: result.error }) };
  if (!isAuthorized(result.value.deviceToken, env.DEVICE_TOKENS)) return { response: json(401, { error: "unauthorized" }) };
  const limit = limitFor(route, route === "parse" ? env.PARSE_DAILY_LIMIT : env.BRIEF_DAILY_LIMIT);
  const quota = await consumeQuota(env.QUOTA, await hashToken(result.value.deviceToken), route, limit, deps.now(), env.QUOTA_TZ ?? "Asia/Kolkata");
  if (!quota.allowed) return { response: json(429, { error: "quota_exceeded", message: QUOTA_MESSAGES[route] }) };
  return { value: result.value };
}

function upstreamError(e: unknown): Response {
  if (e instanceof BriefUnavailable) return json(502, { error: "brief_unavailable" });
  if (e instanceof Anthropic.APIConnectionTimeoutError) return json(504, { error: "upstream_timeout" });
  if (e instanceof Anthropic.RateLimitError) return json(503, { error: "upstream_busy" });
  if (e instanceof Anthropic.APIError) return json(502, { error: "upstream_error" });
  throw e;
}

export async function handle(request: Request, env: Env, deps: Deps): Promise<Response> {
  const route = new URL(request.url).pathname;
  if (route !== "/parse" && route !== "/brief") return json(404, { error: "not_found" });
  if (request.method !== "POST") return json(405, { error: "method_not_allowed" });

  try {
    if (route === "/parse") {
      const g = await guard("parse", request, env, deps, validateParseRequest);
      if ("response" in g) return g.response;
      return json(200, await runParse(deps.anthropic(env), g.value));
    }
    const g = await guard("brief", request, env, deps, validateBriefRequest);
    if ("response" in g) return g.response;
    const client = deps.anthropic(env) as Anthropic;
    const briefClient = typeof client.withOptions === "function" ? client.withOptions({ timeout: BRIEF_TIMEOUT_MS }) : client;
    return json(200, await runBrief(briefClient, g.value));
  } catch (e) {
    return upstreamError(e);
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const startedAt = Date.now();
    const response = await handle(request, env, defaultDeps).catch(() => json(500, { error: "internal" }));
    logOutcome(new URL(request.url).pathname, response.status, startedAt);
    return response;
  },
} satisfies ExportedHandler<Env>;
