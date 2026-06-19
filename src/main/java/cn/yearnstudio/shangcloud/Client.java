package cn.yearnstudio.shangcloud;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

public class Client {
    public String clientId;
    private String clientSecret;
    public String redirectUri;
    public String scope;
    public String baseUrl;
    public TempVarStorage kvStorage;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final Gson GSON = new Gson();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public Client(String clientId, String clientSecret, String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.scope = "user:basic";
        this.baseUrl = "https://api.yearnstudio.cn";
        this.kvStorage = new RamKv();
    }

    public static Client initClient(String clientId, String clientSecret, String redirectUri) {
        return new Client(clientId, clientSecret, redirectUri);
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    private String generateAuthorizeHeader() {
        String raw = clientId + ":" + clientSecret;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes).substring(0, length);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String generateOAuthUrl() {
        String state = generateRandomString(10);
        kvStorage.setTempVariable(state, "0");
        return baseUrl + "/oauth/authorize"
            + "?response_type=code"
            + "&state=" + urlEncode(state)
            + "&client_id=" + urlEncode(clientId)
            + "&redirect_uri=" + urlEncode(redirectUri)
            + "&scope=" + urlEncode(scope);
    }

    public User generateUserInstance(String code, String state) throws ShangCloudException {
        try {
            kvStorage.getTempVariable(state);
        } catch (ShangCloudException e) {
            throw new ShangCloudException("State '" + state + "' not found or expired");
        }
        kvStorage.deleteTempVariable(state);

        String body = "grant_type=authorization_code"
            + "&code=" + urlEncode(code)
            + "&redirect_uri=" + urlEncode(redirectUri);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/oauth/token"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Basic " + generateAuthorizeHeader())
            .timeout(Duration.ofSeconds(10))
            .build();

        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() != 200) {
            throw new ShangCloudException("Auth failed with status: " + response.statusCode());
        }

        TokenResponse tokenResponse = GSON.fromJson(response.body(), TokenResponse.class);
        UserInstance user = new UserInstance();
        user.initUser(tokenResponse.accessToken, tokenResponse.refreshToken,
            tokenResponse.tokenType, tokenResponse.expiresIn, this);
        return user;
    }

    UserBasicInfo getUserBasicInfo(String accessToken, String tokenType) throws ShangCloudException {
        String responseBody = request("/api/user/info", "{}", accessToken, tokenType);
        return GSON.fromJson(responseBody, UserBasicInfo.class);
    }

    String variableAction(String action, String key, String value,
                          String accessToken, String tokenType) throws ShangCloudException {
        VariableRequest body = new VariableRequest();
        body.key = key;
        body.action = action;
        body.value = value;
        String responseBody = request("/api/varibles", GSON.toJson(body), accessToken, tokenType);
        VariableResponse resp = GSON.fromJson(responseBody, VariableResponse.class);
        if (resp != null && resp.error != null && !resp.error.isEmpty()) {
            throw new ShangCloudException("variable " + action + " failed: " + resp.error);
        }
        return resp != null && resp.value != null ? resp.value : "";
    }

    MmoNewRoomResponse mmoNewRoom(String accessToken, String tokenType, String protocol) throws ShangCloudException {
        String body = mmoRequest("/api/mmo/room/new", "{}", accessToken, tokenType, "", protocol);
        return GSON.fromJson(body, MmoNewRoomResponse.class);
    }

    MmoJoinRoomResponse mmoJoinRoom(String accessToken, String tokenType, String roomId, String protocol) throws ShangCloudException {
        String body = mmoRequest("/api/mmo/room/join", "{}", accessToken, tokenType, roomId, protocol);
        return GSON.fromJson(body, MmoJoinRoomResponse.class);
    }

    void mmoSetRoomConfig(String accessToken, String tokenType, String roomId, boolean allowMultiLogin) throws ShangCloudException {
        mmoRequest("/api/mmo/room/config", GSON.toJson(new MmoConfigRequest(allowMultiLogin)), accessToken, tokenType, roomId, "");
    }

    void mmoSetRoomData(String accessToken, String tokenType, String roomId, String key, Object value, String dataType) throws ShangCloudException {
        mmoRequest("/api/mmo/room/data/set", GSON.toJson(new MmoDataSetRequest(key, value, dataType)), accessToken, tokenType, roomId, "");
    }

    java.util.Map<String, Object> mmoGetRoomData(String accessToken, String tokenType, String roomId) throws ShangCloudException {
        String body = mmoRequest("/api/mmo/room/data/get", "{}", accessToken, tokenType, roomId, "");
        MmoDataGetResponse resp = GSON.fromJson(body, MmoDataGetResponse.class);
        return resp != null && resp.extraData != null ? resp.extraData : new java.util.HashMap<>();
    }

    void mmoDeleteRoomData(String accessToken, String tokenType, String roomId, String key) throws ShangCloudException {
        mmoRequest("/api/mmo/room/data/delete", GSON.toJson(new MmoKeyRequest(key)), accessToken, tokenType, roomId, "");
    }

    void mmoKickUser(String accessToken, String tokenType, String roomId, String targetUid) throws ShangCloudException {
        mmoRequest("/api/mmo/room/kick", GSON.toJson(new MmoKickRequest(targetUid)), accessToken, tokenType, roomId, "");
    }

    int mmoGetRoomUserCount(String accessToken, String tokenType, String roomId) throws ShangCloudException {
        String body = mmoRequest("/api/mmo/room/usercount", "{}", accessToken, tokenType, roomId, "");
        MmoUserCountResponse resp = GSON.fromJson(body, MmoUserCountResponse.class);
        return resp != null ? resp.userCount : 0;
    }

    String mmoRequest(String path, String jsonBody, String accessToken, String tokenType, String roomId, String protocol) throws ShangCloudException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .header("Content-Type", "application/json")
            .header("Authorization", tokenType + " " + accessToken)
            .timeout(Duration.ofSeconds(10));
        if (roomId != null && !roomId.isEmpty()) builder.header("X-MMO-Room", roomId);
        if (protocol != null && !protocol.isEmpty()) builder.header("X-MMO-Protoctl", protocol);
        HttpRequest req = builder.build();
        HttpResponse<String> response = sendRequest(req);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ShangCloudException(
                "Server returned error status: " + response.statusCode() + ", body: " + response.body());
        }
        return response.body();
    }

    private static class MmoConfigRequest {
        @com.google.gson.annotations.SerializedName("allow_multi_login") boolean allowMultiLogin;
        MmoConfigRequest(boolean allowMultiLogin) { this.allowMultiLogin = allowMultiLogin; }
    }

    private static class MmoDataSetRequest {
        String key;
        Object value;
        String type;
        MmoDataSetRequest(String key, Object value, String type) {
            this.key = key; this.value = value;
            if (type != null && !type.isEmpty()) this.type = type;
        }
    }

    private static class MmoDataGetResponse {
        @com.google.gson.annotations.SerializedName("extra_data") java.util.Map<String, Object> extraData;
    }

    private static class MmoKeyRequest {
        String key;
        MmoKeyRequest(String key) { this.key = key; }
    }

    private static class MmoKickRequest {
        @com.google.gson.annotations.SerializedName("target_uid") String targetUid;
        MmoKickRequest(String targetUid) { this.targetUid = targetUid; }
    }

    private static class MmoUserCountResponse {
        @com.google.gson.annotations.SerializedName("user_count") int userCount;
    }

    String request(String path, String jsonBody, String accessToken, String tokenType) throws ShangCloudException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .header("Content-Type", "application/json")
            .header("Authorization", tokenType + " " + accessToken)
            .timeout(Duration.ofSeconds(10))
            .build();

        HttpResponse<String> response = sendRequest(req);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ShangCloudException(
                "Server returned error status: " + response.statusCode() + ", body: " + response.body());
        }
        return response.body();
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws ShangCloudException {
        try {
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ShangCloudException("Request failed: " + e.getMessage(), e);
        }
    }

    private static class TokenResponse {
        @SerializedName("access_token") String accessToken;
        @SerializedName("refresh_token") String refreshToken;
        @SerializedName("token_type") String tokenType;
        @SerializedName("expires_in") int expiresIn;
    }

    private static class VariableRequest {
        String key;
        String action;
        String value;
    }

    private static class VariableResponse {
        String value;
        String error;
    }
}
