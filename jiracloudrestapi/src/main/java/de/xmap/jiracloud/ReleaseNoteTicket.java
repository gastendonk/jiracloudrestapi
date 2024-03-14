package de.xmap.jiracloud;

import java.util.List;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;

public class ReleaseNoteTicket {
    private final IssueAccess issue;
    
    public static List<ReleaseNoteTicket> load(JiraCloudAccess jira, String pageId) {
        String jql = "issuetype=\"Release note ticket\" AND \"Release note page Ids[Labels]\" in (\"" + pageId + "\")";
        return jira.loadIssues(jql, issue -> new ReleaseNoteTicket(issue));
    }
    
    public ReleaseNoteTicket(IssueAccess issue) {
        this.issue = issue;
    }

    /**
     * @return ticket number
     */
    public String getKey() {
        return issue.text("/key");
    }

    /**
     * @param lang "de" or "en"
     * @return release note title
     */
    public String getRNT(String lang) {
        if ("de".equals(lang)) {
            return issue.text("/fields/customfield_10077");
        }
        return issue.text("/fields/customfield_10078");
    }

    /**
     * @return release note summary
     */
    public DocFieldML getRNS() {
        return new DocFieldML(issue, "/fields/customfield_10079", "/fields/customfield_10080");
    }

    /**
     * @return release note details
     */
    public DocFieldML getRND() {
        return new DocFieldML(issue, "/fields/customfield_10081", "/fields/customfield_10082");
    }
    
    /**
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
