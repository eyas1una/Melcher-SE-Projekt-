package com.group_2.ui;

import com.group_2.service.WGService;
import com.group_2.service.UserService;
import com.group_2.service.RoomService;
import com.group_2.util.SessionManager;
import com.model.Room;
import com.model.User;
import com.model.WG;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.fxml.FXML;
import org.springframework.stereotype.Component;

/**
 * Controller for the main dashboard view.
 * Only shown when user is part of a WG.
 */
@Component
public class DashboardController extends Controller {

    private final UserService userService;
    private final WGService wgService;
    private final RoomService roomService;
    private final SessionManager sessionManager;

    // Sidebar elements
    @FXML
    private Button menuWgButton;
    @FXML
    private Button menuAccountButton;
    @FXML
    private Button menuRoomsButton;
    @FXML
    private Button menuMembersButton;
    @FXML
    private Text avatarInitial;
    @FXML
    private Text userNameText;
    @FXML
    private Text userEmailText;

    // Main content views
    @FXML
    private VBox wgDetailsView;
    @FXML
    private VBox accountView;
    @FXML
    private VBox roomsView;
    @FXML
    private VBox membersView;

    // WG Details view elements
    @FXML
    private Text wgNameText;
    @FXML
    private Text wgIdText;
    @FXML
    private Text adminText;
    @FXML
    private Text memberCountText;
    @FXML
    private Text roomCountText;
    @FXML
    private VBox mitbewohnerBox;
    @FXML
    private VBox roomsBox;

    // Account view elements
    @FXML
    private Text accountAvatarInitial;
    @FXML
    private Text accountNameText;
    @FXML
    private Text accountEmailText;
    @FXML
    private Text accountFullNameText;
    @FXML
    private Text accountEmailDisplayText;
    @FXML
    private Text accountWgStatusText;

    // Rooms view elements
    @FXML
    private VBox roomsListBox;

    // Members view elements
    @FXML
    private VBox membersListBox;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    public DashboardController(UserService userService, WGService wgService, RoomService roomService,
            SessionManager sessionManager) {
        this.userService = userService;
        this.wgService = wgService;
        this.roomService = roomService;
        this.sessionManager = sessionManager;
    }

    public void initView() {
        sessionManager.refreshCurrentUser();
        updateSidebarUserInfo();
        showWgView();
    }

    private void updateSidebarUserInfo() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            String initial = currentUser.getName() != null && !currentUser.getName().isEmpty()
                    ? currentUser.getName().substring(0, 1).toUpperCase()
                    : "?";
            avatarInitial.setText(initial);
            userNameText.setText(currentUser.getName() +
                    (currentUser.getSurname() != null ? " " + currentUser.getSurname() : ""));
            userEmailText.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "");
        }
    }

    @FXML
    public void refreshView() {
        sessionManager.refreshCurrentUser();
        updateSidebarUserInfo();
        showWgView();
    }

    private void resetAllViews() {
        wgDetailsView.setVisible(false);
        wgDetailsView.setManaged(false);
        accountView.setVisible(false);
        accountView.setManaged(false);
        roomsView.setVisible(false);
        roomsView.setManaged(false);
        membersView.setVisible(false);
        membersView.setManaged(false);
    }

    private void resetMenuStyles() {
        menuWgButton.getStyleClass().remove("menu-item-active");
        menuAccountButton.getStyleClass().remove("menu-item-active");
        menuRoomsButton.getStyleClass().remove("menu-item-active");
        menuMembersButton.getStyleClass().remove("menu-item-active");
    }

    @FXML
    public void showWgView() {
        resetAllViews();
        resetMenuStyles();
        menuWgButton.getStyleClass().add("menu-item-active");
        showWGDetails(sessionManager.getCurrentUser().getWg());
    }

    @FXML
    public void showAccountView() {
        resetAllViews();
        resetMenuStyles();
        menuAccountButton.getStyleClass().add("menu-item-active");

        accountView.setVisible(true);
        accountView.setManaged(true);

        User currentUser = sessionManager.getCurrentUser();
        String initial = currentUser.getName() != null && !currentUser.getName().isEmpty()
                ? currentUser.getName().substring(0, 1).toUpperCase()
                : "?";
        accountAvatarInitial.setText(initial);
        accountNameText.setText(currentUser.getName());
        accountEmailText.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "No email");

        String fullName = currentUser.getName() +
                (currentUser.getSurname() != null ? " " + currentUser.getSurname() : "");
        accountFullNameText.setText(fullName);
        accountEmailDisplayText.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "Not set");
        accountWgStatusText
                .setText(currentUser.getWg() != null ? "Member of: " + currentUser.getWg().name : "Not in a WG");
    }

    @FXML
    public void showRoomsView() {
        resetAllViews();
        resetMenuStyles();
        menuRoomsButton.getStyleClass().add("menu-item-active");

        roomsView.setVisible(true);
        roomsView.setManaged(true);

        User currentUser = sessionManager.getCurrentUser();
        roomsListBox.getChildren().clear();
        if (currentUser.getWg() != null && currentUser.getWg().rooms != null) {
            for (Room room : currentUser.getWg().rooms) {
                roomsListBox.getChildren().add(createRoomListItem(room));
            }
        } else {
            Text emptyText = new Text("No rooms available");
            emptyText.getStyleClass().add("card-subtitle");
            roomsListBox.getChildren().add(emptyText);
        }
    }

    @FXML
    public void showMembersView() {
        resetAllViews();
        resetMenuStyles();
        menuMembersButton.getStyleClass().add("menu-item-active");

        membersView.setVisible(true);
        membersView.setManaged(true);

        User currentUser = sessionManager.getCurrentUser();
        membersListBox.getChildren().clear();
        if (currentUser.getWg() != null && currentUser.getWg().mitbewohner != null) {
            for (User member : currentUser.getWg().mitbewohner) {
                membersListBox.getChildren().add(createMemberListItem(member));
            }
        } else {
            Text emptyText = new Text("No members available");
            emptyText.getStyleClass().add("card-subtitle");
            membersListBox.getChildren().add(emptyText);
        }
    }

    @FXML
    public void handleLogout() {
        loadScene(menuWgButton.getScene(), "/login.fxml");
    }

    @FXML
    public void backToHome() {
        loadScene(menuWgButton.getScene(), "/main_screen.fxml");
        javafx.application.Platform.runLater(() -> {
            MainScreenController mainScreenController = applicationContext.getBean(MainScreenController.class);
            mainScreenController.initView();
        });
    }

    @FXML
    public void showShoppingLists() {
        loadScene(menuWgButton.getScene(), "/shopping_list.fxml");
    }

    private void showWGDetails(WG wg) {
        resetAllViews();
        wgDetailsView.setVisible(true);
        wgDetailsView.setManaged(true);

        wgNameText.setText(wg.name);
        wgIdText.setText("Invite: " + wg.getInviteCode());
        adminText.setText("Admin: " + (wg.admin != null ? wg.admin.getName() : "Unknown"));

        int memberCount = wg.mitbewohner != null ? wg.mitbewohner.size() : 0;
        int roomCount = wg.rooms != null ? wg.rooms.size() : 0;
        memberCountText.setText(String.valueOf(memberCount));
        roomCountText.setText(String.valueOf(roomCount));

        mitbewohnerBox.getChildren().clear();
        if (wg.mitbewohner != null && !wg.mitbewohner.isEmpty()) {
            for (User u : wg.mitbewohner) {
                mitbewohnerBox.getChildren().add(createMemberListItem(u));
            }
        } else {
            Text emptyText = new Text("No members yet");
            emptyText.getStyleClass().add("card-subtitle");
            mitbewohnerBox.getChildren().add(emptyText);
        }

        roomsBox.getChildren().clear();
        if (wg.rooms != null && !wg.rooms.isEmpty()) {
            for (Room r : wg.rooms) {
                roomsBox.getChildren().add(createRoomListItem(r));
            }
        } else {
            Text emptyText = new Text("No rooms defined");
            emptyText.getStyleClass().add("card-subtitle");
            roomsBox.getChildren().add(emptyText);
        }
    }

    private HBox createMemberListItem(User user) {
        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("list-item");
        item.setPadding(new Insets(10, 15, 10, 15));

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("avatar");
        String initial = user.getName() != null && !user.getName().isEmpty()
                ? user.getName().substring(0, 1).toUpperCase()
                : "?";
        Text avatarText = new Text(initial);
        avatarText.getStyleClass().add("avatar-text");
        avatar.getChildren().add(avatarText);

        VBox info = new VBox(3);
        Text nameText = new Text(user.getName() +
                (user.getSurname() != null ? " " + user.getSurname() : ""));
        nameText.getStyleClass().add("list-item-title");
        Text emailText = new Text(user.getEmail() != null ? user.getEmail() : "No email");
        emailText.getStyleClass().add("list-item-subtitle");
        info.getChildren().addAll(nameText, emailText);

        item.getChildren().addAll(avatar, info);
        return item;
    }

    private HBox createRoomListItem(Room room) {
        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("list-item");
        item.setPadding(new Insets(10, 15, 10, 15));

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("avatar");
        iconPane.setStyle("-fx-background-color: linear-gradient(to bottom right, #10b981, #059669);");
        Text iconText = new Text("🚪");
        iconText.setStyle("-fx-font-size: 18px;");
        iconPane.getChildren().add(iconText);

        VBox info = new VBox(3);
        Text nameText = new Text(room.getName());
        nameText.getStyleClass().add("list-item-title");
        Text idText = new Text("Room ID: " + room.getId());
        idText.getStyleClass().add("list-item-subtitle");
        info.getChildren().addAll(nameText, idText);

        item.getChildren().addAll(iconPane, info);
        return item;
    }
}
