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
