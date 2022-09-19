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
	private static ServerSocket monitor; // 进程互斥监视器
	private static final int defaultMonitorPort = 19002; // 互斥监视端口
	private static volatile ApplicationContext context; // spring context
	private static final UUID instanceId = UUID.randomUUID(); // 进程标识
	private static final EventBus defaultEventBus = EventBus.getDefault(); // 事件总线
	private static final CustomSplash splash = new CustomSplash();
	public static final List<Image> appIcons = Collections.singletonList(new Image(Objects.requireNonNull(CkfJavafxApplication.class.getResourceAsStream("/image/logo.png"))));
	public static final AtomicBoolean upgrading = new AtomicBoolean(false); // 控件升级标识

	static {
		// 防止重复启动多个控件实例
		checkInstanceRunnable();
	}

	public static void main(String[] args) {
		launch(CkfJavafxApplication.class, MainStageView.class, splash, args);
	}

	/**
	 * 解决openjfx 8上运行报错 {@code java.lang.ClassNotFoundException: com.sun.deploy.uitoolkit.impl.fx.HostServicesFactory}
	 * 的问题，详见 https://github.com/javafxports/openjdk-jfx/issues/540，覆写了 {@link AbstractJavaFxApplicationSupport}
	 * 的 {@code start} 方法对 {@link javafx.application.HostServices} 的调用。
	 *
	 * @param stage 主场景
	 * @throws Exception 异常
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void start(Stage stage) throws Exception {
		log.info("[{}]ckf示例程序启动", instanceId);
		SwingUtilities.invokeAndWait(this::installSystemTray);
		Platform.setImplicitExit(false);
		stage.setOnCloseRequest(e -> {
			// 如果不支持系统托盘或托盘图标创建失败，则关闭窗口时直接退出程序
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
		stage.setTitle("ckf示例程序");
		stage.setResizable(true);
	}

	@Override
	public void stop() throws Exception {
		log.info("[{}]ckf示例程序关闭", instanceId);
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
			log.info("Event bus registered：{}", view.getPresenter().getClass());
		}

		newStage.setScene(newScene);
		newStage.initModality(mode);
		newStage.initOwner(getStage());
		newStage.setTitle(annotation.title());
		newStage.initStyle(StageStyle.valueOf(annotation.stageStyle()));
		newStage.setOnHiding(event -> EventBus.getDefault().post(new WindowHidingEvent(view)));
		newStage.setOnShown(event -> {
			if (params != null) {
				log.debug("跳转参数：{}", params);
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
				// 闪屏启动阶段不响应托盘事件
				return;
			}
			if (hid) {
				// 如果支持系统托盘，则隐藏窗口到系统托盘；如果不支持系统托盘，则将窗口最小化
				GUIState.getStage().setIconified(true);
				if (isTrayIconAvailable()) {
					GUIState.getStage().hide();
					displayMessageOnTray("ckf示例程序");
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
			trayIcon.displayMessage("ckf示例程序", message, TrayIcon.MessageType.INFO);
		}
	}

	/**
	 * 创建系统托盘图标
	 * 关于Linux上托盘图标不透明的问题是JDK6/7/8上的一个BUG，无法从Java代码层面修复，详见：
	 * 1）https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6453521
	 * 2) https://stackoverflow.com/questions/331407/java-trayicon-using-image-with-transparent-background
	 */
	private void installSystemTray() {
		if (SystemTray.isSupported()) {
			try {
				log.info("创建托盘图标");
				final SystemTray systemTray = SystemTray.getSystemTray();
				final BufferedImage trayImage = ImageIO.read(Objects.requireNonNull(CkfJavafxApplication.class.getResourceAsStream("/image/tray.png")));
				final PopupMenu popupMenu = new PopupMenu();
				final MenuItem exitMenuItem = new MenuItem("Exit");
				exitMenuItem.addActionListener(e -> stopApplication());
				popupMenu.add(exitMenuItem);
				final TrayIcon trayIcon = new TrayIcon(trayImage, "ckf示例程序（双击显示窗口）", popupMenu);
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
				log.error("[{}]ckf示例程序托盘图标创建失败", instanceId);
			}
		}
	}

	/**
	 * 移除系统托盘图标
	 */
	private void removeSystemTray() {
		if (isTrayIconAvailable()) {
			log.info("移除托盘图标");
			SystemTray systemTray = SystemTray.getSystemTray();
			TrayIcon trayIcon = (TrayIcon) GUIState.getStage().getUserData();
			systemTray.remove(trayIcon);
			GUIState.getStage().setUserData(null);
		}
	}

	/**
	 * 是否支持系统托盘
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
			log.warn("[{}]ckf示例程序已在运行或端口[{}]被占用", instanceId, defaultMonitorPort);
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
		// 如果spring context初始化失败也关闭UI
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
