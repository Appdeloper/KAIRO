export type Route = "parse" | "brief";

export const DEFAULT_LIMITS: Record<Route, number> = { parse: 50, brief: 3 };

export const QUOTA_MESSAGES: Record<Route, string> = {
  parse: "Aaj ka AI quota khatam ho gaya 🙏 Kal phir milte hain. Tab tak offline mode se kaam chal raha hai.",
  brief: "Aaj ke AI briefings ho gaye. Kal subah fresh briefing milegi ✨",
};

/** Two days, so yesterday's counter is still readable around the roll-over and then disappears. */
const COUNTER_TTL_SECONDS = 2 * 24 * 60 * 60;

/** Calendar day in the configured zone, so the quota resets at local midnight rather than UTC. */
export function quotaDay(now: Date, timeZone: string): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
}

export interface QuotaResult {
  allowed: boolean;
  used: number;
  limit: number;
}

/**
 * Counts one request against the device's daily limit. KV is eventually consistent, so two
 * simultaneous requests can both slip through near the limit; acceptable for a cost guard.
 */
export async function consumeQuota(
  kv: KVNamespace,
  deviceHash: string,
  route: Route,
  limit: number,
  now: Date,
  timeZone: string,
): Promise<QuotaResult> {
  const key = `quota:${route}:${quotaDay(now, timeZone)}:${deviceHash}`;
  const used = Number.parseInt((await kv.get(key)) ?? "0", 10) || 0;
  if (used >= limit) return { allowed: false, used, limit };
  await kv.put(key, String(used + 1), { expirationTtl: COUNTER_TTL_SECONDS });
  return { allowed: true, used: used + 1, limit };
}

export function limitFor(route: Route, configured: string | undefined): number {
  const n = Number.parseInt(configured ?? "", 10);
  return Number.isInteger(n) && n > 0 ? n : DEFAULT_LIMITS[route];
}
