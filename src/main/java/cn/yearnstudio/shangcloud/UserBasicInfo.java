package cn.yearnstudio.shangcloud;

import com.google.gson.annotations.SerializedName;

public class UserBasicInfo {
    @SerializedName("uid")
    private int userId;

    @SerializedName("nickname")
    private String nickname;

    @SerializedName("mail")
    private String mail;

    @SerializedName("avatar")
    private String avatar;

    public int getUserId() { return userId; }
    public String getNickname() { return nickname; }
    public String getMail() { return mail; }
    public String getAvatar() { return avatar; }

    @Override
    public String toString() {
        return "UserBasicInfo{userId=" + userId
            + ", nickname='" + nickname + '\''
            + ", mail='" + mail + '\''
            + ", avatar='" + avatar + '\''
            + '}';
    }
}
