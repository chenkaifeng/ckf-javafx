package com.ckf.ckfjavafx.module;

import com.ckf.ckfjavafx.domain.FileConfig;
import com.ckf.ckfjavafx.event.ConfigChangeEvent;
import com.ckf.ckfjavafx.event.WindowHidingEvent;
import com.ckf.ckfjavafx.util.ConfigUtils;
import com.ckf.ckfjavafx.util.SaveDirUtils;
import de.felixroske.jfxsupport.FXMLController;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.springframework.context.annotation.Lazy;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

@Lazy
@FXMLController
@Slf4j
public class ImportController extends AbstractController implements Initializable {
    @FXML
    public Button selectButton;
    @FXML
    public Button confirmButton;
    @FXML
    public Button cancelButton;

    @PostConstruct
    public void init() {
        log.info("初始化文件导入页面");
    }

    @PreDestroy
    public void destroy() {
        // 解除监听
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        binding();

        register();
    }

    private void register() {
        // 注册监听
        EventBus.getDefault().register(this);
    }

    private void binding() {
        Tooltip tooltip = new Tooltip();
        tooltip.getStyleClass().addAll("h6", "text-default");
        tooltip.textProperty().bind(selectButton.textProperty());
        selectButton.setTooltip(tooltip);
    }

    /**
     * 监听窗口关闭事件
     *
     * @param event 事件信息
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWindowHiding(WindowHidingEvent event) {
        if (event.getView() != null && event.getView().getClass() == ImportStageView.class) {
            selectButton.setText(selectButton.getAccessibleText());
        }
    }

    public void onSelectButtonClicked(Event event) {
        if (event.getSource() == selectButton) {
            Window window = getWindow((Node) event.getSource());
            final FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择待加载文件");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("image Files", "*.jpg", "*.png")
            );
            File file = fileChooser.showOpenDialog(window);
            if (file != null) {
                selectButton.setText(file.getAbsolutePath());
            }
        }
    }

    public void onCancelButtonClicked(ActionEvent event) {
        if (event.getSource() == cancelButton) {
            closeWindow(cancelButton);
        }
    }


    public void onConfirmButtonClicked(ActionEvent event) {
        if (event.getSource() == confirmButton) {
            if (StringUtils.isBlank(selectButton.getText())) {
                log.error("加载失败，未选择文件");
                showError(getWindow(confirmButton), "请选择文件");
                return;
            }
            try {
                File srcFile = new File(selectButton.getText());
                File destFile = new File(SaveDirUtils.getFileSaveDir().concat(File.separator).concat(srcFile.getName()));
                FileUtils.copyFile(srcFile, destFile);

                FileConfig fileConfig = FileConfig.builder()
                        .name(srcFile.getName())
                        .path(destFile.getAbsolutePath())
                        .importTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .build();
                ConfigUtils.write(SaveDirUtils.getConfigFileSavePath(), fileConfig.getName(), fileConfig);

                EventBus.getDefault().post(new ConfigChangeEvent());
                showHint(getWindow(selectButton), "文件加载成功");
                closeWindow((Control) event.getSource());
            } catch (Exception e) {
                log.error("加载失败，原因：", e);
                showError(getWindow(selectButton), e.getMessage());
            }
        }
    }
}
