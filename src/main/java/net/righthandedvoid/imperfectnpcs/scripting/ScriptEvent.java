package net.righthandedvoid.imperfectnpcs.scripting;

import java.util.Map;

/**
 * An event sent to a script, in a language-neutral form. Each language
 * binding converts it into its own types, so the Lua handler for `interact`
 * gets a table with fields like `event.npc` and `event.player`.
 *
 * Values in `data` may be null, Strings, Numbers, Booleans, ScriptNPCs, or
 * nested Maps and Lists of those.
 *
 * @param name The event's name, which is also the name of the handler that receives it.
 * @param data The event's fields.
 */
public record ScriptEvent(String name, Map<String, Object> data) {
}