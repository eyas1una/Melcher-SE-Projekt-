package com.group_2.ui.core;

import com.group_2.dto.cleaning.RoomDTO;
import com.group_2.dto.core.UserSessionDTO;
import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.dto.core.WgDetailsViewDTO;
import com.group_2.service.core.CoreViewService;
import com.group_2.service.core.HouseholdSetupService;
import com.group_2.service.core.WGService;
import com.group_2.util.SessionManager;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Controller for the WG settings view. Shows rooms, members, invitation code,
 * and admin controls.
 */
@Component
public class SettingsController extends Controller {

    private final SessionManager sessionManager;
    private final WGService wgService;
    private final HouseholdSetupService householdSetupService;
    private final CoreViewService coreViewService;

    @Autowired
    private ApplicationContext applicationContext;

    @FXML
    private Text wgNameHeader;
    @FXML
    private Text inviteCodeText;
    @FXML
    private Text roomCountText;
    @FXML
    private Text memberCountText;
    @FXML
    private VBox roomsBox;
    @FXML
    private VBox membersBox;
    @FXML
    private VBox adminCard;
    @FXML
    private javafx.scene.control.Button addRoomButton;

    private WgDetailsViewDTO currentWg;
    private Long currentUserId;

    @Autowired
    public SettingsController(SessionManager sessionManager, WGService wgService,
            HouseholdSetupService householdSetupService, CoreViewService coreViewService) {
        this.sessionManager = sessionManager;
        this.wgService = wgService;
        this.householdSetupService = householdSetupService;
        this.coreViewService = coreViewService;
    }

    public void initView() {
        sessionManager.refreshCurrentUser();
        loadWGData();
    }

    private void loadWGData() {
        UserSessionDTO session = sessionManager.getCurrentUserSession().orElse(null);
        if (session == null) {
            currentWg = null;
            currentUserId = null;
            return;
        }
        currentUserId = session.userId();
        if (session.wgId() == null) {
            currentWg = null;
            showWarningAlert("No WG", "You are not a member of any WG.", getOwnerWindow(wgNameHeader));
            return;
        }

        WgDetailsViewDTO wg = coreViewService.getWgDetails(session.wgId());
        if (wg == null) {
            currentWg = null;
            showWarningAlert("No WG", "You are not a member of any WG.", getOwnerWindow(wgNameHeader));
            return;
        }
        currentWg = wg;

        // Header
        wgNameHeader.setText(wg.name());
        inviteCodeText.setText(wg.inviteCode());

        // Check if admin
        boolean isAdmin = isCurrentUserAdmin();
        adminCard.setVisible(isAdmin);
        adminCard.setManaged(isAdmin);

        // Show/hide Add Room button based on admin status
        if (addRoomButton != null) {
            addRoomButton.setVisible(isAdmin);
            addRoomButton.setManaged(isAdmin);
        }

        // Load rooms
        loadRooms(wg.rooms(), isAdmin);

        // Load members
        loadMembers(wg.members());
    }

    private void loadRooms(java.util.List<RoomDTO> rooms, boolean isAdmin) {
        roomsBox.getChildren().clear();
        if (!rooms.isEmpty()) {
            roomCountText.setText(rooms.size() + " room" + (rooms.size() > 1 ? "s" : "") + " in your WG");
            for (RoomDTO room : rooms) {
                roomsBox.getChildren().add(createRoomListItem(room, isAdmin));
            }
        } else {
            roomCountText.setText("No rooms yet");
            Text noRooms = new Text(isAdmin ? "Add your first room above!" : "No rooms have been added yet.");
            noRooms.getStyleClass().add("list-item-subtitle");
            roomsBox.getChildren().add(noRooms);
        }
    }

    private void loadMembers(java.util.List<UserSummaryDTO> members) {
        membersBox.getChildren().clear();
        if (members != null && !members.isEmpty()) {
            memberCountText.setText(
                    members.size() + " member" + (members.size() > 1 ? "s" : "") + " in your WG");
            for (UserSummaryDTO user : members) {
                membersBox.getChildren().add(createMemberListItem(user));
            }
        } else {
            memberCountText.setText("No members yet");
        }
    }

    private HBox createRoomListItem(RoomDTO room, boolean isAdmin) {
        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("list-item");
        item.setPadding(new Insets(10, 15, 10, 15));

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().addAll("avatar", "avatar-green");
        Text iconText = new Text("??");
        iconText.getStyleClass().add("avatar-text");
        iconPane.getChildren().add(iconText);

        VBox info = new VBox(3);
        javafx.scene.layout.HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
        Text nameText = new Text(room.name());
        nameText.getStyleClass().add("list-item-title");
        Text idText = new Text("Room ID: " + room.id());
        idText.getStyleClass().add("list-item-subtitle");
        info.getChildren().addAll(nameText, idText);

        item.getChildren().addAll(iconPane, info);

        // Admin actions for rooms
        if (isAdmin) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_RIGHT);

            javafx.scene.control.Button editBtn = new javafx.scene.control.Button("Edit");
            editBtn.setTooltip(new javafx.scene.control.Tooltip("Edit Room"));
            editBtn.getStyleClass().addAll("table-action-button", "table-edit-button");
            editBtn.setOnAction(e -> editRoom(room));
            actions.getChildren().add(editBtn);

            javafx.scene.control.Button deleteBtn = new javafx.scene.control.Button("Delete");
            deleteBtn.setTooltip(new javafx.scene.control.Tooltip("Delete Room"));
            deleteBtn.getStyleClass().addAll("table-action-button", "table-delete-button");
            deleteBtn.setOnAction(e -> deleteRoom(room));
            actions.getChildren().add(deleteBtn);

            item.getChildren().add(actions);
        }

        return item;
    }

    private HBox createMemberListItem(UserSummaryDTO user) {
        boolean currentUserIsAdmin = isCurrentUserAdmin();
        boolean isMemberAdmin = currentWg != null && currentWg.admin() != null
                && currentWg.admin().id() != null && currentWg.admin().id().equals(user.id());
        boolean isSelf = currentUserId != null && user.id() != null && user.id().equals(currentUserId);

        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("list-item");
        item.setPadding(new Insets(10, 15, 10, 15));

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("avatar");
        String initial = user.name() != null && !user.name().isEmpty()
                ? user.name().substring(0, 1).toUpperCase()
                : "?";
        Text avatarText = new Text(initial);
        avatarText.getStyleClass().add("avatar-text");
        avatar.getChildren().add(avatarText);

        VBox info = new VBox(3);
        javafx.scene.layout.HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
        String fullName = user.displayName();
        Text nameText = new Text(fullName + (isMemberAdmin ? " (Admin)" : "") + (isSelf ? " (You)" : ""));
        nameText.getStyleClass().add("list-item-title");
        Text emailText = new Text(user.email() != null ? user.email() : "No email");
        emailText.getStyleClass().add("list-item-subtitle");
        info.getChildren().addAll(nameText, emailText);

        item.getChildren().addAll(avatar, info);

        // Admin actions (only show if current user is admin and not viewing themselves)
        if (currentUserIsAdmin && !isSelf) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_RIGHT);

            if (!isMemberAdmin) {
                // Make Admin button
                javafx.scene.control.Button makeAdminBtn = new javafx.scene.control.Button("Make Admin");
                makeAdminBtn.setTooltip(new javafx.scene.control.Tooltip("Make Admin"));
                makeAdminBtn.getStyleClass().addAll("table-action-button", "table-warning-button");
                makeAdminBtn.setOnAction(e -> handleMakeAdmin(user));
                actions.getChildren().add(makeAdminBtn);
            }

            // Remove button
            javafx.scene.control.Button removeBtn = new javafx.scene.control.Button("Remove");
            removeBtn.setTooltip(new javafx.scene.control.Tooltip("Remove from WG"));
            removeBtn.getStyleClass().addAll("table-action-button", "table-delete-button");
            removeBtn.setOnAction(e -> handleRemoveMember(user));
            actions.getChildren().add(removeBtn);

            item.getChildren().add(actions);
        }

        return item;
    }

    private void handleMakeAdmin(UserSummaryDTO user) {
        if (currentWg == null || currentUserId == null)
            return;

        String userName = user.displayName();

        boolean confirmed = showConfirmDialog("Transfer Admin Rights", "Make " + userName + " the new admin?",
                "You will no longer be the admin of this WG.", getOwnerWindow(membersBox));

        if (confirmed) {
            try {
                wgService.updateWG(currentWg.id(), currentWg.name(), user.id());
                sessionManager.refreshCurrentUser();
                loadWGData();
                showSuccessAlert("Success", userName + " is now the admin!", getOwnerWindow(membersBox));
            } catch (Exception e) {
                showErrorAlert("Error", "Failed to transfer admin rights: " + e.getMessage(),
                        getOwnerWindow(membersBox));
            }
        }
    }

    private void handleRemoveMember(UserSummaryDTO user) {
        if (currentWg == null || currentUserId == null)
            return;

        String userName = user.displayName();

        boolean confirmed = showConfirmDialog("Remove Member", "Remove " + userName + " from the WG?",
                "This action cannot be undone.", getOwnerWindow(membersBox));

        if (confirmed) {
            try {
                wgService.removeMitbewohner(currentWg.id(), user.id());
                sessionManager.refreshCurrentUser();
                loadWGData();
                showSuccessAlert("Success", userName + " has been removed from the WG.", getOwnerWindow(membersBox));
            } catch (Exception e) {
                showErrorAlert("Error", "Failed to remove member: " + e.getMessage(), getOwnerWindow(membersBox));
            }
        }
    }

    @FXML
    public void copyInviteCode() {
        if (currentWg != null && currentWg.inviteCode() != null) {
            String code = currentWg.inviteCode();
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(code);
            clipboard.setContent(content);
            showSuccessAlert("Copied!", "Invitation code copied to clipboard: " + code, getOwnerWindow(inviteCodeText));
        }
    }

    @FXML
    public void addRoom() {
        if (currentWg == null || currentUserId == null)
            return;

        // Only admin can add rooms
        if (!isCurrentUserAdmin()) {
            showWarningAlert("Permission Denied", "Only the admin can add rooms.", getOwnerWindow(roomsBox));
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        configureDialogOwner(dialog, getOwnerWindow(roomsBox));
        styleDialog(dialog);
        dialog.setTitle("Add Room");
        dialog.setHeaderText("Add a new room to your WG");
        dialog.setContentText("Room name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(roomName -> {
            if (!roomName.trim().isEmpty()) {
                try {
                    RoomDTO newRoom = householdSetupService.createRoomDTO(roomName.trim());
                    wgService.addRoomById(currentWg.id(), newRoom.id());
                    sessionManager.refreshCurrentUser();
                    loadWGData();
                    showSuccessAlert("Success", "Room '" + roomName + "' added!", getOwnerWindow(roomsBox));
                } catch (Exception e) {
                    showErrorAlert("Error", "Failed to add room: " + e.getMessage(), getOwnerWindow(roomsBox));
                }
            }
        });
    }

    private void editRoom(RoomDTO room) {
        if (currentWg == null || currentUserId == null)
            return;

        TextInputDialog dialog = new TextInputDialog(room.name());
        configureDialogOwner(dialog, getOwnerWindow(roomsBox));
        styleDialog(dialog);
        dialog.setTitle("Edit Room");
        dialog.setHeaderText("Edit room name");
        dialog.setContentText("Room name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                try {
                    householdSetupService.updateRoom(room.id(), newName.trim());
                    sessionManager.refreshCurrentUser();
                    loadWGData();
                    showSuccessAlert("Success", "Room renamed to '" + newName + "'!", getOwnerWindow(roomsBox));
                } catch (Exception e) {
                    showErrorAlert("Error", "Failed to rename room: " + e.getMessage(), getOwnerWindow(roomsBox));
                }
            }
        });
    }

    private void deleteRoom(RoomDTO room) {
        if (currentWg == null || currentUserId == null)
            return;

        boolean confirmed = showConfirmDialog("Delete Room", "Delete room '" + room.name() + "'?",
                "This will also remove any cleaning tasks and templates associated with this room.",
                getOwnerWindow(roomsBox));

        if (confirmed) {
            try {
                // Use the consolidated deletion method that handles everything in one
                // transaction
                householdSetupService.deleteRoomById(room.id(), currentWg.id());
                sessionManager.refreshCurrentUser();
                loadWGData();
                showSuccessAlert("Success", "Room '" + room.name() + "' deleted!", getOwnerWindow(roomsBox));
            } catch (Exception e) {
                showErrorAlert("Error", "Failed to delete room: " + e.getMessage(), getOwnerWindow(roomsBox));
            }
        }
    }

    @FXML
    public void editWgName() {
        if (currentWg == null || currentUserId == null)
            return;

        TextInputDialog dialog = new TextInputDialog(currentWg.name());
        configureDialogOwner(dialog, getOwnerWindow(wgNameHeader));
        styleDialog(dialog);
        dialog.setTitle("Edit WG Name");
        dialog.setHeaderText("Change your WG name");
        dialog.setContentText("New name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                try {
                    Long adminId = currentWg.admin() != null ? currentWg.admin().id() : null;
                    wgService.updateWG(currentWg.id(), newName.trim(), adminId);
                    sessionManager.refreshCurrentUser();
                    loadWGData();
                    showSuccessAlert("Success", "WG name updated!", getOwnerWindow(wgNameHeader));
                } catch (Exception e) {
                    showErrorAlert("Error", "Failed to update WG name: " + e.getMessage(),
                            getOwnerWindow(wgNameHeader));
                }
            }
        });
    }

    @FXML
    public void deleteWG() {
        if (currentWg == null || currentUserId == null)
            return;

        boolean confirmed = showConfirmDialog("Delete WG", "Are you sure you want to delete this WG?",
                "This action cannot be undone. All members will be removed.", getOwnerWindow(wgNameHeader));

        if (confirmed) {
            try {
                wgService.deleteWG(currentWg.id());
                sessionManager.refreshCurrentUser();
                showSuccessAlert("Success", "WG deleted.", getOwnerWindow(wgNameHeader));
                loadScene(wgNameHeader.getScene(), "/core/no_wg.fxml");
            } catch (Exception e) {
                showErrorAlert("Error", "Failed to delete WG: " + e.getMessage(), getOwnerWindow(wgNameHeader));
            }
        }
    }

    @FXML
    public void backToHome() {
        loadScene(wgNameHeader.getScene(), "/core/main_screen.fxml");
        javafx.application.Platform.runLater(() -> {
            MainScreenController mainController = applicationContext.getBean(MainScreenController.class);
            mainController.initView();
        });
    }

    private boolean isCurrentUserAdmin() {
        return currentWg != null && currentUserId != null && currentWg.admin() != null
                && currentWg.admin().id() != null && currentWg.admin().id().equals(currentUserId);
    }
}
