package de.xmap.jiracloud;

import java.util.List;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;

public class ReleaseNoteTicket {
    private final IssueAccess issue;
    private String releaseFor_issueType;
    private String sort;
    private String customerTicketNumber;
    
    public static List<ReleaseNoteTicket> load(JiraCloudAccess jira, String pageId) {
        String jql = "issuetype=\"Release note ticket\" AND \"Release notes page Ids[Labels]\" in (\"" + pageId + "\")";
        return jira.loadIssues(jql, "&maxResults=500", issue -> new ReleaseNoteTicket(issue));
    }
    
    public ReleaseNoteTicket(IssueAccess issue) {
        this.issue = issue;
    }

    public IssueAccess getIssueAccess() {
        return issue;
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
     * @return linked issue of type "release for" (ticket number)
     */
    public String getReleaseFor() {
        List<String> ret = issue.getLinkedOutwardIssue("release for");
        return ret.isEmpty() ? null : ret.get(0);
    }

    public String getReleaseFor_issueType() {
        return releaseFor_issueType;
    }

    public void setReleaseFor_issueType(String v) {
        releaseFor_issueType = v;
    }

    public String getCustomerTicketNumber() {
        return customerTicketNumber == null ? getKey() : customerTicketNumber;
    }

    public void setCustomerTicketNumber(String customerTicketNumber) {
        this.customerTicketNumber = customerTicketNumber;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    /**
     * <h1>customerTicketNumber</h1>
     * <p>For the selected customer, the 'Customer project key' ticket number from the "release for" linked ticket may be used.
     * Otherwise the RNT number.</p>
     * 
     * <h1>releaseFor_issueType</h1>
     * <p>Determines issue type of getReleaseFor() ticket, e.g. "Bug". null if issue type could not be determined.
     * Access it using getReleaseFor_issueType()</p>
     * 
     * @param project null if project should not be compared
     * @param jira -
     */
    public void loadCustomerTicketNumberAndType(String project, JiraCloudAccess jira) {
        customerTicketNumber = null;
        releaseFor_issueType = null;
        String releaseFor = getReleaseFor(); // Ticketnr. des über "release for" verknüpfte Ticket
        if (releaseFor == null || releaseFor.isBlank()) {
            return;
        }
        List<IssueAccess> issues;
        try {
            issues = jira.loadIssues("key=\"" + releaseFor + "\"", i -> i);
        } catch (Exception e) {
            return;
        }
        if (issues.size() == 1) {
            IssueAccess issue = issues.get(0);
            try {
                if (project == null || project.equals(issue.text("/fields/project/key"))) { // Project übereinstimmend?
                    customerTicketNumber = issue.textne("/fields/customfield_10048"); // Customer project key
                }
                releaseFor_issueType = issue.textne("/fields/issuetype/name");
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * @param key ticket number, e.g. ABC-9, ABC-123
     * @return e.g. ABC-000009, ABC-000123
     */
    public static String makeSortKey(String key) {
        if (key == null || !key.contains("-")) {
            return key;
        }
        int o = key.indexOf("-") + 1;
        String number = key.substring(o);
        while (number.length() < 6) {
            number = "0" + number;
        }
        return key.substring(0, o) + number;
    }
}
