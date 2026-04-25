package cn.yearnstudio.shangcloud;

import java.time.Instant;

public class UserInstance implements User {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private int expiresIn;
    private Instant expiryTime;
    private Client client;

    @Override
    public void initUser(String accessToken, String refreshToken, String tokenType,
                         int expiresIn, Client client) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.client = client;
        this.expiryTime = Instant.now().plusSeconds(expiresIn);
        save();
    }

    @Override
    public void save() {}

    @Override
    public boolean isExpired() {
        return Instant.now().plusSeconds(60).isAfter(expiryTime);
    }

    @Override
    public UserBasicInfo getBasicInfo() throws ShangCloudException {
        return client.getUserBasicInfo(accessToken, tokenType);
    }

    @Override
    public String getVariable(String key) throws ShangCloudException {
        return client.variableAction("read", key, "", accessToken, tokenType);
    }

    @Override
    public void setVariable(String key, String value) throws ShangCloudException {
        client.variableAction("write", key, value, accessToken, tokenType);
    }

    @Override
    public void deleteVariable(String key) throws ShangCloudException {
        client.variableAction("delete", key, "", accessToken, tokenType);
    }

    public int getExpiresIn() { return expiresIn; }
    public Instant getExpiryTime() { return expiryTime; }
    public String getTokenType() { return tokenType; }
}
