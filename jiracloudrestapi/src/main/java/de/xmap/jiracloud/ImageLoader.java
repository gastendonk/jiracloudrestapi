package de.xmap.jiracloud;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

public class ImageLoader {

    public ImagesResult images(String html, JiraCloudAccess jira) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        for (Element macro : doc.select("span.jira-issue-macro[data-jira-key]")) {
            String jiraKey = macro.attr("data-jira-key");
            macro.replaceWith(new TextNode(jiraKey));
        }
        Map<String, byte[]> images = new HashMap<>();
        for (Element element : doc.select("img[src]")) {
            String src = element.attr("src");
            if (src.isBlank()) {
                continue;
            }
            byte[] image = images.get(src);
            if (image == null) {
                image = jira.loadImage(src);
            }
            images.put(src, image);
        }
        return new ImagesResult(doc.body().html(), images);
    }

    public Function<String, String> getEditImgSrc() {
        return src -> src;
    }
}
