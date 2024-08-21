package de.xmap.jiracloud;

/**
 * Entry of issue history
 */
public class Changelog {
    private final String field;
    private final String from;
    private final String to;

    public Changelog(String field, String from, String to) {
        this.field = field;
        this.from = from;
        this.to = to;
    }

    public String getField() {
        return field;
    }

    /**
     * @return old value
     */
    public String getFrom() {
        return from;
    }

    /**
     * @return new value
     */
    public String getTo() {
        return to;
    }
}
