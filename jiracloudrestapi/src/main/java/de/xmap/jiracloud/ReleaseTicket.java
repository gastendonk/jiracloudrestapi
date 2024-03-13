package de.xmap.jiracloud;

import java.util.List;

import de.xmap.jiracloud.JiraCloudAccess.Issue;

public class ReleaseTicket {
    private final Issue issue;
    
    public static List<ReleaseTicket> load(JiraCloudAccess jira, String project) {
        String jql = "issuetype=\"Release\" and project=\"" + project + "\"";
        return jira.loadIssues(jql, issue -> new ReleaseTicket(issue));
    }
    
    public ReleaseTicket(Issue issue) {
        this.issue = issue;
    }

    public String getKey() {
        return issue.text("/key");
    }
    
    public String getPageId() {
        return issue.text("/fields/customfield_10084");
    }
    
    public String getTargetVersion() {
        return issue.text("/fields/customfield_10101/name");
    }
    
    public boolean isRelevant() {
        String pageId = getPageId();
        return getTargetVersion() != null && pageId != null
                && pageId.length() == "2024-03-11T15:13:11.3+0000".length() && pageId.contains("T");
    }
}
