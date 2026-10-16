package de.xmap.jiracloud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

/** Converts CKEditor-style HTML into Atlassian Document Format (ADF) for Jira Cloud. */
public final class HtmlToAdfConverter {
    private static final Pattern HEADING = Pattern.compile("h([1-6])");
    private static final Pattern CSS_COLOR = Pattern.compile("(?:^|;)\\s*color\\s*:\\s*(#[0-9a-fA-F]{3,8})\\s*(?:;|$)");

    private HtmlToAdfConverter() {
    }

    /**
     * HTML containing tables or images must be edited in Jira, not Minerva.
     * Checks elements rather than searching raw HTML text.
     */
    public static boolean containsUnsupportedHtml(String html) {
        return html != null && !html.isBlank()
                && !Jsoup.parseBodyFragment(html).body().select("table, img").isEmpty();
    }

    /** Throws IllegalArgumentException instead of silently losing a table or image. */
    public static JSONObject convertToAdf(String html) {
        JSONArray content = new JSONArray();
        if (html != null && !html.isBlank()) {
            Element body = Jsoup.parseBodyFragment(html).body();
            if (!body.select("table, img").isEmpty()) {
                throw new IllegalArgumentException("HTML enthält Tabellen oder Bilder; Bearbeitung nur in Jira möglich.");
            }
            addBlocks(body, content);
        }
        return new JSONObject().put("version", 1).put("type", "doc").put("content", content);
    }

    private static void addBlocks(Element parent, JSONArray result) {
        JSONArray pending = new JSONArray();
        for (Node child : parent.childNodes()) {
            if (child instanceof Element e && isBlock(e.normalName())) {
                flushParagraph(pending, result);
                pending = new JSONArray();
                addBlock(e, result);
            } else {
                addInline(child, pending, List.of());
            }
        }
        flushParagraph(pending, result);
    }

    private static void addBlock(Element e, JSONArray result) {
        String tag = e.normalName();
        Matcher heading = HEADING.matcher(tag);
        if (heading.matches()) {
            JSONArray content = inlineChildren(e);
            JSONObject h = block("heading", content);
            h.put("attrs", new JSONObject().put("level", Integer.parseInt(heading.group(1))));
            result.put(h);
            return;
        }
        switch (tag) {
            case "p" -> result.put(block("paragraph", inlineChildren(e)));
            case "div", "section", "article", "main", "header", "footer", "figure", "figcaption" -> addBlocks(e, result);
            case "ul", "ol" -> result.put(convertList(e));
            case "blockquote" -> {
                JSONArray blocks = new JSONArray();
                addBlocks(e, blocks);
                if (blocks.isEmpty()) blocks.put(block("paragraph", new JSONArray()));
                result.put(block("blockquote", blocks));
            }
            case "pre" -> result.put(new JSONObject().put("type", "codeBlock")
                    .put("content", new JSONArray().put(text(e.wholeText(), List.of()))));
            case "hr" -> result.put(new JSONObject().put("type", "rule"));
            default -> addBlocks(e, result);
        }
    }

    private static JSONObject convertList(Element list) {
        boolean ordered = list.normalName().equals("ol");
        JSONObject node = block(ordered ? "orderedList" : "bulletList", new JSONArray());
        if (ordered) {
            String start = list.attr("start");
            try {
                int order = Integer.parseInt(start);
                if (order > 0 && order != 1) node.put("attrs", new JSONObject().put("order", order));
            } catch (NumberFormatException ignored) {
                // Default list start is 1.
            }
        }
        JSONArray items = node.getJSONArray("content");
        for (Element child : list.children()) {
            if (!child.normalName().equals("li")) continue;
            JSONArray blocks = new JSONArray();
            addBlocks(child, blocks);
            if (blocks.isEmpty()) blocks.put(block("paragraph", new JSONArray()));
            // A listItem starts with a paragraph (or another permitted block).
            items.put(block("listItem", blocks));
        }
        return node;
    }

    private static JSONArray inlineChildren(Element parent) {
        JSONArray result = new JSONArray();
        for (Node child : parent.childNodes()) addInline(child, result, List.of());
        return result;
    }

    private static void addInline(Node node, JSONArray target, List<JSONObject> marks) {
        if (node instanceof TextNode t) {
            String value = t.getWholeText();
            if (!value.isEmpty()) target.put(text(value, marks));
            return;
        }
        if (!(node instanceof Element e)) return;
        String tag = e.normalName();
        if (tag.equals("script") || tag.equals("style") || tag.equals("svg")) return;
        if (tag.equals("br")) {
            target.put(new JSONObject().put("type", "hardBreak"));
            return;
        }
        if (isBlock(tag)) {
            // Invalid nested block inside inline HTML: preserve its text.
            for (Node child : e.childNodes()) addInline(child, target, marks);
            return;
        }
        List<JSONObject> nextMarks = new ArrayList<>(marks);
        JSONObject mark = markFor(e);
        if (mark != null) nextMarks.add(mark);
        addStyleMarks(e, nextMarks);
        for (Node child : e.childNodes()) addInline(child, target, nextMarks);
    }

    private static JSONObject markFor(Element e) {
        return switch (e.normalName()) {
            case "strong", "b" -> new JSONObject().put("type", "strong");
            case "em", "i" -> new JSONObject().put("type", "em");
            case "u" -> new JSONObject().put("type", "underline");
            case "s", "strike", "del" -> new JSONObject().put("type", "strike");
            case "code" -> new JSONObject().put("type", "code");
            case "sub" -> new JSONObject().put("type", "subsup")
                    .put("attrs", new JSONObject().put("type", "sub"));
            case "sup" -> new JSONObject().put("type", "subsup")
                    .put("attrs", new JSONObject().put("type", "sup"));
            case "a" -> {
                String href = e.attr("href").trim();
                yield safeUrl(href) ? new JSONObject().put("type", "link")
                        .put("attrs", new JSONObject().put("href", href)) : null;
            }
            default -> null;
        };
    }

    private static void addStyleMarks(Element e, List<JSONObject> marks) {
        String style = e.attr("style").toLowerCase(Locale.ROOT);
        if (style.isBlank()) return;
        if (style.matches("(?s).*font-weight\\s*:\\s*(bold|[6-9]00).*")) addMark(marks, "strong");
        if (style.matches("(?s).*font-style\\s*:\\s*italic.*")) addMark(marks, "em");
        if (style.contains("underline")) addMark(marks, "underline");
        if (style.contains("line-through")) addMark(marks, "strike");
        Matcher color = CSS_COLOR.matcher(style);
        if (color.find()) {
            marks.add(new JSONObject().put("type", "textColor")
                    .put("attrs", new JSONObject().put("color", color.group(1))));
        }
    }

    private static void addMark(List<JSONObject> marks, String type) {
        if (marks.stream().noneMatch(m -> type.equals(m.getString("type")))) {
            marks.add(new JSONObject().put("type", type));
        }
    }

    private static boolean safeUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:");
    }

    private static JSONObject text(String value, List<JSONObject> marks) {
        JSONObject result = new JSONObject().put("type", "text").put("text", value);
        if (!marks.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (JSONObject mark : marks) arr.put(mark);
            result.put("marks", arr);
        }
        return result;
    }

    private static JSONObject block(String type, JSONArray content) {
        return new JSONObject().put("type", type).put("content", content);
    }

    private static void flushParagraph(JSONArray inline, JSONArray result) {
        if (!inline.isEmpty()) {
            // Ignore whitespace-only formatting between block elements.
            if (inline.length() == 1 && inline.getJSONObject(0).optString("type").equals("text")
                    && inline.getJSONObject(0).optString("text").isBlank()) return;
            result.put(block("paragraph", inline));
        }
    }

    private static boolean isBlock(String tag) {
        return HEADING.matcher(tag).matches() || switch (tag) {
            case "p", "div", "section", "article", "main", "header", "footer", "figure", "figcaption",
                 "ul", "ol", "blockquote", "pre", "hr" -> true;
            default -> false;
        };
    }
}
