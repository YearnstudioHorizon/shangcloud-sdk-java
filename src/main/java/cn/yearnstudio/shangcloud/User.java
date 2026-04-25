package cn.yearnstudio.shangcloud;

public interface User {
    void initUser(String accessToken, String refreshToken, String tokenType, int expiresIn, Client client);
    void save();
    boolean isExpired();
    UserBasicInfo getBasicInfo() throws ShangCloudException;
}
