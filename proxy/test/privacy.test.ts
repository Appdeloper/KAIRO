import { describe, expect, it } from "vitest";
import { stripPii } from "../src/privacy";
import { validateParseRequest } from "../src/validate";
import { context, TOKEN } from "./helpers";

describe("stripPii", () => {
  it("removes emails and phone numbers in common Indian formats", () => {
    expect(stripPii("mail rohan.k@gmail.com about the logo")).toBe("mail [email] about the logo");
    expect(stripPii("call +91 98765 43210 at 6")).toBe("call [phone] at 6");
    expect(stripPii("call 9876543210 kal")).toBe("call [phone] kal");
    expect(stripPii("office 022-2654-1234")).toBe("office [phone]");
    expect(stripPii("(080) 4567 8901 pe ring kar")).toBe("[phone] pe ring kar");
  });

  it("keeps times, durations, short numbers and ISO dates", () => {
    const text = "reel 12 edit 18:30 pe, 2 ghante, submit by 2026-10-07, room 204";
    expect(stripPii(text)).toBe(text);
  });

  it("is applied to text and every context string before validation returns", () => {
    const result = validateParseRequest({
      deviceToken: TOKEN,
      text: "send invoice to a@b.co and 9876543210",
      context: {
        ...context,
        firstName: "Aarav",
        items: [{ title: "Call 98765 43210", role: "x@y.in", day: "today", startMinute: 600, endMinute: 630, kind: "task" }],
      },
    });
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.text).toBe("send invoice to [email] and [phone]");
    expect(result.value.context.items[0].title).toBe("Call [phone]");
    expect(result.value.context.items[0].role).toBe("[email]");
  });
});
