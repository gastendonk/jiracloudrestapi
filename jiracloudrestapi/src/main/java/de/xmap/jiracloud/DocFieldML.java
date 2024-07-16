package de.xmap.jiracloud;

import java.util.HashMap;
import java.util.Map;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;
import kong.unirest.json.JSONPointerException;

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
     * @return DocField, null if it does not exist
     */
    public DocField get(String lang) {
        return map.get(lang);
    }
    
    public void set(String lang, DocField docField) {
    	try {
			docField.isPlainText(); // check if exist
			map.put(lang, docField);
		} catch (JSONPointerException e) {
		}
    }
}
