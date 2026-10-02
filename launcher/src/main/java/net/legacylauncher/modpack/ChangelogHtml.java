package net.legacylauncher.modpack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the changelogs libraries hand out into HTML the Swing text pane can show.
 * <p>
 * Modrinth and FTB write Markdown, ATLauncher plain text, CurseForge HTML already. Only
 * the handful of Markdown constructs changelogs actually use are understood - headings,
 * lists, bold, italics, code and links - which is all a "what's new" list needs; the
 * rest comes through as text rather than as stray symbols.
 */
public final class ChangelogHtml {
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern BARE_URL = Pattern.compile("(?<![\"'=>])(https?://[^\\s<]+)");

    private ChangelogHtml() {
    }

    public static String fromPlainText(String text) {
        if (text == null) {
            return null;
        }
        return "<html><body>" + linkify(escape(text.trim())).replace("\r\n", "\n").replace("\n", "<br>")
                + "</body></html>";
    }

    public static String fromMarkdown(String markdown) {
        if (markdown == null) {
            return null;
        }
        StringBuilder html = new StringBuilder("<html><body>");
        boolean inList = false;
        StringBuilder paragraph = new StringBuilder();
        for (String raw : markdown.replace("\r\n", "\n").split("\n")) {
            String line = raw.trim();
            boolean quote = line.startsWith(">");
            if (quote) {
                line = line.replaceFirst("^>+\\s*", "");
            }
            boolean bullet = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ");
            if (line.isEmpty() || line.startsWith("#") || bullet || line.matches("-{3,}|\\*{3,}")) {
                flush(html, paragraph);
            }
            if (!bullet && inList && (line.isEmpty() || line.startsWith("#"))) {
                html.append("</ul>");
                inList = false;
            }
            if (line.isEmpty()) {
                continue;
            }
            if (line.matches("-{3,}|\\*{3,}")) {
                html.append("<hr>");
            } else if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                String heading = inline(line.substring(level).trim());
                if (level >= 3) {
                    // Swing draws h4 and below smaller than body text, which reads backwards
                    html.append("<p><b>").append(heading).append("</b></p>");
                } else {
                    html.append("<h").append(level + 1).append('>').append(heading)
                            .append("</h").append(level + 1).append('>');
                }
            } else if (bullet) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(inline(line.substring(2).trim())).append("</li>");
            } else {
                if (paragraph.length() > 0) {
                    paragraph.append(' ');
                }
                paragraph.append(quote ? "<i>" + inline(line) + "</i>" : inline(line));
            }
        }
        flush(html, paragraph);
        if (inList) {
            html.append("</ul>");
        }
        return html.append("</body></html>").toString();
    }

    /**
     * CurseForge's own HTML, wrapped so the text pane treats it as a document.
     */
    public static String fromHtml(String html) {
        if (html == null) {
            return null;
        }
        return html.toLowerCase().contains("<html") ? html : "<html><body>" + html + "</body></html>";
    }

    private static void flush(StringBuilder html, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            html.append("<p>").append(paragraph).append("</p>");
            paragraph.setLength(0);
        }
    }

    private static String inline(String text) {
        String result = escape(text);
        result = CODE.matcher(result).replaceAll("<code>$1</code>");
        Matcher link = LINK.matcher(result);
        StringBuffer linked = new StringBuffer();
        while (link.find()) {
            link.appendReplacement(linked, Matcher.quoteReplacement(
                    "<a href=\"" + link.group(2) + "\">" + link.group(1) + "</a>"));
        }
        link.appendTail(linked);
        result = linked.toString();
        Matcher bold = BOLD.matcher(result);
        StringBuffer bolded = new StringBuffer();
        while (bold.find()) {
            String inner = bold.group(1) != null ? bold.group(1) : bold.group(2);
            bold.appendReplacement(bolded, Matcher.quoteReplacement("<b>" + inner + "</b>"));
        }
        bold.appendTail(bolded);
        result = ITALIC.matcher(bolded.toString()).replaceAll("<i>$1</i>");
        return result.contains("<a ") ? result : linkify(result);
    }

    private static String linkify(String text) {
        return BARE_URL.matcher(text).replaceAll("<a href=\"$1\">$1</a>");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
