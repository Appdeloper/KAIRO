import { describe, expect, it } from "vitest";
import { validateBriefRequest, validateParseRequest } from "../src/validate";
import { context, parseBody } from "./helpers";

describe("validateParseRequest", () => {
  it("accepts a well-formed request", () => {
    expect(validateParseRequest(parseBody("gym skip kar")).ok).toBe(true);
  });

  it.each([
    ["missing token", { ...parseBody("x"), deviceToken: undefined }],
    ["empty text", parseBody("   ")],
    ["text too long", parseBody("a".repeat(501))],
    ["not an object", "gym skip kar"],
    ["bad date", parseBody("x", { context: { ...context, localDate: "05/10/2026" } })],
    ["bad time", parseBody("x", { context: { ...context, localTime: "25:00" } })],
    ["minute out of range", parseBody("x", { context: { ...context, items: [{ ...context.items[0], startMinute: 1440 }] } })],
    ["unknown kind", parseBody("x", { context: { ...context, items: [{ ...context.items[0], kind: "meeting" }] } })],
    ["ids are not accepted as a day", parseBody("x", { context: { ...context, items: [{ ...context.items[0], day: 3 }] } })],
    ["too many items", parseBody("x", { context: { ...context, items: Array(61).fill(context.items[0]) } })],
  ])("rejects %s", (_, body) => {
    expect(validateParseRequest(body).ok).toBe(false);
  });

  it("brief requests need valid free slots", () => {
    expect(validateBriefRequest({ deviceToken: "device-token-123456", context }).ok).toBe(true);
    const bad = { ...context, freeSlots: [{ startMinute: 700, endMinute: 600 }] };
    expect(validateBriefRequest({ deviceToken: "device-token-123456", context: bad }).ok).toBe(false);
  });
});
