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
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.xmap.jiracloud.Issue.FixVersion;
import de.xmap.jiracloud.Issue.Project;
import de.xmap.jiracloud.Issue.Subtask;
import de.xmap.jiracloud.Issue.Version;
import de.xmap.jiracloud.Ticket2.Subticket;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

public class JiraCloudAccess {
    private final String url;
    private final String auth;
    private boolean debugMode = false;
    
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
    
    public <T> List<T> loadIssues(String jql, Function<IssueAccess, T> creator) {
        return loadIssues(jql, "", creator);
    }

    public List<IssueAccess> loadIssues(String jql, String queryExtension) {
        return loadIssues(jql, queryExtension, i -> i);
    }

    private <T> List<T> loadIssues(String jql, String queryExtension, Function<IssueAccess, T> creator) {
        List<T> ret = new ArrayList<>();
        HttpResponse<JsonNode> response = get("/rest/api/3/search?jql=" + urlEncode(jql, "") + queryExtension);
        if (response.getStatus() >= 300) {
            throw new RuntimeException("Error loading issues. Status is " + response.getStatus());
        }
        JsonNode json = response.getBody();
        if (debugMode) {
            System.out.println(json.toPrettyString());
        }
        for (Object issue0 : json.getObject().getJSONArray("issues")) {
            ret.add(creator.apply(new IssueAccess((JSONObject) issue0)));
        }
        return ret;
    }
    
    /**
     * @param path -
     * @return JSON
     */
    public HttpResponse<JsonNode> get(String path) {
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

    byte[] loadImage(String src) {
        HttpResponse<byte[]> response = Unirest.get(url + src).header("Authorization", auth).asBytes();
        if (response.getStatus() >= 300) {
            throw new RuntimeException("Error loading image. Status is " + response.getStatus());
        }
        return response.getBody();
    }

    public class IssueAccess {
        private final JSONObject jo;
        
        public IssueAccess(JSONObject issue) {
            jo = issue;
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
            List<IssueAccess> r = loadIssues(jql, "&expand=renderedFields&fields=" + fieldname);
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
            JSONArray content = (JSONArray) jo.query(path + "/content");
            if (content.length() == 1) {
                String type = (String) jo.query(path + "/content/0/type");
                return "paragraph".equals(type);
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
         * Get linked issues
         * @param outwardType -
         * @return ticket numbers
         */
        public List<String> getLinkedOutwardIssue(String outwardType) {
            List<String> ret = new ArrayList<>();
            for (Object i : (JSONArray) jo.query("/fields/issuelinks")) {
                JSONObject o = (JSONObject) i;
                if (outwardType.equals(o.query("/type/outward"))) {
                    ret.add((String) o.query("/outwardIssue/key"));
                }
            }
            return ret;
        }

        public JSONObject getJSONObject() {
            return jo;
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
}
