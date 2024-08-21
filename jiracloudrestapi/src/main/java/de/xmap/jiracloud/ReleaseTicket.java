package de.xmap.jiracloud;

import java.util.List;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;

public class ReleaseTicket {
    private final IssueAccess issue;
    
    public static List<ReleaseTicket> load(JiraCloudAccess jira, String project) {
		String jql;
		if (project == null || project.isBlank()) {
			jql = "issuetype=\"Release\"";
		} else {
			jql = "issuetype=\"Release\" and project=\"" + project + "\"";
		}
        return jira.loadAllIssues(jql, "", issue -> new ReleaseTicket(issue));
    }
    
    public ReleaseTicket(IssueAccess issue) {
        this.issue = issue;
    }

    public String getKey() {
        return issue.getKey();
    }

    public String getTitle() {
    	return issue.getTitle();
    }
    
    /**
     * @return Main page ID
     */
    public String getPageId() {
        return issue.text("/fields/customfield_10065");
    }

    /**
     * @return 'Main page ID (EN)' is unused
     */
    public String getPageIdEN() {
        return issue.text("/fields/customfield_10066"); 
    }

    /**
     * @return target release version
     */
    public String getTargetVersion() {
        return issue.text("/fields/customfield_10073/name");
    }
    
    public boolean isRelevant() {
        String pageId = getPageId();
        return getTargetVersion() != null && pageId != null
                && pageId.length() == "2024-03-11T15:13:11.3+0000".length() && pageId.contains("T");
    }
}
