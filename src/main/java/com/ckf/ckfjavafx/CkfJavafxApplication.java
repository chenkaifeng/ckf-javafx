package com.ckf.ckfjavafx;

import com.ckf.ckfjavafx.event.WindowHidingEvent;
import com.ckf.ckfjavafx.module.CustomSplash;
import com.ckf.ckfjavafx.module.MainStageView;
import de.felixroske.jfxsupport.AbstractFxmlView;
import de.felixroske.jfxsupport.AbstractJavaFxApplicationSupport;
import de.felixroske.jfxsupport.FXMLView;
import de.felixroske.jfxsupport.GUIState;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.util.ReflectionUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@SpringBootApplication(exclude = {RestTemplateAutoConfiguration.class, ErrorMvcAutoConfiguration.class})
public class CkfJavafxApplication extends AbstractJavaFxApplicationSupport implements ApplicationContextAware, DisposableBean {
	private static ServerSocket monitor; // ?????????????????????
	private static final int defaultMonitorPort = 19002; // ??????????????????
	private static volatile ApplicationContext context; // spring context
	private static final UUID instanceId = UUID.randomUUID(); // ????????????
	private static final EventBus defaultEventBus = EventBus.getDefault(); // ????????????
	private static final CustomSplash splash = new CustomSplash();
	public static final List<Image> appIcons = Collections.singletonList(new Image(Objects.requireNonNull(CkfJavafxApplication.class.getResourceAsStream("/image/logo.png"))));
	public static final AtomicBoolean upgrading = new AtomicBoolean(false); // ??????????????????

	static {
		// ????????????????????????????????????
		checkInstanceRunnable();
	}

	public static void main(String[] args) {
		launch(CkfJavafxApplication.class, MainStageView.class, splash, args);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void start(Stage stage) throws Exception {
		log.info("[{}]ckf??????????????????", instanceId);
		SwingUtilities.invokeAndWait(this::installSystemTray);
		Platform.setImplicitExit(false);
		stage.setOnCloseRequest(e -> {
			// ?????????????????????????????????????????????????????????????????????????????????????????????
			if (!isTrayIconAvailable()) {
				stopApplication();
			}
		});
		GUIState.setStage(stage);
		Stage splashStage = new Stage(StageStyle.TRANSPARENT);
		if (splash.visible()) {
			splash.setStage(splashStage);
			splashStage.setScene(new Scene(splash.getParent(), Color.TRANSPARENT));
			splashStage.getIcons().addAll(appIcons);
			splashStage.initStyle(StageStyle.TRANSPARENT);
			beforeShowingSplash(splashStage);
			splashStage.show();
		}

		final Field superField = ReflectionUtils.findField(AbstractJavaFxApplicationSupport.class, null, CompletableFuture.class);
		Objects.requireNonNull(superField).setAccessible(true);
		final CompletableFuture<Runnable> splashIsShowing = (CompletableFuture<Runnable>) ReflectionUtils.getField(superField, this);
		Objects.requireNonNull(splashIsShowing).complete(() -> {
			GUIState.getStage().initStyle(StageStyle.DECORATED);
			beforeInitialView(GUIState.getStage(), (ConfigurableApplicationContext) context);
			showView(MainStageView.class);
			if (splash.visible()) {
				splash.close();
			}
		});
	}

	@Override
	public void beforeInitialView(Stage stage, ConfigurableApplicationContext ctx) {
		stage.setTitle("ckf????????????");
		stage.setResizable(true);
	}

	@Override
	public void stop() throws Exception {
		log.info("[{}]ckf??????????????????", instanceId);
		SwingUtilities.invokeAndWait(this::removeSystemTray);
		Platform.setImplicitExit(true);
		closeStages();
		super.stop();
		IOUtils.closeQuietly(monitor);
	}

	public static void stopApplication() {
		if (Platform.isFxApplicationThread()) {
			try {
				Platform.exit();
			} catch (Exception ignored) {
				forceExit();
			}
		} else {
			Platform.runLater(CkfJavafxApplication::stopApplication);
		}
	}

	public static void showView(final Class<? extends AbstractFxmlView> window, final Modality mode, final Object params, final Consumer<Stage> decorator) {
		final AbstractFxmlView view = context.getBean(window);
		final FXMLView annotation = window.getAnnotation(FXMLView.class);
		final Stage newStage = new Stage();
		final Scene newScene = Optional.ofNullable(view.getView().getScene()).orElseGet(() -> {
			Scene scene = new Scene(view.getView());
			scene.setFill(null);
			return scene;
		});

		if (!defaultEventBus.isRegistered(view.getPresenter()) && hasSubscribe(view.getPresenter())) {
			defaultEventBus.register(view.getPresenter());
			log.info("Event bus registered???{}", view.getPresenter().getClass());
		}

		newStage.setScene(newScene);
		newStage.initModality(mode);
		newStage.initOwner(getStage());
		newStage.setTitle(annotation.title());
		newStage.initStyle(StageStyle.valueOf(annotation.stageStyle()));
		newStage.setOnHiding(event -> EventBus.getDefault().post(new WindowHidingEvent(view)));
		newStage.setOnShown(event -> {
			if (params != null) {
				log.debug("???????????????{}", params);
				defaultEventBus.post(params);
			}
		});
		if (decorator != null) {
			decorator.accept(newStage);
		}

		newStage.showAndWait();
	}

	public static boolean hasSubscribe(Object object) {
		Method[] subscribes = MethodUtils.getMethodsWithAnnotation(object.getClass(), Subscribe.class);
		return subscribes != null && subscribes.length > 0;
	}

	@Override
	public Collection<Image> loadDefaultIcons() {
		return appIcons;
	}

	public static void runInBackground(final boolean hid) {
		if (Platform.isFxApplicationThread()) {
			if (splash.visible()) {
				// ???????????????????????????????????????
				return;
			}
			if (hid) {
				// ???????????????????????????????????????????????????????????????????????????????????????????????????????????????
				GUIState.getStage().setIconified(true);
				if (isTrayIconAvailable()) {
					GUIState.getStage().hide();
					displayMessageOnTray("ckf????????????");
				}
			} else {
				GUIState.getStage().show();
				GUIState.getStage().setIconified(false);
			}
		} else {
			Platform.runLater(() -> runInBackground(hid));
		}
	}

	public static void displayMessageOnTray(final String message) {
		if (StringUtils.isBlank(message)) {
			return;
		}
		if (isTrayIconAvailable()) {
			TrayIcon trayIcon = (TrayIcon) GUIState.getStage().getUserData();
			trayIcon.displayMessage("ckf????????????", message, TrayIcon.MessageType.INFO);
		}
	}

	/**
	 * ????????????????????????
	 */
	private void installSystemTray() {
		if (SystemTray.isSupported()) {
			try {
				log.info("??????????????????");
				final SystemTray systemTray = SystemTray.getSystemTray();
				final BufferedImage trayImage = ImageIO.read(Objects.requireNonNull(CkfJavafxApplication.class.getResourceAsStream("/image/tray.png")));
				final PopupMenu popupMenu = new PopupMenu();
				final MenuItem exitMenuItem = new MenuItem("Exit");
				exitMenuItem.addActionListener(e -> stopApplication());
				popupMenu.add(exitMenuItem);
				final TrayIcon trayIcon = new TrayIcon(trayImage, "ckf????????????????????????????????????", popupMenu);
				trayIcon.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						if (e.getButton() == MouseEvent.BUTTON1 && 2 == e.getClickCount()) {
							runInBackground(false);
						}
					}
				});
				systemTray.add(trayIcon);
				Platform.runLater(() -> GUIState.getStage().setUserData(trayIcon));
			} catch (AWTException | IOException | RuntimeException e) {
				log.error("[{}]ckf????????????????????????????????????", instanceId);
			}
		}
	}

	/**
	 * ????????????????????????
	 */
	private void removeSystemTray() {
		if (isTrayIconAvailable()) {
			log.info("??????????????????");
			SystemTray systemTray = SystemTray.getSystemTray();
			TrayIcon trayIcon = (TrayIcon) GUIState.getStage().getUserData();
			systemTray.remove(trayIcon);
			GUIState.getStage().setUserData(null);
		}
	}

	/**
	 * ????????????????????????
	 * @return
	 */
	public static boolean isTrayIconAvailable() {
		return SystemTray.isSupported() && (GUIState.getStage().getUserData() instanceof TrayIcon);
	}

	private void closeStages() {
		Optional.ofNullable(splash.getStage()).ifPresent(Stage::close);
		Optional.ofNullable(GUIState.getStage()).ifPresent(Stage::close);
	}

	private static void checkInstanceRunnable() {
		try {
			monitor = new ServerSocket(defaultMonitorPort, 1);
			monitor.setSoTimeout(500);
		} catch (Exception e) {
			log.warn("[{}]ckf?????????????????????????????????[{}]?????????", instanceId, defaultMonitorPort);
			forceExit();
		}
	}

	private synchronized static void setContext(ApplicationContext context) {
		CkfJavafxApplication.context = context;
	}

	public static ApplicationContext getContext() {
		return context;
	}

	@Override
	public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
		setContext(context);
	}

	@Override
	public void destroy() {
		// ??????spring context????????????????????????UI
		final Thread thread = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					TimeUnit.MILLISECONDS.sleep(100);
				} catch (InterruptedException e) {
					forceExit();
				}
				final ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) context;
				if (!ctx.isActive() && !ctx.isRunning()) {
					if (!Platform.isImplicitExit()) {
						Platform.setImplicitExit(true);
						Platform.runLater(this::closeStages);
					}
				}
			}
		}, "ShutdownThread");
		thread.setDaemon(true);
		thread.start();
	}

	private static void forceExit() {
		System.exit(1);
	}
}
