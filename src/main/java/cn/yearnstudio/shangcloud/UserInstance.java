package cn.yearnstudio.shangcloud;

import java.time.Instant;
import java.util.Map;

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

    public MmoNewRoomResponse newRoom(String protocol) throws ShangCloudException {
        return client.mmoNewRoom(accessToken, tokenType, protocol != null ? protocol : "");
    }

    public MmoJoinRoomResponse joinRoom(String roomId, String protocol) throws ShangCloudException {
        return client.mmoJoinRoom(accessToken, tokenType, roomId, protocol != null ? protocol : "");
    }

    public void setRoomConfig(String roomId, boolean allowMultiLogin) throws ShangCloudException {
        client.mmoSetRoomConfig(accessToken, tokenType, roomId, allowMultiLogin);
    }

    public void setRoomData(String roomId, String key, Object value, String dataType) throws ShangCloudException {
        client.mmoSetRoomData(accessToken, tokenType, roomId, key, value, dataType);
    }

    public Map<String, Object> getRoomData(String roomId) throws ShangCloudException {
        return client.mmoGetRoomData(accessToken, tokenType, roomId);
    }

    public void deleteRoomData(String roomId, String key) throws ShangCloudException {
        client.mmoDeleteRoomData(accessToken, tokenType, roomId, key);
    }

    public void kickUser(String roomId, String targetUid) throws ShangCloudException {
        client.mmoKickUser(accessToken, tokenType, roomId, targetUid);
    }

    public int getRoomUserCount(String roomId) throws ShangCloudException {
        return client.mmoGetRoomUserCount(accessToken, tokenType, roomId);
    }
}
