export interface Env {
  /** Set with `wrangler secret put ANTHROPIC_API_KEY`. Never in code, config or git. */
  ANTHROPIC_API_KEY: string;
  /** Comma- or newline-separated device tokens allowed to call this Worker. A secret. */
  DEVICE_TOKENS: string;
  /** Per-device daily counters. */
  QUOTA: KVNamespace;
  /** IANA zone that decides when "today" rolls over for quotas. */
  QUOTA_TZ?: string;
  PARSE_DAILY_LIMIT?: string;
  BRIEF_DAILY_LIMIT?: string;
}
