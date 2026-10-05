/** Constant-time string compare, so response timing doesn't reveal how much of a token matched. */
function timingSafeEqual(a: string, b: string): boolean {
  const enc = new TextEncoder();
  const x = enc.encode(a);
  const y = enc.encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

export function allowedTokens(secret: string | undefined): string[] {
  return (secret ?? "").split(/[\s,]+/).map((t) => t.trim()).filter((t) => t.length > 0);
}

export function isAuthorized(token: string, secret: string | undefined): boolean {
  // Check every entry (no early exit) to keep timing independent of the token's position.
  let ok = false;
  for (const allowed of allowedTokens(secret)) ok = timingSafeEqual(token, allowed) || ok;
  return ok;
}

/** KV keys use a hash, so the raw device token never sits in storage. */
export async function hashToken(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("").slice(0, 32);
}
