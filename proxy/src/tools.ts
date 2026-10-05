import type Anthropic from "@anthropic-ai/sdk";

/*
 * One tool per app Command. Strict schemas: every property required, nullable via anyOf, no extra
 * keys. The app validates every value again; the model never touches the database.
 */

const nullable = (schema: Record<string, unknown>) => ({ anyOf: [schema, { type: "null" }] });

const DATE = nullable({
  type: "string",
  description: '"today", "tomorrow", or an ISO date yyyy-MM-dd. null means today.',
});
const MINUTE = (what: string) => nullable({ type: "integer", description: `${what}, in minutes after local midnight (0-1439).` });

const ADD_TASK_SCHEMA: Anthropic.Tool.InputSchema = {
  type: "object",
  properties: {
    title: { type: "string", description: "Short task title in the user's words, without the time or date." },
    date: DATE,
    start_minute: MINUTE("Start time only if the user said one; otherwise null so the app's scheduler picks a slot"),
    duration_minutes: nullable({ type: "integer", description: "Only if the user said how long. Otherwise null." }),
    role: nullable({ type: "string", description: "One of the role names from context, or null if unsure." }),
    deadline: nullable({ type: "string", description: '"today", "tomorrow" or yyyy-MM-dd if the user gave a due date, else null.' }),
    priority: nullable({ type: "integer", enum: [1, 2, 3, 4], description: "1 = urgent ... 4 = someday. null unless the user signalled urgency." }),
  },
  required: ["title", "date", "start_minute", "duration_minutes", "role", "deadline", "priority"],
  additionalProperties: false,
};

const TARGET = { type: "string", description: "The item's title exactly as it appears in context, or the user's words if it isn't listed." };

export const TOOLS: Anthropic.Tool[] = [
  {
    name: "add_task",
    description: "Add one new task the user explicitly asked for.",
    strict: true,
    input_schema: ADD_TASK_SCHEMA,
  },
  {
    name: "move_block",
    description: "Move an existing TASK to another time or day. Never use this for lectures.",
    strict: true,
    input_schema: {
      type: "object",
      properties: { target: TARGET, date: DATE, start_minute: MINUTE("New start time") },
      required: ["target", "date", "start_minute"],
      additionalProperties: false,
    },
  },
  {
    name: "skip_block",
    description: "Skip a task or a lecture for one day (the user is not doing it that day).",
    strict: true,
    input_schema: {
      type: "object",
      properties: { target: TARGET, date: DATE },
      required: ["target", "date"],
      additionalProperties: false,
    },
  },
  {
    name: "complete_task",
    description: "Mark an existing task as done.",
    strict: true,
    input_schema: {
      type: "object",
      properties: { target: TARGET },
      required: ["target"],
      additionalProperties: false,
    },
  },
  {
    name: "query_day",
    description: "The user wants to hear their plan. next_only = true for 'what's next' style questions.",
    strict: true,
    input_schema: {
      type: "object",
      properties: { date: DATE, next_only: { type: "boolean" } },
      required: ["date", "next_only"],
      additionalProperties: false,
    },
  },
  {
    name: "brain_dump",
    description: "The user listed three or more new tasks at once. One entry per task they said.",
    strict: true,
    input_schema: {
      type: "object",
      properties: { tasks: { type: "array", items: ADD_TASK_SCHEMA } },
      required: ["tasks"],
      additionalProperties: false,
    },
  },
  {
    name: "unclear",
    description: "Use when the input is not a planner command, is too ambiguous to act on, or asks to move a lecture.",
    strict: true,
    input_schema: {
      type: "object",
      properties: { reason: { type: "string", enum: ["not_a_planner_command", "ambiguous", "lecture_fixed"] } },
      required: ["reason"],
      additionalProperties: false,
    },
  },
];

export const TOOL_NAMES = new Set(TOOLS.map((t) => t.name));
