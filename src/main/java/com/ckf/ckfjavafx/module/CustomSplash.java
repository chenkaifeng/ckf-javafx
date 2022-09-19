package com.ckf.ckfjavafx.module;

import de.felixroske.jfxsupport.SplashScreen;
import javafx.stage.Stage;

public class CustomSplash extends SplashScreen {
    private boolean visible = true;
    private Stage stage;

    public Stage getStage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public boolean visible() {
        return visible;
    }

    public void close() {
        this.visible = false;
        if (this.stage != null) {
            this.stage.hide();
            this.stage.setScene(null);
            this.stage = null;
        }
    }

    @Override
    public String getImagePath() {
        return "/image/banner.png";
    }
}
