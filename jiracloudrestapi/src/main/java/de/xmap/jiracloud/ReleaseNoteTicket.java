package de.xmap.jiracloud;

import java.util.List;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;

public class ReleaseNoteTicket {
    private final IssueAccess issue;
    
    public static List<ReleaseNoteTicket> load(JiraCloudAccess jira, String pageId) {
        String jql = "issuetype=\"Release note ticket\" AND \"Release notes page Ids[Labels]\" in (\"" + pageId + "\")";
        return jira.loadIssues(jql, "&maxResults=500", issue -> new ReleaseNoteTicket(issue));
    }
    
    public ReleaseNoteTicket(IssueAccess issue) {
        this.issue = issue;
    }

    /**
     * @return ticket number
     */
    public String getKey() {
        return issue.getKey();
    }
    
    public String getTitle() {
    	return issue.getTitle();
    }

    /**
     * @param lang "de" or "en"
     * @return release note title
     */
    public String getRNT(String lang) {
        if ("de".equals(lang)) {
            return issue.text("/fields/customfield_10055");
        }
        return issue.text("/fields/customfield_10056");
    }

    /**
     * @return release note summary
     */
    public DocFieldML getRNS() {
        return new DocFieldML(issue, "/fields/customfield_10057", "/fields/customfield_10058");
    }

    /**
     * @return release note details
     */
    public DocFieldML getRND() {
        return new DocFieldML(issue, "/fields/customfield_10059", "/fields/customfield_10060");
    }
    
    /**
     * Das eigentliche Ticket (i.d.R. vom Typ Story oder Bug)
     * @return linked issue of type "release for"
     */
    public String getReleaseFor() {
        List<String> ret = issue.getLinkedOutwardIssue("release for");
        return ret.isEmpty() ? null : ret.get(0);
    }

    public IssueAccess getIssueAccess() {
        return issue;
    }
}
