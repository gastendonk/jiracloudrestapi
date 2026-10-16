package de.xmap.jiracloud;

/**
 * @see JiraCloudAccess#saveFields(String, java.util.Collection)
 */
public class Field {
    private String id;
    private String text;
    /** true: simple text field, false: multi-line field (which contain HTML or plain text) */
    private boolean plainText;

    public Field() {
    }

    public Field(String id, String text, boolean plainText) {
        this.id = id;
        this.text = text;
        this.plainText = plainText;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isPlainText() {
        return plainText;
    }

    public void setPlainText(boolean plainText) {
        this.plainText = plainText;
    }
}
