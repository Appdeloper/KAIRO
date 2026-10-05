import { stripPii } from "./privacy";

export type ItemKind = "lecture" | "task";

export interface ContextItem {
  title: string;
  role: string | null;
  day: "today" | "tomorrow";
  startMinute: number | null;
  endMinute: number | null;
  kind: ItemKind;
}

export interface FreeSlot {
  startMinute: number;
  endMinute: number;
}

/** What the app tells us about the user's day. Names only: the app resolves targets itself. */
export interface PlannerContext {
  firstName: string;
  localDate: string;
  localTime: string;
  roles: string[];
  items: ContextItem[];
  /** Only /brief uses this; computed on the device by the deterministic scheduler. */
  freeSlots: FreeSlot[];
}

export interface ParseRequest {
  deviceToken: string;
  text: string;
  context: PlannerContext;
}

export interface BriefRequest {
  deviceToken: string;
  context: PlannerContext;
}

export type Validated<T> = { ok: true; value: T } | { ok: false; error: string };

const LIMITS = { text: 500, title: 120, name: 40, roles: 12, items: 60, slots: 24, token: 200 } as const;

const isObject = (v: unknown): v is Record<string, unknown> => typeof v === "object" && v !== null && !Array.isArray(v);
const isMinute = (v: unknown): v is number => Number.isInteger(v) && (v as number) >= 0 && (v as number) <= 1439;
const isShortString = (v: unknown, max: number): v is string => typeof v === "string" && v.trim().length > 0 && v.length <= max;

class Invalid extends Error {}

function fail(message: string): never {
  throw new Invalid(message);
}

function readItem(raw: unknown, i: number): ContextItem {
  if (!isObject(raw)) fail(`context.items[${i}] must be an object`);
  if (!isShortString(raw.title, LIMITS.title)) fail(`context.items[${i}].title is invalid`);
  if (raw.role !== null && !isShortString(raw.role, LIMITS.name)) fail(`context.items[${i}].role is invalid`);
  if (raw.day !== "today" && raw.day !== "tomorrow") fail(`context.items[${i}].day must be today or tomorrow`);
  if (raw.kind !== "lecture" && raw.kind !== "task") fail(`context.items[${i}].kind must be lecture or task`);
  if (raw.startMinute !== null && !isMinute(raw.startMinute)) fail(`context.items[${i}].startMinute is out of range`);
  if (raw.endMinute !== null && !Number.isInteger(raw.endMinute)) fail(`context.items[${i}].endMinute is invalid`);
  return {
    title: stripPii(raw.title as string),
    role: raw.role === null ? null : stripPii(raw.role as string),
    day: raw.day,
    startMinute: raw.startMinute as number | null,
    endMinute: raw.endMinute as number | null,
    kind: raw.kind,
  };
}

function readSlot(raw: unknown, i: number): FreeSlot {
  if (!isObject(raw) || !isMinute(raw.startMinute) || !Number.isInteger(raw.endMinute) || (raw.endMinute as number) <= raw.startMinute) {
    fail(`context.freeSlots[${i}] is invalid`);
  }
  return { startMinute: raw.startMinute as number, endMinute: raw.endMinute as number };
}

function readContext(raw: unknown): PlannerContext {
  if (!isObject(raw)) fail("context must be an object");
  if (!isShortString(raw.firstName, LIMITS.name)) fail("context.firstName is invalid");
  if (typeof raw.localDate !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(raw.localDate)) fail("context.localDate must be yyyy-MM-dd");
  if (typeof raw.localTime !== "string" || !/^([01]\d|2[0-3]):[0-5]\d$/.test(raw.localTime)) fail("context.localTime must be HH:mm");
  if (!Array.isArray(raw.roles) || raw.roles.length > LIMITS.roles || !raw.roles.every((r) => isShortString(r, LIMITS.name))) {
    fail("context.roles is invalid");
  }
  if (!Array.isArray(raw.items) || raw.items.length > LIMITS.items) fail("context.items is invalid");
  const slots = raw.freeSlots ?? [];
  if (!Array.isArray(slots) || slots.length > LIMITS.slots) fail("context.freeSlots is invalid");
  return {
    // Privacy is enforced here too, so no code path can forward unstripped context.
    firstName: stripPii(raw.firstName as string),
    localDate: raw.localDate,
    localTime: raw.localTime,
    roles: (raw.roles as string[]).map(stripPii),
    items: raw.items.map(readItem),
    freeSlots: slots.map(readSlot),
  };
}

function readToken(raw: unknown): string {
  if (!isShortString(raw, LIMITS.token)) fail("deviceToken is missing");
  return raw as string;
}

function validated<T>(build: () => T): Validated<T> {
  try {
    return { ok: true, value: build() };
  } catch (e) {
    if (e instanceof Invalid) return { ok: false, error: e.message };
    throw e;
  }
}

export function validateParseRequest(body: unknown): Validated<ParseRequest> {
  return validated(() => {
    if (!isObject(body)) fail("body must be a JSON object");
    if (!isShortString(body.text, LIMITS.text)) fail(`text must be 1-${LIMITS.text} characters`);
    return { deviceToken: readToken(body.deviceToken), text: stripPii(body.text as string), context: readContext(body.context) };
  });
}

export function validateBriefRequest(body: unknown): Validated<BriefRequest> {
  return validated(() => {
    if (!isObject(body)) fail("body must be a JSON object");
    return { deviceToken: readToken(body.deviceToken), context: readContext(body.context) };
  });
}
