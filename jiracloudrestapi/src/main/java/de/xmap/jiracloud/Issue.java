package de.xmap.jiracloud;

import java.util.List;
import java.util.Set;

// JSON for Issue access
public class Issue {
    /** ticket number */
    public String key;
    public Fields fields;
    
    public static class Fields {
        public HasName issuetype;
        public HasName status;
        public HasName resolution;
        public String summary;
        public Set<String> labels;
        public List<FixVersion> fixVersions;
        public List<Subtask> subtasks;
    }

    public static class HasName {
        public String name;
    }

    public static class Subtask {
        public String key;
        public SubtaskFields fields;
    }
    
    public static class SubtaskFields {
        public HasName issuetype;
    }

    public static class FixVersion extends HasName {
        public String description;
        public boolean archived;
        public boolean released;
    }
    
    // JSON for Project Versions access
    public static class Version extends HasName {
        public String id;
        public boolean archived;
        public boolean released;
    }

    // JSON for Project access
    public static class Project {
        public String id;
        public String key;
    }
}
