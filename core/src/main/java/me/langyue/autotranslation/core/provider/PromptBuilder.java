package me.langyue.autotranslation.core.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.langyue.autotranslation.core.lang.LanguageProfile;

import java.util.Map;

/**
 * Builds the instructions sent to a chat model. Bump {@link #VERSION} whenever the wording changes in a way
 * that can change results, so cached translations made with an older prompt are not reused.
 */
public final class PromptBuilder {
    public static final String VERSION = "p1";

    private PromptBuilder() {
    }

    public static String systemPrompt(LanguageProfile target, boolean strict) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a professional game localizer. Translate Minecraft mod text from English into ")
                .append(target.englishName()).append(" (language code ").append(target.providerCode()).append(").\n")
                .append("Rules:\n")
                .append("1. Translate only the meaning of the given segments. Never add, remove, explain or comment on anything.\n")
                .append("2. Tokens such as [[P0]], [[R1]] and [[/R1]] are protected markers. Copy every marker exactly once, unchanged, ")
                .append("to the matching place in the translation. Never translate, merge, renumber, reorder inside or drop a marker.\n")
                .append("3. Keep numbers, item and block ids, commands, URLs and proper names unchanged. Keep line breaks exactly.\n")
                .append("4. Write natural, fluent ").append(target.englishName())
                .append(" that sounds like official Minecraft localization. Use the glossary terms exactly as given, ")
                .append("and always use the same name for the same item or mechanic.\n")
                .append("5. The context fields only explain the situation. Never translate or output them.\n");
        if (target.isTurkish()) {
            sb.append("6. Address the player the way official Turkish Minecraft does (neutral imperative, for example ")
                    .append("\"Bir elmas topla\"), and keep Turkish suffixes attached with an apostrophe after proper names.\n");
        }
        sb.append("Answer with a single JSON object {\"translations\": [\"...\"]} that has exactly one string per input ")
                .append("segment, in the same order, and nothing else.");
        if (strict) {
            sb.append("\nYour previous answer was rejected because a marker, a number or the line structure was changed. ")
                    .append("Translate literally and copy every marker exactly.");
        }
        return sb.toString();
    }

    public static String userPrompt(ProviderRequest request) {
        TranslationContext c = request.context();
        JsonObject o = new JsonObject();
        o.addProperty("source_mod", c.sourceMod());
        o.addProperty("content_type", c.contentType());
        o.addProperty("title_context", c.titleContext());
        o.addProperty("context_before", c.contextBefore());
        o.addProperty("context_after", c.contextAfter());
        JsonObject glossary = new JsonObject();
        for (Map.Entry<String, String> e : c.glossary().entrySet()) {
            glossary.addProperty(e.getKey(), e.getValue());
        }
        o.add("glossary", glossary);
        JsonArray segments = new JsonArray();
        request.segments().forEach(segments::add);
        o.add("segments", segments);
        return o.toString();
    }
}
