const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;
// Digit runs with common phone separators. Colons are excluded so times like 18:30 survive.
const PHONE_CANDIDATE = /\+?\(?\d[\d\s().-]{6,}\d/g;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const MIN_PHONE_DIGITS = 8;

/**
 * Removes phone numbers and email addresses before anything reaches the model (CLAUDE.md rule 8).
 * Short numbers ("2 ghante", "reel 12", "18:30") and ISO dates are left alone.
 */
export function stripPii(text: string): string {
  return text.replace(EMAIL, "[email]").replace(PHONE_CANDIDATE, (match) => {
    const trimmed = match.trim();
    if (ISO_DATE.test(trimmed)) return match;
    const digits = trimmed.replace(/\D/g, "").length;
    return digits >= MIN_PHONE_DIGITS ? "[phone]" : match;
  });
}
