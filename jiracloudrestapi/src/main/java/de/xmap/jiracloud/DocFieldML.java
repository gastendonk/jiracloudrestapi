package de.xmap.jiracloud;

import java.util.HashMap;
import java.util.Map;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;

/**
 * Multi Language DocField
 */
public class DocFieldML {
    private final Map<String, DocField> map = new HashMap<>();

    public DocFieldML() {
    }

    public DocFieldML(IssueAccess issue, String path_de, String path_en) {
        set("de", new DocField(path_de, issue));
        set("en", new DocField(path_en, issue));
    }

    /**
     * @param lang "de", "en"
     * @return DocField
     */
    public DocField get(String lang) {
        return map.get(lang);
    }
    
    public void set(String lang, DocField docField) {
        map.put(lang, docField);
    }
}
