package net.ripe.db.whois.common.sso;

public class UserSession {
    final private String username;
    final private boolean isActive;
    private String uuid;

    public UserSession(String username, boolean isActive) {
        this.username = username;
        this.isActive = isActive;
    }

    public String getUsername() {
        return username;
    }

    public boolean isActive() {
        return isActive;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "username='" + username + '\'' +
                ", isActive=" + isActive +
                ", uuid='" + uuid + '\'' +
                '}';
    }
}