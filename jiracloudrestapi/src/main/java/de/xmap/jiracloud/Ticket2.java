package de.xmap.jiracloud;

import java.util.List;
import java.util.Set;

public class Ticket2 {
    public String key;
    public String issueType;
    public String resolution;
    public String status;
    public String summary;
    public List<String> fixVersions;
    public List<Subticket> subtasks;
    public Set<String> labels;
    
    public static class Subticket {
        public String issueKey;
        public String issueType;
    }
}
