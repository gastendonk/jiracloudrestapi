package de.xmap.jiracloud;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

public class JiraCloudAccess {
    private final String auth;
    private final String customer;

    /**
     * @param mail mail address for Jira Cloud login
     * @param token token for Jira Cloud login
     * @param customer Jira Cloud customer
     */
    public JiraCloudAccess(String mail, String token, String customer) {
        auth = "Basic " + Base64.getEncoder().encodeToString((mail + ":" + token).getBytes());
        this.customer = customer;
    }
    
    public <T> List<T> loadIssues(String jql, Function<Issue, T> creator) {
        return loadIssues(jql, "", creator);
    }

    public List<Issue> loadIssues(String jql, String queryExtension) {
        return loadIssues(jql, queryExtension, i -> i);
    }

    private <T> List<T> loadIssues(String jql, String queryExtension, Function<Issue, T> creator) {
        List<T> ret = new ArrayList<>();
        JsonNode json = Unirest.get("https://" + customer + ".atlassian.net/rest/api/3/search?jql=" + urlEncode(jql, "") + queryExtension) //
                .header("Accept", "application/json") //
                .header("Authorization", auth) //
                .asJson().getBody();
        for (Object issue0 : json.getObject().getJSONArray("issues")) {
            ret.add(creator.apply(new Issue((JSONObject) issue0)));
        }
        return ret;
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
        return Unirest.get("https://" + customer + ".atlassian.net" + src).header("Authorization", auth).asBytes().getBody();
    }

    public class Issue {
        private final JSONObject jo;
        
        public Issue(JSONObject issue) {
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
            List<Issue> r = loadIssues(jql, "&expand=renderedFields&fields=" + fieldname);
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
    }
}
