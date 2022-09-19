package com.ckf.ckfjavafx.module;

import com.ckf.ckfjavafx.CkfJavafxApplication;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Java FX view controller 的公共方法封装
 */
public abstract class AbstractController {
    private void setIcons(Alert alert) {
        Stage stage = getWindowAsStage(alert.getDialogPane());
        stage.getIcons().addAll(CkfJavafxApplication.appIcons);
    }

    public void alert(Window owner, String message, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType, message);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.initOwner(owner);
        setIcons(alert);
        alert.showAndWait();
    }

    public void showHint(Window owner, String message) {
        alert(owner, message, Alert.AlertType.INFORMATION);
    }

    public void showError(Window owner, String message) {
        alert(owner, message, Alert.AlertType.ERROR);
    }

    public void showWarning(Window owner, String message) {
        alert(owner, message, Alert.AlertType.WARNING);
    }

    public void showConfirm(Window owner, String message, EventHandler<ActionEvent> okCallback) {
        showConfirm(owner, message, okCallback, null);
    }

    public void showConfirm(Window owner, String message, EventHandler<ActionEvent> okCallback, EventHandler<ActionEvent> cancelCallback) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message);
        alert.initOwner(owner);
        setIcons(alert);
        Optional<ButtonType> result = alert.showAndWait();
        if (okCallback != null && result.isPresent() && result.get() == ButtonType.OK) {
            okCallback.handle(new ActionEvent(owner, owner));
        }
        if (cancelCallback != null && (!result.isPresent() || result.get() == ButtonType.CANCEL)) {
            cancelCallback.handle(new ActionEvent(owner, owner));
        }
    }

    public void closeWindow(Control control) {
        getWindow(control).hide();
    }

    public Window getWindow(Node node) {
        return node.getScene().getWindow();
    }

    public Stage getWindowAsStage(Node node) {
        Window window = getWindow(node);
        return window instanceof Stage ? (Stage) window : null;
    }
}
