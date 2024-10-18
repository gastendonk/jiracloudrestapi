package de.xmap.jiracloud;

import java.util.List;

public class PageTitles {
    private List<PageTitle> pageTitles;
    
    public List<PageTitle> getPageTitles() {
        return pageTitles;
    }

    public void setPageTitles(List<PageTitle> pageTitles) {
        this.pageTitles = pageTitles;
    }

    public static class PageTitle {
        private String id;
        private String tiny;
        private String title;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTiny() {
            return tiny;
        }

        public void setTiny(String tiny) {
            this.tiny = tiny;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
