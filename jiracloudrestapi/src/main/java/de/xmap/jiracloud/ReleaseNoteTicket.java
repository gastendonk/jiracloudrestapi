package de.xmap.jiracloud;

import java.util.List;
import java.util.Map;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;

/**
 * RNT: release note title, RNS: release note summary, RND: release note details
 */
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
    
    public String getRNT_de() {
        return issue.text("/fields/customfield_10077");
    }
    public String getRNT_en() {
        return issue.text("/fields/customfield_10078");
    }
    
    public String getRNS_de() {
        return issue.doc("/fields/customfield_10079");
    }
    public String getRNS_en() {
        return issue.doc("/fields/customfield_10080");
    }
    
    public String getRND_de() {
        return issue.doc("/fields/customfield_10081");
    }
    public String getRND_en() {
        return issue.doc("/fields/customfield_10082");
    }
    
    public Map<String, byte[]> getRND_de_images() {
        return issue.images("/fields/customfield_10081");
    }
    public Map<String, byte[]> getRND_en_images() {
        return issue.images("/fields/customfield_10082");
    }
}
