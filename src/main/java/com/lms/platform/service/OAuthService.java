package com.lms.platform.service;

import com.google.gson.*;
import com.lms.platform.util.AppConfig;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OAuth2 authorization code flow for Google and Microsoft.
 * Handles: redirect URL generation, code→token exchange, userinfo retrieval.
 */
public class OAuthService {

    public record OAuthUserInfo(String email, String nom, String prenom, String provider) {}

    // ═══ Google OAuth2 ═══

    public static String googleAuthUrl(String callbackUrl) {
        String clientId = AppConfig.get("lms.oauth.google.client-id", "");
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + enc(clientId) +
                "&redirect_uri=" + enc(callbackUrl) +
                "&response_type=code" +
                "&scope=" + enc("openid email profile") +
                "&access_type=offline" +
                "&prompt=select_account";
    }

    public static OAuthUserInfo googleCallback(String code, String callbackUrl) throws Exception {
        String clientId = AppConfig.get("lms.oauth.google.client-id", "");
        String clientSecret = AppConfig.get("lms.oauth.google.client-secret", "");

        // Exchange code for token
        String tokenBody = "code=" + enc(code) +
                "&client_id=" + enc(clientId) +
                "&client_secret=" + enc(clientSecret) +
                "&redirect_uri=" + enc(callbackUrl) +
                "&grant_type=authorization_code";

        JsonObject tokenResp = postForm("https://oauth2.googleapis.com/token", tokenBody);
        String accessToken = tokenResp.get("access_token").getAsString();

        // Get user info
        JsonObject userInfo = getJson("https://www.googleapis.com/oauth2/v2/userinfo",
                Map.of("Authorization", "Bearer " + accessToken));

        String email = userInfo.has("email") ? userInfo.get("email").getAsString() : "";
        String nom = userInfo.has("family_name") ? userInfo.get("family_name").getAsString() : "";
        String prenom = userInfo.has("given_name") ? userInfo.get("given_name").getAsString() : "";
        if (nom.isEmpty() && userInfo.has("name")) nom = userInfo.get("name").getAsString();

        return new OAuthUserInfo(email, nom, prenom, "GOOGLE");
    }

    // ═══ Microsoft OAuth2 ═══

    public static String microsoftAuthUrl(String callbackUrl) {
        String clientId = AppConfig.get("lms.oauth.microsoft.client-id", "");
        String tenant = AppConfig.get("lms.oauth.microsoft.tenant", "common");
        return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize?" +
                "client_id=" + enc(clientId) +
                "&redirect_uri=" + enc(callbackUrl) +
                "&response_type=code" +
                "&scope=" + enc("openid email profile User.Read") +
                "&response_mode=query" +
                "&prompt=select_account";
    }

    public static OAuthUserInfo microsoftCallback(String code, String callbackUrl) throws Exception {
        String clientId = AppConfig.get("lms.oauth.microsoft.client-id", "");
        String clientSecret = AppConfig.get("lms.oauth.microsoft.client-secret", "");
        String tenant = AppConfig.get("lms.oauth.microsoft.tenant", "common");

        // Exchange code for token
        String tokenBody = "code=" + enc(code) +
                "&client_id=" + enc(clientId) +
                "&client_secret=" + enc(clientSecret) +
                "&redirect_uri=" + enc(callbackUrl) +
                "&grant_type=authorization_code" +
                "&scope=" + enc("openid email profile User.Read");

        JsonObject tokenResp = postForm(
                "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token", tokenBody);
        String accessToken = tokenResp.get("access_token").getAsString();

        // Get user info from Microsoft Graph
        JsonObject userInfo = getJson("https://graph.microsoft.com/v1.0/me",
                Map.of("Authorization", "Bearer " + accessToken));

        String email = "";
        if (userInfo.has("mail") && !userInfo.get("mail").isJsonNull())
            email = userInfo.get("mail").getAsString();
        else if (userInfo.has("userPrincipalName"))
            email = userInfo.get("userPrincipalName").getAsString();

        String nom = userInfo.has("surname") ? userInfo.get("surname").getAsString() : "";
        String prenom = userInfo.has("givenName") ? userInfo.get("givenName").getAsString() : "";
        if (nom.isEmpty() && userInfo.has("displayName")) nom = userInfo.get("displayName").getAsString();

        return new OAuthUserInfo(email, nom, prenom, "MICROSOFT");
    }

    // ═══ Config checks ═══

    public static boolean isGoogleEnabled() {
        return "true".equals(AppConfig.get("lms.oauth.google.enabled", "false"))
                && !AppConfig.get("lms.oauth.google.client-id", "").isEmpty();
    }

    public static boolean isMicrosoftEnabled() {
        return "true".equals(AppConfig.get("lms.oauth.microsoft.enabled", "false"))
                && !AppConfig.get("lms.oauth.microsoft.client-id", "").isEmpty();
    }

    public static boolean isRegistrationEnabled() {
        return "true".equals(AppConfig.get("lms.registration.enabled", "true"));
    }

    // ═══ HTTP helpers ═══

    private static JsonObject postForm(String url, String body) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static JsonObject getJson(String url, Map<String, String> headers) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        var builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        headers.forEach(builder::header);
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
