package de.xmap.jiracloud;

import java.util.List;

/**
 * Confluence pages as JSON, internal classes
 */
public class Pages {
    private List<Page> results;
    private Next _links;
    
    public List<Page> getResults() {
        return results;
    }

    public void setResults(List<Page> results) {
        this.results = results;
    }
    
    public Next get_links() {
        return _links;
    }

    public void set_links(Next _links) {
        this._links = _links;
    }
    
    public static class Page {
        private String id;
        private String title;
        private Tiny _links;
        
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Tiny get_links() {
            return _links;
        }

        public void set_links(Tiny _links) {
            this._links = _links;
        }
    }

    public static class Tiny {
        private String tinyui;

        public String getTinyui() {
            return tinyui;
        }

        public void setTinyui(String tinyui) {
            this.tinyui = tinyui;
        }
    }

    public static class Next {
        private String next;

        public String getNext() {
            return next;
        }

        public void setNext(String next) {
            this.next = next;
        }
    }
}
