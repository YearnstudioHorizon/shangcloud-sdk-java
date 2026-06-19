package cn.yearnstudio.shangcloud;

import com.google.gson.annotations.SerializedName;

public class MmoJoinRoomResponse {
    @SerializedName("connect_key")  public String connectKey;
    @SerializedName("edge_url")     public String edgeUrl;
    @SerializedName("room_id")      public String roomId;
    @SerializedName("protocol")     public String protocol;
    @SerializedName("assigned_uid") public String assignedUid;
}
