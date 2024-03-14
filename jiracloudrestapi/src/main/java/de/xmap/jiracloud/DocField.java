package de.xmap.jiracloud;

import java.util.Map;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;

public class DocField {
    private final String path;
    private final IssueAccess issue;

    public DocField(String path, IssueAccess issue) {
        this.path = path;
        this.issue = issue;
    }

    public boolean isPlainText() {
        return issue.isPlainText(path);
    }

    public String getText() {
        return issue.text(path);
    }

    public Map<String, byte[]> getImages() {
        return issue.images(path);
    }
}
