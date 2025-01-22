package de.xmap.jiracloud;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.xmap.jiracloud.Issue.FixVersion;
import de.xmap.jiracloud.Issue.Project;
import de.xmap.jiracloud.Issue.Subtask;
import de.xmap.jiracloud.Issue.Version;
import de.xmap.jiracloud.PageTitles.PageTitle;
import de.xmap.jiracloud.Pages.Page;
import de.xmap.jiracloud.Ticket2.Subticket;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

public class JiraCloudAccess {
	public static String cf_developmentDescription = "customfield_10053";
	public static String cf_rnt_de = "customfield_10055";
	public static String cf_rnt_en = "customfield_10056";
	public static String cf_rns_de = "customfield_10057";
	public static String cf_rns_en = "customfield_10058";
	public static String cf_rnd_de = "customfield_10059";
	public static String cf_rnd_en = "customfield_10060";
	public static String cf_featuresID = "10140";
	public static String cf_features = "customfield_" + cf_featuresID;
	public static String cf_changeNotesTitle = "customfield_10173";
	public static String cf_changeNotesDescription = "customfield_10174";
	/** context ID */
	public static final String CID = "10287";

	private final String url;
    private final String auth;
    private boolean debugMode = false;
    private boolean showAccess = false;
	
    /**
     * @param mail mail address for Jira Cloud login
     * @param token token for Jira Cloud login
     * @param customer Jira Cloud customer
     */
    public JiraCloudAccess(String mail, String token, String customer) {
        if (customer == null || customer.isBlank()) {
            throw new IllegalArgumentException("Please specify customer");
        } else if (mail == null || !mail.contains("@")) {
            throw new IllegalArgumentException("Please specify mail");
        } else if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Please specify token");
        }
        url = "https://" + customer + ".atlassian.net";
        auth = "Basic " + Base64.getEncoder().encodeToString((mail + ":" + token).getBytes());
    }
    
    /**
     * @deprecated use loadAllIssues() because it supports paging
     */
    public <T> List<T> loadIssues(String jql, Function<IssueAccess, T> creator) {
        return loadIssues(jql, "", creator);
    }

    /**
     * @deprecated use loadAllIssues() because it supports paging
     */
    public List<IssueAccess> loadIssues(String jql, String queryExtension) {
        return loadIssues(jql, queryExtension, i -> i);
    }

    /**
     * @param <T> -
     * @param jql -
     * @param queryExtension don't specify startAt nd maxResults !
     * @param creator -
     * @return issues
     */
    public <T> List<T> loadAllIssues(String jql, String queryExtension, Function<IssueAccess, T> creator) {
        List<T> ret = new ArrayList<>();
        Issues<T> issues = _loadIssues(jql, queryExtension + "&startAt=0&maxResults=100" /* more than 100 are not possible */, creator);
        ret.addAll(issues.list);
        while (ret.size() < issues.total) {
            ret.addAll(_loadIssues(jql, queryExtension + "&startAt=" + ret.size() /* 0 based */ + "&maxResults=100", creator).list);
        }
        return ret;
    }

    /**
     * @deprecated use loadAllIssues() because it supports paging
     */
    public <T> List<T> loadIssues(String jql, String queryExtension, Function<IssueAccess, T> creator) {
        return _loadIssues(jql, queryExtension, creator).list;
    }
    
    private <T> Issues<T> _loadIssues(String jql, String queryExtension, Function<IssueAccess, T> creator) {
    	Issues<T> ret = new Issues<T>();
    	long start = System.currentTimeMillis();
        HttpResponse<JsonNode> response = get("/rest/api/3/search?jql=" + urlEncode(jql, "") + queryExtension);
        if (response.getStatus() >= 300) {
            throw new RuntimeException("Error loading issues. Status is " + response.getStatus());
        }
        JsonNode json = response.getBody();
        if (debugMode) {
            System.out.println(json.toPrettyString());
        }
        ret.total = json.getObject().getInt("total");
        for (Object issue0 : json.getObject().getJSONArray("issues")) {
            ret.list.add(creator.apply(new IssueAccess((JSONObject) issue0)));
        }

        if (showAccess) {
			long end = System.currentTimeMillis();
			System.err.println("\t\t_loadIssues: " + (end - start) + "ms | " + jql + " | " + queryExtension + " | "
					+ ret.list.size() + " | " + ret.total);
        }
        return ret;
    }
    
    private static class Issues<T> {
    	final List<T> list = new ArrayList<T>();
    	int total;
    }
    
    public List<Changelog> loadHistory(String key) {
        List<Changelog> ret = new ArrayList<>(), list;
        do {
            list = _loadHistory(key, "&startAt=" + ret.size() /* 0 based */ + "&maxResults=100");
            ret.addAll(list);
        } while (!list.isEmpty());
        return ret;
    }
    
    private List<Changelog> _loadHistory(String key, String queryExtension) {
        List<Changelog> ret = new ArrayList<>();
        HttpResponse<JsonNode> response = get("/rest/api/3/issue/" + key + "/changelog" + queryExtension);
        if (response.getStatus() >= 300) {
            throw new RuntimeException("Error loading issues. Status is " + response.getStatus());
        }
        JsonNode json = response.getBody();
        if (debugMode) {
            System.out.println(json.toPrettyString());
        }
        for (Object i : json.getObject().getJSONArray("values")) {
            for (Object j : ((JSONObject) i).getJSONArray("items")) {
                JSONObject d = (JSONObject) j;
                ret.add(new Changelog((String) d.query("/field"), (String) d.query("/fromString"),
                        (String) d.query("/toString")));
            }
        }
        return ret;
    }
    
    /**
     * @param path -
     * @return JSON
     */
    public HttpResponse<JsonNode> get(String path) {
        if (debugMode) {
            System.out.println(url + path);
        }
        return Unirest.get(url + path).header("Accept", "application/json").header("Authorization", auth).asJson();
    }

    public static String urlEncode(String text, String fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return fallback;
        }
    }

    public byte[] loadImage(String src) {
        HttpResponse<byte[]> response = Unirest.get(url + src).header("Authorization", auth).asBytes();
        if (response.getStatus() >= 300) {
            throw new RuntimeException("Error loading image. Status is " + response.getStatus());
        }
        byte[] ret = response.getBody();
		if (showAccess) {
        	System.err.println("\t\tloadImage " + src + ", " + ret.length);
        }
        return ret;
    }

    public class IssueAccess {
        private final JSONObject jo;
        
        public IssueAccess(JSONObject issue) {
            jo = issue;
        }
        
        /**
         * @return ticket number
         */
        public String getKey() {
            return text("/key");
        }

        /**
         * @return ticket title (aka summary)
         */
        public String getTitle() {
        	return text("/fields/summary");
        }
        
        /**
         * @return e.g. "In Progress"
         */
        public String getStatus() {
            return text("/fields/status/name");
        }
        
        /**
         * @return ticket type, e.g. Story
         */
        public String getType() {
            return text("/fields/issuetype/name");
        }
        
        /**
         * @return e.g. "2024-09-25T15:03:45.400+0200"
         */
        public String getCreated() {
        	return text("/fields/created");
        }

        /**
         * DateTime of last change of ticket
         * @return e.g. "2024-11-18T09:47:42.978+0100"
         */
        public String getUpdated() {
        	return text("/fields/updated");
        }

        /**
         * @return name of person who creates the issue
         */
        public String getReporter() {
            return text("/fields/reporter/displayName");
        }
        
        public String getChangeNotesTitle() {
        	return text("/fields/" + cf_changeNotesTitle);
        }
        
        public TreeSet<String> getLabels() {
            return array("fields", "labels", "$i");
        }

        public TreeSet<String> getFeatures() {
            return array("fields", cf_features, "/value");
        }

        public TreeSet<String> getFixVersions() {
            return array("fields", "fixVersions", "/name");
        }

        public TreeSet<String> array(String fields, String name, String field) {
            TreeSet<String> ret = new TreeSet<>();
            JSONArray array = jo.getJSONObject(fields).optJSONArray(name);
            if (array != null) { // is null if field is empty
                if ("$i".equals(field)) {
                    for (int i = 0; i < array.length(); i++) {
                        ret.add(array.getString(i));
                    }
                } else {
                    for (Object entry : array) {
                        ret.add((String) ((JSONObject) entry).query(field));
                    }
                }
            }
            return ret;
        }

        /**
         * @param path plain text field
         * @return plain text
         */
        public String text(String path) {
            try {
                return (String) jo.query(path);
            } catch (Exception e) {
                return null;
            }
        }
        
        /**
         * @param path plain text field
         * @return same as text() but returns null instead of ""
         */
        public String textne(String path) {
            String ret = text(path);
            return ret != null && ret.isBlank() ? null : ret;
        }
        
        /**
         * @param path content field
         * @return plain text or HTML
         */
        public String doc(String path) {
            if (isPlainText(path)) {
                return (String) jo.query(path + "/content/0/content/0/text");
            }

            int o = path.lastIndexOf("/");
            String fieldname = path.substring(o + 1);

            String jql = "issue=\"" + text("/key") + "\"";
            List<IssueAccess> r = loadAllIssues(jql, "&expand=renderedFields&fields=" + fieldname, i -> i);
            if (r.size() != 1) {
                throw new RuntimeException("Expected 1 item for JQL " + jql);
            }
            return r.get(0).text(path.replace("fields", "renderedFields"));
        }
        
        /**
         * @param path content field
         * @return true if plain text, false if HTML
         */
        public boolean isPlainText(String path) {
            JSONArray contents = (JSONArray) jo.query(path + "/content");
            if (contents.length() == 1) {
            	String pp = path + "/content/0/";
            	JSONArray subContents = (JSONArray) jo.query(pp + "content");
        		String type = (String) jo.query(pp + "type");
            	return subContents.length() == 1 && "paragraph".equals(type);
            }
            return false;
        }
        
        /**
         * @param path content field
         * @return Map(key=src, value=image data)
         */
        public Map<String, byte[]> images(String path) {
            Map<String, byte[]> images = new HashMap<>();
            String html = doc(path);
            int o = html.indexOf("<img");
            while (o >= 0) {
                int oo = html.indexOf(">", o);
                if (oo >= 0) {
                    int ooo = html.indexOf("src=\"", o);
                    if (ooo >= 0 && ooo < oo) {
                        ooo += "src=\"".length();
                        int oooo = html.indexOf("\"", ooo);
                        if (oooo > ooo) {
                            String src = html.substring(ooo, oooo);
                            byte[] img = loadImage(src);
                            if (images == null) {
                                images = new HashMap<>();
                            }
                            images.put(src, img);
                        }
                    }
                }
                o = html.indexOf("<img", oo);
            }
            return images;
        }
        
        /**
         * @param outwardType e.g. "release for"
         * @return ticket numbers
         */
        public List<String> getLinkedOutwardIssue(String outwardType) {
			return getLinkedIssue(outwardType, "/fields/issuelinks", "/type/outward", "/outwardIssue/key");
        }
        
        /**
         * @param outwardType e.g. "release for"
         * @return ticket numbers
         */
		public List<String> getLinkedInwardIssue(String outwardType) {
			return getLinkedIssue(outwardType, "/fields/issuelinks", "/type/outward", "/inwardIssue/key");
		}

		protected List<String> getLinkedIssue(String outwardType, String expr1, String expr2, String expr3) {
			List<String> ret = new ArrayList<>();
			for (Object i : (JSONArray) jo.query(expr1)) {
				JSONObject o = (JSONObject) i;
				if (outwardType.equals(o.query(expr2))) {
					try {
						ret.add(((String) o.query(expr3)));
					} catch (Exception ignore) {
					}
				}
			}
			return ret;
		}

        public JSONObject getJSONObject() {
            return jo;
        }

		JiraCloudAccess jira() {
			return JiraCloudAccess.this;
		}
    }
    
    // =================================================
    
    /**
     * @return 0: success, 1: no fixversions field
     */
    public int setFixVersions(String ticketNr, Set<String> fixVersions) {
        String fixversionJSON = "";
        for (String fv : fixVersions) {
            if (!fixversionJSON.isEmpty()) {
                fixversionJSON += ",";
            }
            fixversionJSON += """
                    {
                        \"description\": \"automated entry\",
                        \"name\": \"$fv\",
                        \"archived\": false,
                        \"released\": false
                    }
                    """.replace("$fv", fv);
        }
        String body = """
                {
                \"fields\": {
                    \"fixVersions\": [
                        $a
                    ]
                }
            }
            """.replace("$a", fixversionJSON);
        HttpResponse<JsonNode> response = Unirest.put(url + "/rest/api/3/issue/" + ticketNr) //
                .header("Accept", "application/json") //
                .header("Content-type", "application/json") //
                .header("Authorization", auth) //
                .body(body)
                .asJson();
        String res = response.getBody().toString();
        if (res.contains("Version name") && res.contains("is not valid")) {
            throw new JiraVersionNotExistException();
        } else if (res.contains("It is not on the appropriate screen, or unknown.")) { // XDEV-4579
            // Fix version cannot be written to Jira ticket, because ticket has no fix versions field.
            // Example: This can happen if you commit with XDEV- and then move the ticket to XIS.
            // ('A.1' FixVersionAction record will be written.)
            // Continue with execution!
            return 1; // write Action "A.1" instead of "A"
        } else if (response.getStatus() >= 300) {
            System.err.println(body);
            System.err.println(response.getBody().toPrettyString());
            throw new RuntimeException("Status is " + response.getStatus() + ". See log.");
        }
        return 0; // success
    }
    
    public List<String> getFixVersions(String ticketNr) {
        if (ticketNr == null || !ticketNr.contains("-")) {
            throw new IllegalArgumentException();
        }
        List<String> ret = new ArrayList<>();
        HttpResponse<JsonNode> response = Unirest.get(url + "/rest/api/3/issue/" + ticketNr) //
                .header("Accept", "application/json") //
                .header("Authorization", auth) //
                .asJson();
        if (response.getStatus() >= 300) {
            throw new RuntimeException("Status is " + response.getStatus());
        }
        Issue ticket = new Gson().fromJson(response.getBody().toString(), Issue.class);
        for (FixVersion fv : ticket.fields.fixVersions) {
            ret.add(fv.name);
        }
        return ret;
    }
    
    /**
     * @param ticketNr or project key + "-"
     * @param version -
     * @return true if added successfully, false if version number already exists
     */
    public boolean createVersion(String ticketNr, String version) {
        String description = "created by fixversionapp " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); // wish by Kevin to have date
        String projectId = getProjectId(ticketNr);
        String body = """
                {
                    \"archived\": false,
                    \"description\": \"$d\",
                    \"name\": \"$n\",
                    \"projectId\": $p,
                    \"released\": false
                }
                """
                .replace("$d", description)
                .replace("$n", version)
                .replace("$p", projectId);
        HttpResponse<JsonNode> response = Unirest.post(url + "/rest/api/3/version") //
                .header("Accept", "application/json") //
                .header("Content-type", "application/json") //
                .header("Authorization", auth) //
                .body(body)
                .asJson();
        if (response.getStatus() >= 300) {
            String msg = response.getBody().toPrettyString();
            if (msg.contains("already exists")) {
                System.out.println(msg);
                return false;
            } else {
                System.err.println(msg);
                throw new RuntimeException("Status is " + response.getStatus() + ". See log.");
            }
        }        
        return true;
    }
    
    public String getProjectId(String ticketNr) {
        int o = ticketNr.indexOf("-");
        String p = ticketNr.substring(0, o);
        HttpResponse<JsonNode> response = Unirest.get(url + "/rest/api/3/project/" + p) //
                .header("Accept", "application/json") //
                .header("Authorization", auth) //
                .asJson();
        if (response.getStatus() >= 300) {
            System.err.println(response.getBody().toPrettyString());
            throw new RuntimeException("Status is " + response.getStatus());
        }
        Project project = new Gson().fromJson(response.getBody().toString(), Project.class);
        return project.id;
    }
    
    public Ticket2 loadTicket(String ticketNr) {
        String u = url + "/rest/api/3/issue/" + ticketNr;
        HttpResponse<JsonNode> response = Unirest.get(u) //
                .header("Accept", "application/json") //
                .header("Authorization", auth) //
                .asJson();
        if (response.getStatus() >= 300) {
            System.err.println("Error loading ticket using " + u);
            System.err.println(response.getBody().toPrettyString());
            throw new RuntimeException("Can not load Jira ticket. Status is " + response.getStatus());
        }
        Issue ticket = new Gson().fromJson(response.getBody().toString(), Issue.class);

        Ticket2 ret = new Ticket2();
        ret.key = ticket.key;
        ret.issueType = ticket.fields.issuetype.name;
        ret.summary = ticket.fields.summary;
        ret.status = ticket.fields.status.name;
        ret.resolution = ticket.fields.resolution == null ? "" : ticket.fields.resolution.name;
        ret.labels = ticket.fields.labels;
        ret.fixVersions = ticket.fields.fixVersions.stream().map(i -> i.name).collect(Collectors.toList());
        ret.subtasks = new ArrayList<>();
        if (ticket.fields.subtasks != null) {
            for (Subtask subtask : ticket.fields.subtasks) {
                Subticket s = new Subticket();
                s.issueKey = subtask.key;
                s.issueType = subtask.fields.issuetype.name;
                ret.subtasks.add(s);
            }
        }
        return ret;
    }

    // called by JiraVersions
    public List<Version> getProjectVersions(String project) {
        HttpResponse<JsonNode> response = Unirest.get(url + "/rest/api/3/project/" + project + "/versions") //
                .header("Accept", "application/json") //
                .header("Authorization", auth) //
                .asJson();
        if (response.getStatus() >= 300) {
            System.err.println(response.getBody().toPrettyString());
            throw new RuntimeException("Status is " + response.getStatus() + ". See log.");
        }
        java.lang.reflect.Type type = new TypeToken<ArrayList<Version>>() {}.getType();
        return new Gson().fromJson(response.getBody().toString(), type);
    }
    
    // called by JiraVersions
    public void setProjectVersionReleased(String versionId) {
        HttpResponse<JsonNode> response = Unirest.put(url + "/rest/api/3/version/" + versionId) //
                .header("Content-type", "application/json") //
                .header("Accept", "application/json") //
                .header("Authorization", auth) //
                .body("{\"released\":true}") //
                .asJson();
        if (response.getStatus() >= 300) {
            System.err.println(response.getBody().toPrettyString());
            throw new RuntimeException("Status is " + response.getStatus() + ". See log.");
        }
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    public boolean isShowAccess() {
		return showAccess;
	}

	public void setShowAccess(boolean showAccess) {
		this.showAccess = showAccess;
	}

	public List<FieldOption> loadFieldOptions(String customFieldID, String contextID) {
		return loadFieldOptions(customFieldID, contextID, true);
	}

	public List<FieldOption> loadFieldOptions(String customFieldID, String contextID, boolean sort) {
        List<FieldOption> ret = new ArrayList<>(), list;
        do {
            list = _loadFieldOptions(customFieldID, contextID, "?startAt=" + ret.size());
            ret.addAll(list);
        } while (!list.isEmpty());
        if (sort) {
        	ret.sort((a, b) -> a.getValue().compareTo(b.getValue()));
        }
        return ret;
    }

    private List<FieldOption> _loadFieldOptions(String customFieldID, String contextID, String queryExtension) {
        List<FieldOption> ret = new ArrayList<>();
        HttpResponse<JsonNode> response = get("/rest/api/3/field/customfield_" + customFieldID + "/context/" + contextID + "/option" + queryExtension);
        if (response.getStatus() >= 300) {
            throw new RuntimeException("Error loading field options. Status is " + response.getStatus());
        }
        JsonNode json = response.getBody();
        if (debugMode) {
            System.out.println(json.toPrettyString());
        }
        for (Object i0 : json.getObject().getJSONArray("values")) {
            JSONObject i = (JSONObject) i0; 
            FieldOption o = new FieldOption();
            o.setId(i.getString("id"));
            o.setValue(i.getString("value"));
            o.setDisabled(i.getBoolean("disabled"));
            ret.add(o);
        }
        return ret;
    }
    
    public void createFieldOptions(String customFieldID, String contextID, List<String> options) {
        String m = url + "/rest/api/3/field/customfield_" + customFieldID + "/context/" + contextID + "/option";
        for (int i = 0; i < options.size(); i += 1000) { // paging with size 1000
            int to = i + 1000;
            if (to > options.size()) {
                to = options.size();
            }
            String body = "{\"options\": [" + options.subList(i, to).stream()
                    .map(o -> "{\"value\":\"" + o.replace("\"", "\\\"") + "\"}").collect(Collectors.joining(","))
                    + "]}";
            HttpResponse<JsonNode> response = Unirest.post(m) //
                    .header("Accept", "application/json") //
                    .header("Content-type", "application/json") //
                    .header("Authorization", auth) //
                    .body(body) //
                    .asJson();
            if (response.getStatus() >= 300) {
                throw new RuntimeException("Error creating field options. Status is " + response.getStatus() + " (" + i + " to " + to + ")");
            }
        }
    }
    
    public void deleteFieldOption(String customFieldID, String contextID, String id) {
        String m = url + "/rest/api/3/field/customfield_" + customFieldID + "/context/" + contextID + "/option/" + id;
        HttpResponse<JsonNode> response = Unirest.delete(m) //
                .header("Accept", "application/json") //
                .header("Content-type", "application/json") //
                .header("Authorization", auth) //
                .asJson();
        if (response.getStatus() >= 300) {
            throw new RuntimeException("Error deleting field option " + id + ". Status is " + response.getStatus());
        }
    }
    
    public void disableFieldOptions(String customFieldID, String contextID, List<String> idList, boolean disabled) {
        String m = url + "/rest/api/3/field/customfield_" + customFieldID + "/context/" + contextID + "/option";
        String body = "{\"options\":[" //
                + idList.stream().map(i -> "{\"disabled\":" + disabled + ",\"id\":\"" + i + "\"}")
                        .collect(Collectors.joining(",")) //
                + "]}";
        HttpResponse<JsonNode> response = Unirest.put(m) //
                .header("Accept", "application/json") //
                .header("Content-type", "application/json") //
                .header("Authorization", auth) //
                .body(body) //
                .asJson();
        if (response.getStatus() >= 300) {
			throw new RuntimeException("Error " + (disabled ? "disabling" : "enabling") + " field options. Status is "
					+ response.getStatus());
        }
    }

    /**
     * Reorder field options
     * @param customFieldID e.g. cf_featuresID
     * @param contextID e.g. CID
     * @param idList not empty, in target order
     * @param actionFieldName "after" or "position" (without quotes)
     * @param actionValue if after: option ID; if position: "First" or "Last"
     */
    public void moveFieldOptions(String customFieldID, String contextID, List<String> idList, String actionFieldName, String actionValue) {
    	String m = url + "/rest/api/3/field/customfield_" + customFieldID + "/context/" + contextID + "/option/move";
    	String body = "{ \"customFieldOptionIds\": [\n"
    		+ idList.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",\n"))
    		+ "], \"" + actionFieldName + "\": \"" + actionValue + "\" }";
    	if (debugMode) {
    		System.out.println(m + "\n" + body);
    	}
    	HttpResponse<JsonNode> response = Unirest.put(m) //
                .header("Accept", "application/json") //
		        .header("Content-type", "application/json") //
		        .header("Authorization", auth) //
		        .body(body) //
		        .asJson();
    	if (response.getStatus() >= 300) {
    		throw new RuntimeException("Error moving field options. Status is " + response.getStatus());
    	}
    }
    
    /**
     * @param ticketNr e.g. "XDEV-4711"
     * @param featureNumbers set containing existing and new feature numbers, not null
     */
    public void saveFeatureNumbers(String ticketNr, Set<String> featureNumbers) {
        String json = featureNumbers.stream().map(fn -> "{\"value\": \"" + fn + "\"}").collect(Collectors.joining(", "));
        String body = "{\"fields\": { \"" + cf_features + "\": [" + json + "]}}";
        HttpResponse<JsonNode> response = Unirest.put(url + "/rest/api/3/issue/" + ticketNr) //
                .header("Accept", "application/json") //
                .header("Content-type", "application/json") //
                .header("Authorization", auth) //
                .body(body)
                .asJson();
        if (response.getStatus() >= 300) {
            String m = response.getBody().toPrettyString();
            if (m.contains("Issue does not exist")) {
                throw new RuntimeException("Issue does not exist: " + ticketNr);
            } else if (m.contains("cannot be set")) {
                throw new RuntimeException("Features field does not exist!");
            }
            System.err.println(body);
            System.err.println(response.getBody().toPrettyString());
            throw new RuntimeException("Can not save feature numbers. Status is " + response.getStatus() + ". See log.");
        }
    }

    /**
     * expensive
     * @return all Confluence pages
     */
    public PageTitles loadAllConfluencePages() {
        PageTitles ret = new PageTitles();
        ret.setPageTitles(new ArrayList<>());
        String path = "/wiki/api/v2/pages";
        do {
            HttpResponse<JsonNode> response = get(path);
            if (response.getStatus() >= 300) {
                throw new RuntimeException("Error loading Confluence pages. Status is " + response.getStatus());
            }
            Pages pages = new Gson().fromJson(response.getBody().toString(), Pages.class);
            pages.getResults().stream().forEach(page -> {
                PageTitle p = new PageTitle();
                p.setId(page.getId());
                p.setTiny(page.get_links().getTinyui());
                p.setTitle(page.getTitle());
                ret.getPageTitles().add(p);
            });
            path = pages.get_links().getNext();
        } while (path != null);
        return ret;
    }
    
    public String getConfluencePageTitle(String pURL, List<PageTitle> pageTitles) {
        if (isConfluenceUrlType1(pURL)) {
            int o = pURL.indexOf("/pages/");
            String id = pURL.substring(o + "/pages/".length());
            o = id.indexOf("/");
            id = id.substring(0, o);
            if (pageTitles != null) {
                for (PageTitle p : pageTitles) {
                    if (p.getId().equals(id)) {
                        return p.getTitle();
                    }
                }
            }
            HttpResponse<JsonNode> response = get("/wiki/api/v2/pages/" + id);
            if (response.getStatus() == 404) {
                return null; // page not found
            } else if (response.getStatus() >= 300) {
                throw new RuntimeException("Error loading Confluence page title. ID is " + id + ", status is " + response.getStatus());
            }
            Page page = new Gson().fromJson(response.getBody().toString(), Page.class);
            return page.getTitle();
        } else if (pURL.contains(".atlassian.net/wiki/x/")) {
        	if (pageTitles == null) {
        		throw new RuntimeException("pageTitles must not be null");
        	}
            int o = pURL.indexOf("/x/");
            String id = pURL.substring(o);
            for (PageTitle p : pageTitles) {
                if (p.getTiny().equals(id)) {
                    return p.getTitle();
                }
            }
            return null;
        } else {
            throw new IllegalArgumentException("Unsupported URL");
        }
    }
    
    public static boolean isConfluenceUrl(String pURL) {
        return pURL != null && pURL.startsWith("https://") && pURL.contains(".atlassian.net");
    }
    
    public static boolean isConfluenceUrlType1(String pURL) {
        if (pURL == null || !pURL.startsWith("https://")) {
            throw new IllegalArgumentException(pURL);
        }
        return pURL.startsWith("https://") && pURL.contains(".atlassian.net/wiki/spaces") && pURL.contains("/pages/");
    }
}
