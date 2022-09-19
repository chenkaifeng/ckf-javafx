package com.ckf.ckfjavafx.module;

import com.ckf.ckfjavafx.CkfJavafxApplication;
import com.ckf.ckfjavafx.domain.FileConfig;
import com.ckf.ckfjavafx.event.ConfigChangeEvent;
import com.ckf.ckfjavafx.util.ConfigUtils;
import com.ckf.ckfjavafx.util.SaveDirUtils;
import de.felixroske.jfxsupport.FXMLController;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import lombok.extern.slf4j.Slf4j;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.springframework.context.annotation.Lazy;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

@Lazy
@FXMLController
@Slf4j
public class MainController extends AbstractController implements Initializable {

    @FXML
    public Button importButton;

    @FXML
    public Button exitButton;

    @FXML
    public TableView<FileConfig> certTableView;

    @FXML
    public TableColumn<FileConfig, String> nameCol;

    @FXML
    public TableColumn<FileConfig, String> importTimeCol;

    @FXML
    public TableColumn<FileConfig, Button> opCol;

    @PostConstruct
    public void init() {
        log.info("初始化主页面");
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

        reloadData();

        register();
    }

    private void reloadData() {
        Ini ini = ConfigUtils.readConfig(SaveDirUtils.getConfigFileSavePath());
        if (!ini.isEmpty()) {
            List<FileConfig> fileConfigList = new ArrayList<>(ini.size());
            ini.forEach((key, value) -> fileConfigList.add(buildConfig(value)));
            log.info("读取文件配置共计{}个", ini.size());
            certTableView.setItems(FXCollections.observableArrayList(fileConfigList));
        }
    }

    private FileConfig buildConfig(Profile.Section section) {
        FileConfig config = FileConfig.builder().build();
        section.to(config);
        return config;
    }

    private void register() {
        // 注册监听
        EventBus.getDefault().register(this);
    }

    private void binding() {
        final Tooltip exitButtonTooltip = new Tooltip("完全关闭进程");
        exitButtonTooltip.getStyleClass().addAll("h6", "text-default");
        Tooltip.install(exitButton, exitButtonTooltip);
        nameCol.setCellFactory(col -> {
            final TableCell<FileConfig, String> cell = new TableCell<FileConfig, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(item);
                }
            };
            cell.setAlignment(Pos.CENTER_LEFT);
            return cell;
        });
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        importTimeCol.setCellFactory(col -> {
            final TableCell<FileConfig, String> cell = new TableCell<FileConfig, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(item);
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });
        importTimeCol.setCellValueFactory(new PropertyValueFactory<>("importTime"));
        opCol.setCellFactory(col -> {
            final TableCell<FileConfig, Button> cell = new TableCell<FileConfig, Button>() {
                @Override
                protected void updateItem(Button item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        Button deleteButton = new Button("删除");
                        deleteButton.getStyleClass().addAll(Arrays.asList("btn", "btn-danger", "btn-sm"));
                        deleteButton.setOnAction(event -> showConfirm(getWindow(certTableView), "确定删除该文件吗？", e -> {
                            FileConfig config = certTableView.getItems().remove(getIndex());

                            ConfigUtils.removeValue(SaveDirUtils.getConfigFileSavePath(), config.getName());

                            EventBus.getDefault().post(new ConfigChangeEvent());
                            log.info("文件[{}]配置删除成功", config.getName());
                            showHint(getWindow(certTableView), String.format("文件[%s]删除成功", config.getName()));
                        }));
                        setGraphic(deleteButton);
                    }
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });
    }



    public void onImportButtonClicked(ActionEvent event) {
        if (event.getSource() == importButton) {
            CkfJavafxApplication.showView(ImportStageView.class, Modality.APPLICATION_MODAL, null, stage -> {
                stage.setResizable(false);
                stage.getIcons().addAll(CkfJavafxApplication.appIcons);
            });
        }
    }

    public void onExitButtonClicked(ActionEvent event) {
        if (event.getSource() == exitButton) {
            CkfJavafxApplication.stopApplication();
        }
    }

    /**
     * 监听文件更改事件，并动态刷新当前配置
     *
     * @param event 事件信息
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCertFileConfigChanged(ConfigChangeEvent event) {
        log.info("ConfigChangeEvent....");
        reloadData();
    }


}
