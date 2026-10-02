package ua.requestflow;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.json.JSONObject;

public final class FirebaseAuthService {

    private static final String DEFAULT_API_KEY =
            "AIzaSyD9hhn6rAm-DHEZ9Mv9Cnh0MgNyp4fj_Ho";

    private static final String IDENTITY_TOOLKIT_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:";

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private FirebaseAuthService() {
    }

    public static CompletableFuture<JSONObject> createAccount(
            String email,
            String password
    ) {
        return authenticate(
                "signUp",
                email,
                password
        );
    }

    public static CompletableFuture<JSONObject> signIn(
            String email,
            String password
    ) {
        return authenticate(
                "signInWithPassword",
                email,
                password
        );
    }

    private static CompletableFuture<JSONObject> authenticate(
            String operation,
            String email,
            String password
    ) {
        String apiKey = System.getProperty("firebase.api.key");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("FIREBASE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = DEFAULT_API_KEY;
        }

        HttpRequest request;
        try {
            JSONObject payload = new JSONObject()
                    .put("email", email)
                    .put("password", password)
                    .put("returnSecureToken", true);

            request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            IDENTITY_TOOLKIT_URL
                                    + operation
                                    + "?key="
                                    + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                    ))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        return HTTP_CLIENT.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                )
                .thenApply(response -> {
                    JSONObject body = new JSONObject(response.body());
                    if (response.statusCode() != 200) {
                        String code = body
                                .optJSONObject("error")
                                .optString("message", "UNKNOWN_ERROR");
                        throw new CompletionException(
                                new IllegalArgumentException(toUserMessage(code))
                        );
                    }
                    return body;
                });
    }

    private static String toUserMessage(String code) {
        if (code.startsWith("EMAIL_EXISTS")) {
            return "Цей email вже зареєстрований у Firebase.";
        }
        if (code.startsWith("EMAIL_NOT_FOUND")
                || code.startsWith("INVALID_PASSWORD")
                || code.startsWith("INVALID_LOGIN_CREDENTIALS")) {
            return "Неправильний email або пароль.";
        }
        if (code.startsWith("WEAK_PASSWORD")) {
            return "Пароль має містити щонайменше 6 символів.";
        }
        if (code.startsWith("INVALID_EMAIL")) {
            return "Введіть коректну email-адресу.";
        }
        if (code.startsWith("OPERATION_NOT_ALLOWED")) {
            return "У Firebase не ввімкнено вхід за email і паролем.";
        }
        if (code.startsWith("API_KEY_INVALID")) {
            return "Firebase API key некоректний. Перевірте налаштування проєкту.";
        }
        return "Помилка Firebase Authentication: " + code;
    }
}