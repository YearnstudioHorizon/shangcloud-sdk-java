package cn.yearnstudio.shangcloud;

import com.google.gson.annotations.SerializedName;

public class MmoNewRoomResponse {
    @SerializedName("connect_key") public String connectKey;
    @SerializedName("edge_url")    public String edgeUrl;
    @SerializedName("room_id")     public String roomId;
    @SerializedName("protocol")    public String protocol;
}
