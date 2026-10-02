package ua.requestflow;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainController {

    private static final String SERVER_URL =
            "http://127.0.0.1:4567";

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

    private static String currentUserId;
    private static String currentUserFirebaseUid;
    private static String currentUserRole;
    private static String currentUserName;
    private static String currentUserEmail;

    @FXML
    private TextField loginEmailField;

    @FXML
    private PasswordField loginPasswordField;

    @FXML
    private TextField registerNameField;

    @FXML
    private TextField registerEmailField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private PasswordField registerConfirmPasswordField;

        @FXML
        private Button registerButton;

    @FXML
    private TextField applicationTitleField;

    @FXML
    private ComboBox<String> categoryComboBox;

    @FXML
    private ComboBox<String> priorityComboBox;

    @FXML
    private TextArea descriptionArea;

        @FXML
        private Button createApplicationButton;

    @FXML
    private TableView<ApplicationRow> applicationsTable;

    @FXML
    private Label applicationsTitleLabel;

    @FXML
    private Button newApplicationButton;

    @FXML
    private Button applicationsNavButton;

    @FXML
    private Button navbarCreateApplicationButton;

    @FXML
    private TextField applicationsSearchField;

    @FXML
    private ComboBox<String> applicationFilterCategoryComboBox;

    @FXML
    private ComboBox<String> applicationFilterStatusComboBox;

    @FXML
    private ComboBox<String> applicationStatusComboBox;

    @FXML
    private TextField adminResponseField;

    @FXML
    private Button updateStatusButton;

    @FXML
    private Button sendResponseButton;

    @FXML
    private TableColumn<ApplicationRow, String> idColumn;

    @FXML
    private TableColumn<ApplicationRow, String> titleColumn;

    @FXML
    private TableColumn<ApplicationRow, String> categoryColumn;

    @FXML
    private TableColumn<ApplicationRow, String> priorityColumn;

    @FXML
    private TableColumn<ApplicationRow, String> statusColumn;

    @FXML
    private TableColumn<ApplicationRow, String> responseColumn;

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label totalApplicationsLabel;

    @FXML
    private Label processingApplicationsLabel;

    @FXML
    private Label resolvedApplicationsLabel;

    private final ObservableList<ApplicationRow> allApplications = FXCollections.observableArrayList();

    @FXML
    private void initialize() {

        if (categoryComboBox != null) {
            categoryComboBox.getItems().addAll(
                    "Технічна проблема",
                    "Питання",
                    "Помилка",
                    "Інше"
            );
        }

        if (priorityComboBox != null) {
            priorityComboBox.getItems().addAll(
                    "Низький",
                    "Середній",
                    "Високий"
            );
        }

        if (applicationsTitleLabel != null) {
            refreshApplicationsPageTitle();
        }

        if (applicationsSearchField != null) {
            applicationsSearchField.textProperty().addListener((observable, oldValue, newValue) -> applyApplicationFilter());
        }

        if (applicationFilterCategoryComboBox != null) {
            applicationFilterCategoryComboBox.getItems().addAll(
                    "Усі",
                    "Технічна проблема",
                    "Питання",
                    "Помилка",
                    "Інше"
            );
            applicationFilterCategoryComboBox.setValue("Усі");
            applicationFilterCategoryComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyApplicationFilter());
        }

        if (applicationFilterStatusComboBox != null) {
            applicationFilterStatusComboBox.getItems().addAll(
                    "Активні",
                    "Нова",
                    "В обробці",
                    "Виконано",
                    "Усі статуси"
            );
            applicationFilterStatusComboBox.setValue("Усі статуси");
            applicationFilterStatusComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyApplicationFilter());
        }

        if (applicationStatusComboBox != null) {
            applicationStatusComboBox.getItems().addAll(
                    "Нова",
                    "В обробці",
                    "Виконано"
            );
        }

        if (applicationsTable != null) {
            applicationsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    if (applicationStatusComboBox != null) {
                        applicationStatusComboBox.setValue(newValue.statusProperty().get());
                    }
                    if (adminResponseField != null) {
                        adminResponseField.setText(newValue.responseProperty().get());
                    }
                }
            });
            setupApplicationsTable();
            refreshApplicationsPageTitle();
            loadApplications();
        }

        if (welcomeLabel != null) {
            updateHome();
        }
    }


    @FXML
    private void openLogin() {
        logout();
    }


    @FXML
    private void openRegister() {
        openPage("register.fxml");
    }


    @FXML
    private void openHome() {
        openPage("home.fxml");
    }


    @FXML
    private void openApplications() {
        openPage("applications.fxml");
    }


    @FXML
    private void openCreateApplication() {
        if (isAdminUser()) {
            showWarning("Адміністратор не може створювати заявки.");
            return;
        }

        openPage("create-application.fxml");
    }


    private void openPage(String page) {

        try {
            Main.openPage(page);

        } catch (Exception e) {

            e.printStackTrace();

            showError(
                    "Не вдалося відкрити сторінку:\n"
                            + page
                            + "\n\n"
                            + e.getClass().getSimpleName()
                            + ":\n"
                            + e.getMessage()
            );
        }
    }


    @FXML
    private void handleLogin() {

        String email = loginEmailField.getText().trim();
        String password = loginPasswordField.getText();

        if (email.isEmpty()) {
            showWarning("Введіть email.");
            return;
        }

        if (password.isEmpty()) {
            showWarning("Введіть пароль.");
            return;
        }

        if ("admin1".equalsIgnoreCase(email) && "admin1".equals(password)) {
            loginAsAdmin(email, password);
            return;
        }

        FirebaseAuthService.signIn(email, password)
                .thenCompose(auth -> {
                    currentUserFirebaseUid = auth.getString("localId");
                    return syncFirebaseProfile(
                            auth.getString("localId"),
                            "",
                            email
                    );
                })
                .whenComplete((user, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        showError(getAuthError(error));
                        return;
                    }

                    currentUserId = String.valueOf(user.getInt("id"));
                    currentUserRole = user.optString("role", "user");
                    currentUserName = user.getString("name");
                    currentUserEmail = user.getString("email");
                    openHome();
                }));
    }

    private void loginAsAdmin(String email, String password) {
        JSONObject payload = new JSONObject()
                .put("email", email)
                .put("password", password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + "/api/auth/admin-login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        sendRequest(request, response -> {
            if (response.statusCode() != 200) {
                showError(getServerError(response.body()));
                return;
            }

            JSONObject user = new JSONObject(response.body()).getJSONObject("user");
            currentUserId = String.valueOf(user.getInt("id"));
            currentUserFirebaseUid = "admin-firebase-admin1";
            currentUserRole = "admin";
            currentUserName = user.optString("name", "Адміністратор");
            currentUserEmail = user.optString("email", "admin1");
            openHome();
        }, "Не вдалося увійти як адміністратор.");
    }


    @FXML
    private void handleRegister() {

        String name =
                registerNameField.getText().trim();

        String email =
                registerEmailField.getText().trim();

        String password =
                registerPasswordField.getText();

        String confirmPassword =
                registerConfirmPasswordField.getText();


        if (name.isEmpty()) {
            showWarning("Введіть ім'я.");
            return;
        }

        if (email.isEmpty()) {
            showWarning("Введіть email.");
            return;
        }

        if (password.isEmpty()) {
            showWarning("Введіть пароль.");
            return;
        }

        if (confirmPassword.isEmpty()) {
            showWarning("Повторіть пароль.");
            return;
        }

        if (!password.equals(confirmPassword)) {

            showWarning(
                    "Паролі не співпадають."
            );

            return;
        }

        if (password.length() < 6) {
            showWarning("Пароль має містити щонайменше 6 символів.");
            return;
        }

        registerButton.setDisable(true);
        registerButton.setText("Створюємо акаунт...");

        FirebaseAuthService.createAccount(email, password)
                .thenCompose(auth -> syncFirebaseProfile(
                        auth.getString("localId"),
                        name,
                        email
                ))
                .whenComplete((user, error) -> Platform.runLater(() -> {
                    registerButton.setDisable(false);
                    registerButton.setText("Зареєструватися");

                    if (error != null) {
                        showError(getAuthError(error));
                        return;
                    }

                    currentUserId = null;
                    currentUserFirebaseUid = null;
                    currentUserRole = null;
                    currentUserName = null;
                    currentUserEmail = null;
                    showInfo("Акаунт створено. Тепер увійдіть у систему.");
                    openPage("login.fxml");
                }));
    }


    private CompletableFuture<JSONObject> syncFirebaseProfile(
            String firebaseUid,
            String name,
            String email
    ) {
        JSONObject payload = new JSONObject()
                .put("firebase_uid", firebaseUid)
                .put("name", name)
                .put("email", email);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + "/api/auth/sync"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return HTTP_CLIENT.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                )
                .thenApply(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 201) {
                        throw new CompletionException(
                                new IllegalStateException(getServerError(response.body()))
                        );
                    }
                    return new JSONObject(response.body()).getJSONObject("user");
                });
    }


    private String getAuthError(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? "Не вдалося зв'язатися з Firebase або Ruby-сервером. Перевірте підключення та налаштування."
                : message;
    }


    private void logout() {

        currentUserId = null;
        currentUserFirebaseUid = null;
        currentUserRole = null;
        currentUserName = null;
        currentUserEmail = null;

        openPage("login.fxml");
    }


    @FXML
    private boolean isAdminUser() {
        return currentUserRole != null && "admin".equalsIgnoreCase(currentUserRole);
    }

    @FXML
    private void handleCreateApplication() {

        if (currentUserId == null) {

            showWarning(
                    "Спочатку увійдіть до акаунта."
            );

            openLogin();

            return;
        }

        if (isAdminUser()) {
            showWarning("Адміністратор не може створювати заявки. Перейдіть у режим перегляду заявок користувачів.");
            return;
        }


        String title =
                applicationTitleField
                        .getText()
                        .trim();

        String category =
                categoryComboBox.getValue();

        String priority =
                priorityComboBox.getValue();

        String description =
                descriptionArea
                        .getText()
                        .trim();


        if (title.isEmpty()) {

            showWarning(
                    "Введіть тему заявки."
            );

            return;
        }


        if (category == null) {

            showWarning(
                    "Оберіть категорію."
            );

            return;
        }


        if (priority == null) {

            showWarning(
                    "Оберіть пріоритет."
            );

            return;
        }


        if (description.isEmpty()) {

            showWarning(
                    "Введіть опис заявки."
            );

            return;
        }


        sendApplicationToServer(
                title,
                category,
                priority,
                description
        );
    }


    private void sendApplicationToServer(
            String title,
            String category,
            String priority,
            String description
    ) {

        try {

            String json =
                    "{"
                            + "\"user_id\":\""
                            + escapeJson(currentUserId)
                            + "\","

                            + "\"firebase_uid\":\""
                            + escapeJson(currentUserFirebaseUid == null ? "" : currentUserFirebaseUid)
                            + "\","

                            + "\"title\":\""
                            + escapeJson(title)
                            + "\","

                            + "\"category\":\""
                            + escapeJson(category)
                            + "\","

                            + "\"priority\":\""
                            + escapeJson(priority)
                            + "\","

                            + "\"description\":\""
                            + escapeJson(description)
                            + "\""
                            + "}";


            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            SERVER_URL
                                                    + "/api/applications"
                                    )
                            )
                            .timeout(Duration.ofSeconds(10))
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();


            createApplicationButton.setDisable(true);
            createApplicationButton.setText("Створюємо...");

            sendRequest(request, response -> {
                if (response.statusCode() == 201) {

                /*
                 * Заявка успішно створена.
                 *
                 * Одразу переходимо
                 * на сторінку "Мої заявки".
                 */

                                        openApplications();

                                } else {
                                        showError(getServerError(response.body()));
                                }
                        }, "Не вдалося підключитися до Ruby-сервера. Перевірте, чи запущений app.rb.", () -> {
                                createApplicationButton.setDisable(false);
                                createApplicationButton.setText("Створити заявку");
                        });

        } catch (Exception e) {

            e.printStackTrace();

            showError(
                    "Не вдалося підключитися "
                            + "до Ruby-сервера.\n\n"
                            + "Перевірте, чи запущений app.rb."
            );
        }
    }


    private void refreshApplicationsPageTitle() {
        boolean adminMode = isAdminUser();

        if (applicationsTitleLabel != null) {
            applicationsTitleLabel.setText(adminMode ? "Заявки користувачів" : "Мої заявки");
        }

        if (applicationsNavButton != null) {
            applicationsNavButton.setText(adminMode ? "Заявки користувачів" : "Мої заявки");
        }

        if (navbarCreateApplicationButton != null) {
            navbarCreateApplicationButton.setVisible(!adminMode);
            navbarCreateApplicationButton.setManaged(!adminMode);
        }

        if (newApplicationButton != null) {
            boolean showCreateButton = !adminMode;
            newApplicationButton.setVisible(showCreateButton);
            newApplicationButton.setManaged(showCreateButton);
        }

        if (applicationFilterStatusComboBox != null) {
            applicationFilterStatusComboBox.setVisible(adminMode);
            applicationFilterStatusComboBox.setManaged(adminMode);
        }

        if (applicationStatusComboBox != null) {
            applicationStatusComboBox.setVisible(adminMode);
            applicationStatusComboBox.setManaged(adminMode);
        }

        if (adminResponseField != null) {
            adminResponseField.setVisible(adminMode);
            adminResponseField.setManaged(adminMode);
        }

        if (updateStatusButton != null) {
            updateStatusButton.setVisible(adminMode);
            updateStatusButton.setManaged(adminMode);
        }

        if (sendResponseButton != null) {
            sendResponseButton.setVisible(adminMode);
            sendResponseButton.setManaged(adminMode);
        }
    }

    @FXML
    private void handleApplicationSearch() {
        applyApplicationFilter();
    }

    private void applyApplicationFilter() {
        if (applicationsTable == null) {
            return;
        }

        String query = applicationsSearchField == null
                ? ""
                : applicationsSearchField.getText().trim().toLowerCase(Locale.ROOT);

        String selectedCategory = applicationFilterCategoryComboBox == null
                ? "Усі"
                : applicationFilterCategoryComboBox.getValue();

        if (selectedCategory == null) {
            selectedCategory = "Усі";
        }

        String selectedStatus = applicationFilterStatusComboBox == null || !isAdminUser()
                ? "Усі статуси"
                : applicationFilterStatusComboBox.getValue();
        if (selectedStatus == null) {
            selectedStatus = "Усі статуси";
        }

        if (query.isEmpty() && "Усі".equals(selectedCategory) && "Усі статуси".equals(selectedStatus)) {
            applicationsTable.setItems(allApplications);
            return;
        }

        ObservableList<ApplicationRow> filtered = FXCollections.observableArrayList();

        for (ApplicationRow row : allApplications) {
            String rowCategory = row.categoryProperty().get();
            String haystack = (
                    row.titleProperty().get() + " " +
                    rowCategory + " " +
                    row.priorityProperty().get() + " " +
                    row.statusProperty().get() + " " +
                    row.responseProperty().get()
            ).toLowerCase(Locale.ROOT);

            boolean matchesText = query.isEmpty() || haystack.contains(query);
            boolean matchesCategory = "Усі".equals(selectedCategory) || rowCategory.equals(selectedCategory);
            String rowStatus = row.statusProperty().get();
            boolean matchesStatus = "Усі статуси".equals(selectedStatus)
                    || ("Активні".equals(selectedStatus) && !"Виконано".equals(rowStatus))
                    || selectedStatus.equals(rowStatus);

            if (matchesText && matchesCategory && matchesStatus) {
                filtered.add(row);
            }
        }

        applicationsTable.setItems(filtered);
    }

    @FXML
    private void handleStatusUpdate() {
        if (currentUserRole == null || !"admin".equals(currentUserRole)) {
            showWarning("Тільки адміністратор може змінювати статус заявки.");
            return;
        }

        ApplicationRow selected = applicationsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Спочатку виберіть заявку зі списку.");
            return;
        }

        String newStatus = applicationStatusComboBox != null ? applicationStatusComboBox.getValue() : null;
        if (newStatus == null || newStatus.isBlank()) {
            showWarning("Оберіть новий статус.");
            return;
        }

        String responseText = adminResponseField == null ? "" : adminResponseField.getText().trim();
        JSONObject payload = new JSONObject()
                .put("user_id", currentUserId)
                .put("firebase_uid", currentUserFirebaseUid)
                .put("status", newStatus)
                .put("admin_response", responseText);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + "/api/applications/" + selected.idProperty().get()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        sendRequest(request, response -> {
            if (response.statusCode() != 200) {
                showError(getServerError(response.body()));
                return;
            }
            loadApplications();
            showInfo("Статус заявки оновлено.");
        }, "Не вдалося оновити статус заявки.");
    }

    @FXML
    private void handleAdminReply() {
        handleStatusUpdate();
    }

    private void setupApplicationsTable() {

        applicationsTable.setRowFactory(table -> {
            TableRow<ApplicationRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showApplicationDescription(row.getItem());
                }
            });
            return row;
        });

        idColumn.setCellValueFactory(
                cell ->
                        cell.getValue()
                                .idProperty()
        );

        titleColumn.setCellValueFactory(
                cell ->
                        cell.getValue()
                                .titleProperty()
        );

        categoryColumn.setCellValueFactory(
                cell ->
                        cell.getValue()
                                .categoryProperty()
        );

        priorityColumn.setCellValueFactory(
                cell ->
                        cell.getValue()
                                .priorityProperty()
        );

        statusColumn.setCellValueFactory(
                cell ->
                        cell.getValue()
                                .statusProperty()
        );

        responseColumn.setCellValueFactory(
                cell ->
                        cell.getValue()
                                .responseProperty()
        );
    }

    private void showApplicationDescription(ApplicationRow application) {
        TextArea description = new TextArea(application.descriptionProperty().get());
        description.setEditable(false);
        description.setWrapText(true);
        description.setPrefRowCount(8);
        description.setPrefColumnCount(40);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(Main.getStage());
        alert.initModality(Modality.WINDOW_MODAL);
        alert.setTitle("Опис заявки");
        alert.setHeaderText(application.titleProperty().get());
        alert.getDialogPane().setContent(description);
        alert.setResizable(true);
        alert.showAndWait();
    }


    private void loadApplications() {

        if (currentUserId == null) {
            return;
        }


        try {

            String encodedUserId =
                    URLEncoder.encode(
                            currentUserId,
                            StandardCharsets.UTF_8
                    );

            String encodedFirebaseUid = currentUserFirebaseUid == null
                    ? ""
                    : URLEncoder.encode(
                            currentUserFirebaseUid,
                            StandardCharsets.UTF_8
                    );

            String query = "?user_id=" + encodedUserId;
            if (currentUserFirebaseUid != null && !currentUserFirebaseUid.isBlank()) {
                query += "&firebase_uid=" + encodedFirebaseUid;
            }

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            SERVER_URL
                                                    + "/api/applications"
                                                    + query
                                    )
                            )
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();


            sendRequest(request, response -> {
                if (response.statusCode() != 200) {
                    showError(getServerError(response.body()));
                    return;
                }

                JSONObject result = new JSONObject(response.body());
                JSONArray applications = result.getJSONArray("applications");
                ObservableList<ApplicationRow> rows = FXCollections.observableArrayList();

                for (int i = 0; i < applications.length(); i++) {
                    JSONObject application = applications.getJSONObject(i);
                    rows.add(new ApplicationRow(
                            String.valueOf(application.getInt("id")),
                            application.getString("title"),
                            application.getString("category"),
                            application.getString("priority"),
                            application.getString("status"),
                                application.optString("admin_response", ""),
                                application.optString("description", "")
                    ));
                }

                allApplications.setAll(rows);
                applyApplicationFilter();
            }, "Не вдалося завантажити заявки.");

        } catch (Exception e) {

            e.printStackTrace();

            showError(
                    "Не вдалося завантажити заявки."
            );
        }
    }


    private void updateHome() {

        if (welcomeLabel != null) {

            if (currentUserName != null) {

                welcomeLabel.setText(
                        "Вітаємо, "
                                + currentUserName
                                + "!"
                );

            } else {

                welcomeLabel.setText(
                        "Вітаємо!"
                );
            }
        }


        loadHomeStatistics();
    }


    private void loadHomeStatistics() {

        if (currentUserId == null) {

            if (totalApplicationsLabel != null) {
                totalApplicationsLabel.setText("0");
            }

            if (processingApplicationsLabel != null) {
                processingApplicationsLabel.setText("0");
            }

            if (resolvedApplicationsLabel != null) {
                resolvedApplicationsLabel.setText("0");
            }

            return;
        }


        try {

            String encodedUserId =
                    URLEncoder.encode(
                            currentUserId,
                            StandardCharsets.UTF_8
                    );

            String encodedFirebaseUid = currentUserFirebaseUid == null
                    ? ""
                    : URLEncoder.encode(
                            currentUserFirebaseUid,
                            StandardCharsets.UTF_8
                    );

            String query = "?user_id=" + encodedUserId;
            if (currentUserFirebaseUid != null && !currentUserFirebaseUid.isBlank()) {
                query += "&firebase_uid=" + encodedFirebaseUid;
            }

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            SERVER_URL
                                                    + "/api/applications"
                                                    + query
                                    )
                            )
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();


            sendRequest(request, response -> {
                if (response.statusCode() != 200) {
                    return;
                }

                JSONArray applications = new JSONObject(response.body())
                        .getJSONArray("applications");
                int total = applications.length();
                int processing = 0;
                int resolved = 0;

                for (int i = 0; i < applications.length(); i++) {
                    String status = applications.getJSONObject(i).getString("status");
                    if (status.equals("В обробці")) {
                        processing++;
                    }
                    if (status.equals("Виконано") || status.equals("Вирішено")) {
                        resolved++;
                    }
                }

                if (totalApplicationsLabel != null) {
                    totalApplicationsLabel.setText(String.valueOf(total));
                }
                if (processingApplicationsLabel != null) {
                    processingApplicationsLabel.setText(String.valueOf(processing));
                }
                if (resolvedApplicationsLabel != null) {
                    resolvedApplicationsLabel.setText(String.valueOf(resolved));
                }
            }, "Не вдалося завантажити статистику.");

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private String escapeJson(String text) {

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

        private void sendRequest(
                        HttpRequest request,
                        Consumer<HttpResponse<String>> onResponse,
                        String errorMessage
            ) {
                sendRequest(request, onResponse, errorMessage, () -> { });
            }

            private void sendRequest(
                    HttpRequest request,
                    Consumer<HttpResponse<String>> onResponse,
                    String errorMessage,
                    Runnable onFinished
        ) {
                HTTP_CLIENT.sendAsync(
                                                request,
                                                HttpResponse.BodyHandlers.ofString()
                                )
                                .whenComplete((response, error) -> Platform.runLater(() -> {
                                        try {
                                                if (error != null) {
                                                        Throwable cause = error.getCause() == null
                                                                ? error
                                                                : error.getCause();
                                                        showError(
                                                                errorMessage
                                                                        + "\n\n"
                                                                        + cause.getClass().getSimpleName()
                                                                        + ": "
                                                                        + cause.getMessage()
                                                        );
                                                        return;
                                                }

                                                onResponse.accept(response);
                                        } catch (Exception e) {
                                                e.printStackTrace();
                                                showError("Не вдалося обробити відповідь сервера.");
                                        } finally {
                                                onFinished.run();
                                        }
                                }));
        }


    private String getServerError(String body) {

        try {

            JSONObject json =
                    new JSONObject(body);

            if (json.has("error")) {

                return json.getString(
                        "error"
                );
            }

        } catch (Exception ignored) {
        }


        return "Сервер повернув помилку:\n\n"
                + body;
    }


    private void showInfo(String message) {
        showAlert(Alert.AlertType.INFORMATION, message);
    }


    private void showWarning(String message) {
        showAlert(Alert.AlertType.WARNING, message);
    }


    private void showError(String message) {
        showAlert(Alert.AlertType.ERROR, message);
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(Main.getStage());
        alert.initModality(Modality.WINDOW_MODAL);
        alert.setTitle("RequestFlow");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }


    public static class ApplicationRow {

        private final SimpleStringProperty id;
        private final SimpleStringProperty title;
        private final SimpleStringProperty category;
        private final SimpleStringProperty priority;
        private final SimpleStringProperty status;
        private final SimpleStringProperty response;
        private final SimpleStringProperty description;


        public ApplicationRow(
                String id,
                String title,
                String category,
                String priority,
                String status,
                String response,
                String description
        ) {

            this.id =
                    new SimpleStringProperty(id);

            this.title =
                    new SimpleStringProperty(title);

            this.category =
                    new SimpleStringProperty(category);

            this.priority =
                    new SimpleStringProperty(priority);

            this.status =
                    new SimpleStringProperty(status);

            this.response =
                    new SimpleStringProperty(response);

                this.description =
                    new SimpleStringProperty(description);
        }


        public SimpleStringProperty idProperty() {
            return id;
        }


        public SimpleStringProperty titleProperty() {
            return title;
        }


        public SimpleStringProperty categoryProperty() {
            return category;
        }


        public SimpleStringProperty priorityProperty() {
            return priority;
        }


        public SimpleStringProperty statusProperty() {
            return status;
        }

        public SimpleStringProperty responseProperty() {
            return response;
        }

        public SimpleStringProperty descriptionProperty() {
            return description;
        }
    }
}