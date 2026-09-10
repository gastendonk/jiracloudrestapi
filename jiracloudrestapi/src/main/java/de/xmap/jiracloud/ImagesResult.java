package de.xmap.jiracloud;

import java.util.Map;

public record ImagesResult(String html, Map<String, byte[]> images) {
}
