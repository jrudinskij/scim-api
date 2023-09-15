package lt.jr.alfresco.scim.api.model;

public class GroupInfo {

    private String shortName;
    private String fullName;
    private String displayName;

    public GroupInfo(String shortName, String displayName, String fullName) {
        this.shortName = shortName;
        this.displayName = displayName;
        this.fullName = fullName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
