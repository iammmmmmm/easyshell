package cn.oyzh.jeditermfx.terminal.ui;

import cn.oyzh.common.log.JulLog;
import cn.oyzh.jeditermfx.core.Color;
import cn.oyzh.jeditermfx.terminal.ProcessTtyConnector;
import cn.oyzh.jeditermfx.terminal.SubstringFinder;
import cn.oyzh.jeditermfx.terminal.Terminal;
import cn.oyzh.jeditermfx.terminal.TerminalDisplay;
import cn.oyzh.jeditermfx.terminal.TerminalExecutorServiceManager;
import cn.oyzh.jeditermfx.terminal.TerminalMode;
import cn.oyzh.jeditermfx.terminal.TerminalStarter;
import cn.oyzh.jeditermfx.terminal.TtyBasedArrayDataStream;
import cn.oyzh.jeditermfx.terminal.TtyConnector;
import cn.oyzh.jeditermfx.terminal.model.JediTermDebouncerImpl;
import cn.oyzh.jeditermfx.terminal.model.JediTerminal;
import cn.oyzh.jeditermfx.terminal.model.StyleState;
import cn.oyzh.jeditermfx.terminal.model.TerminalTextBuffer;
import cn.oyzh.jeditermfx.terminal.model.hyperlinks.HyperlinkFilter;
import cn.oyzh.jeditermfx.terminal.model.hyperlinks.TextProcessing;
import cn.oyzh.jeditermfx.core.typeahead.TerminalTypeAheadManager;
import cn.oyzh.jeditermfx.core.typeahead.TypeAheadTerminalModel;
import cn.oyzh.jeditermfx.terminal.model.JediTermTypeAheadModel;
import cn.oyzh.jeditermfx.terminal.ui.settings.SettingsProvider;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * JediTermFX terminal widget with UI implemented in JavaFX.
 */
public class JediTermFxWidget implements TerminalSession, TerminalWidget, TerminalActionProvider {

    protected final TerminalPanel myTerminalPanel;

    protected final JediTerminal myTerminal;

    private final AtomicReference<Session> myRunningSession = new AtomicReference<>();

    private final JediTermTypeAheadModel myTypeAheadTerminalModel;

    private final TerminalTypeAheadManager myTypeAheadManager;

    private JediTermFindComponent myFindComponent;

//TODO
//  @SuppressWarnings("removal")
//  private final PreConnectHandler myPreConnectHandler;

    private TtyConnector myTtyConnector;

    private TerminalStarter myTerminalStarter;

    private final CompletableFuture<TerminalStarter> myTerminalStarterFuture = new CompletableFuture<>();

    protected final SettingsProvider mySettingsProvider;

    private TerminalActionProvider myNextActionProvider;

    private final StackPane myInnerPanel;

    private final TextProcessing myTextProcessing;

    private final List<TerminalWidgetListener> myListeners = new CopyOnWriteArrayList<>();

    private final Object myExecutorServiceManagerLock = new Object();

    private volatile TerminalExecutorServiceManager myExecutorServiceManager;

    private final Set<ScrollBarMark> findResultMarkers = new HashSet<>();

    public JediTermFxWidget(@NotNull SettingsProvider settingsProvider) {
        this(80, 24, settingsProvider);
    }

    public JediTermFxWidget(int columns, int lines, SettingsProvider settingsProvider) {
        mySettingsProvider = settingsProvider;
        StyleState styleState = createDefaultStyle();
        myTextProcessing = new TextProcessing(settingsProvider.getHyperlinkColor(),
                settingsProvider.getHyperlinkHighlightingMode());
        TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(columns, lines, styleState,
                settingsProvider.getBufferMaxLinesCount(), myTextProcessing);
        myTextProcessing.setTerminalTextBuffer(terminalTextBuffer);
        myTerminalPanel = createTerminalPanel(mySettingsProvider, styleState, terminalTextBuffer);
        myTerminal = createTerminal(myTerminalPanel, terminalTextBuffer, styleState);
        myTypeAheadTerminalModel = new JediTermTypeAheadModel(myTerminal, terminalTextBuffer, settingsProvider);
        myTypeAheadManager = new TerminalTypeAheadManager(myTypeAheadTerminalModel);
        JediTermDebouncerImpl typeAheadDebouncer =
                new JediTermDebouncerImpl(myTypeAheadManager::debounce, TerminalTypeAheadManager.MAX_TERMINAL_DELAY,
                        getExecutorServiceManager());
        myTypeAheadManager.setClearPredictionsDebouncer(typeAheadDebouncer);
        myTerminalPanel.setTypeAheadManager(myTypeAheadManager);
        myTerminal.setModeEnabled(TerminalMode.AltSendsEscape, mySettingsProvider.altSendsEscape());
        myTerminalPanel.addTerminalMouseListener(myTerminal);
        myTerminalPanel.setNextProvider(this);
        myTerminalPanel.setCoordAccessor(myTerminal);
//TODO
//    myPreConnectHandler = createPreConnectHandler(myTerminal);
//    myTerminalPanel.addCustomKeyListener(myPreConnectHandler);
        myInnerPanel = new StackPane();
        myInnerPanel.setFocusTraversable(false);
        var canvasPane = myTerminalPanel.getPane();
        VBox.setVgrow(canvasPane, Priority.ALWAYS);
        myInnerPanel.getChildren().addAll(canvasPane);
        myInnerPanel.addEventFilter(KeyEvent.KEY_PRESSED, (e) -> {
            if (this.myFindComponent != null && e.getCode() == KeyCode.ESCAPE) {
                hideFindComponent();
                e.consume();
            }
        });
        myTerminalPanel.init();
    }

    @Override
    public Node getPreferredFocusableNode() {
        return this.myTerminalPanel.getPane();
    }

    @Override
    public Dimension2D getPreferredSize() {
        return new Dimension2D(this.myInnerPanel.getWidth(), this.myInnerPanel.getHeight());
    }

    protected ScrollBar createScrollBar() {
        var scrollBar = new ScrollBar();
        return scrollBar;
    }

    protected StyleState createDefaultStyle() {
        StyleState styleState = new StyleState();
        styleState.setDefaultStyle(mySettingsProvider.getDefaultStyle());
        return styleState;
    }

    protected TerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider,
                @NotNull StyleState styleState, @NotNull TerminalTextBuffer terminalTextBuffer) {
        return new TerminalPanel(settingsProvider, terminalTextBuffer, styleState);
    }

    protected @NotNull JediTerminal createTerminal(@NotNull TerminalDisplay display,
                @NotNull TerminalTextBuffer textBuffer, @NotNull StyleState initialStyleState) {
        return new JediTerminal(display, textBuffer, initialStyleState);
    }

    @SuppressWarnings({"removal", "DeprecatedIsStillUsed"})
    @Deprecated(forRemoval = true)
    private PreConnectHandler createPreConnectHandler(JediTerminal terminal) {
        return new PreConnectHandler(terminal);
    }

    public TerminalDisplay getTerminalDisplay() {
        return getTerminalPanel();
    }

    public TerminalPanel getTerminalPanel() {
        return myTerminalPanel;
    }

    public final @NotNull TerminalExecutorServiceManager getExecutorServiceManager() {
        TerminalExecutorServiceManager manager = myExecutorServiceManager;
        if (manager != null) return manager;
        synchronized (myExecutorServiceManagerLock) {
            manager = myExecutorServiceManager;
            if (manager == null) {
                manager = createExecutorServiceManager();
                myExecutorServiceManager = manager;
            }
            return manager;
        }
    }

    protected @NotNull TerminalExecutorServiceManager createExecutorServiceManager() {
        return new DefaultTerminalExecutorServiceManager();
    }

    @SuppressWarnings("unused")
    public TerminalTypeAheadManager getTypeAheadManager() {
        return myTypeAheadManager;
    }

    public void setTtyConnector(@NotNull TtyConnector ttyConnector) {
        myTtyConnector = ttyConnector;
        TypeAheadTerminalModel.ShellType shellType;
        if (ttyConnector instanceof ProcessTtyConnector) {
            List<String> commandLine = ((ProcessTtyConnector) myTtyConnector).getCommandLine();
            shellType = TypeAheadTerminalModel.commandLineToShellType(commandLine);
        } else {
            shellType = TypeAheadTerminalModel.ShellType.Unknown;
        }
        myTypeAheadTerminalModel.setShellType(shellType);
        myTerminalStarter = createTerminalStarter(myTerminal, myTtyConnector);
        myTerminalStarterFuture.complete(myTerminalStarter);
        myTerminalPanel.setTerminalStarter(myTerminalStarter);
    }

    protected TerminalStarter createTerminalStarter(@NotNull JediTerminal terminal, @NotNull TtyConnector connector) {
        return new TerminalStarter(terminal, connector,
                new TtyBasedArrayDataStream(connector, myTypeAheadManager::onTerminalStateChanged), myTypeAheadManager,
                getExecutorServiceManager());
    }

    @Override
    public TtyConnector getTtyConnector() {
        return myTtyConnector;
    }

    @Override
    public Terminal getTerminal() {
        return myTerminal;
    }

    public void start() {
        synchronized (myRunningSession) {
            if (myRunningSession.get() == null) {
                EmulatorTask task = new EmulatorTask(() -> {
                    synchronized (myRunningSession) {
                        myRunningSession.set(null);
                    }
                });
                Future<?> future = getExecutorServiceManager().getUnboundedExecutorService().submit(task);
                myRunningSession.set(new Session(task, future));
            } else {
                JulLog.error("Should not try to start session again at this point... ");
            }
        }
    }

    /**
     * @deprecated use {@link #close()} instead
     */
    @Deprecated
    public void stop() {
        stopRunningSession();
    }

    private void stopRunningSession() {
        Session session = myRunningSession.get();
        if (session != null) {
            session.stop();
        }
    }

    public boolean isSessionRunning() {
        return myRunningSession.get() != null;
    }

    @Override
    public TerminalTextBuffer getTerminalTextBuffer() {
        return myTerminalPanel.getTerminalTextBuffer();
    }

    public boolean canOpenSession() {
        return !isSessionRunning();
    }

    @Override
    public JediTermFxWidget createTerminalSession(TtyConnector ttyConnector) {
        setTtyConnector(ttyConnector);
        return this;
    }

    @Override
    public Pane getPane() {
        return this.myInnerPanel;
    }

    @Override
    public void close() {
        stopRunningSession();
        if (myTerminalStarter != null) {
            myTerminalStarter.close();
        }
        myTerminalPanel.dispose();
        getExecutorServiceManager().shutdownWhenAllExecuted();
    }

    @Override
    public List<TerminalAction> getActions() {
        return List.of(new TerminalAction(mySettingsProvider.getFindActionPresentation(),
                keyEvent -> {
                    showFindComponent();
                    return true;
                }).withMnemonicKey(KeyCode.F));
    }

    protected void showFindComponent() {
        if (myFindComponent == null) {
            myFindComponent = createFindComponent();
            final Pane pane = myFindComponent.getPane();
            StackPane.setAlignment(pane, Pos.TOP_RIGHT);
            ScrollBar scrollBar = (ScrollBar) myTerminalPanel.getPane().getChildren().get(1);
            StackPane.setMargin(pane, new Insets(0, scrollBar.getWidth(), 0, 0));
            myInnerPanel.getChildren().add(pane);
            pane.requestFocus();
            Runnable settingsChanged = () -> {
                removeFindResultMarkers();
                SubstringFinder.FindResult results = TerminalSearchUtil.searchInTerminalTextBuffer(getTerminalTextBuffer(),
                        myFindComponent.getTextField().getText(), myFindComponent.getIgnoreCaseCheckBox().isSelected());
                myTerminalPanel.setFindResult(results);
                myFindComponent.onResultUpdated(results);
                addFindResultMarkers();
            };
            myFindComponent.getTextField()
                    .textProperty().addListener((ov, oldV, newV) -> settingsChanged.run());
            myFindComponent.getIgnoreCaseCheckBox()
                    .selectedProperty().addListener((ov, oldV, newV) -> settingsChanged.run());
            myFindComponent.getTextField().setOnKeyPressed((keyEvent) -> {
                if (keyEvent.getCode() == KeyCode.ESCAPE) {
                    hideFindComponent();
                } else if (keyEvent.getCode() == KeyCode.ENTER || keyEvent.getCode() == KeyCode.DOWN) {
                    myFindComponent.onResultUpdated(myTerminalPanel.selectNextFindResultItem());
                } else if (keyEvent.getCode() == KeyCode.UP) {
                    myFindComponent.onResultUpdated(myTerminalPanel.selectPrevFindResultItem());
                }
            });
        } else {
            myFindComponent.getPane().requestFocus();
        }
    }

    protected void hideFindComponent() {
        myInnerPanel.getChildren().remove(myFindComponent.getPane());
        removeFindResultMarkers();
        myFindComponent = null;
        myTerminalPanel.setFindResult(null);
        myTerminalPanel.getCanvas().requestFocus();
    }

    protected @NotNull JediTermFindComponent createFindComponent() {
        return new JediTermFindComponent(this);
    }

    protected void addFindResultMarkers() {
        SubstringFinder.FindResult result = myTerminalPanel.getFindResult();
        if (result != null) {
            var scrollBar = myTerminalPanel.getScrollBar();
            StackPane track = (StackPane) scrollBar.lookup(".track");
            int historyLineCount = myTerminalPanel.getTerminalTextBuffer().getHistoryLinesCount();
            int screenLineCount = myTerminalPanel.getTerminalTextBuffer().getScreenLinesCount();
            Color color = mySettingsProvider.getTerminalColorPalette()
                    .getBackground(Objects.requireNonNull(mySettingsProvider.getFoundPatternColor().getBackground()));
            var fxColor = FxTransformers.toFxColor(color);
            for (SubstringFinder.FindResult.FindItem r : result.getItems()) {
                var marker = new ScrollBarMark(fxColor);
                var position = FxScrollBarUtils.getValueFor(r.getStart().y, screenLineCount + historyLineCount,
                        scrollBar.getMin(), scrollBar.getMax());
                marker.setPosition(position);
                if (this.findResultMarkers.add(marker)) {
                    marker.attach(scrollBar, track);
                }
            }
        }
    }

    protected void removeFindResultMarkers() {
        for (var m : this.findResultMarkers) {
            m.detach();
        }
        this.findResultMarkers.clear();
    }

    @Override
    public TerminalActionProvider getNextProvider() {
        return myNextActionProvider;
    }

    public void setNextProvider(TerminalActionProvider actionProvider) {
        this.myNextActionProvider = actionProvider;
    }

    private static class Session {

        private final EmulatorTask myEmulatorTask;

        private final Future<?> mySessionFuture;

        public Session(@NotNull EmulatorTask emulatorTask, @NotNull Future<?> sessionFuture) {
            myEmulatorTask = emulatorTask;
            mySessionFuture = sessionFuture;
        }

        void stop() {
            myEmulatorTask.requestStop();
            mySessionFuture.cancel(true);
        }
    }

    private class EmulatorTask implements Runnable {

        private final TerminalStarter myStarter;

        private final Runnable myOnDone;

        public EmulatorTask(@NotNull Runnable onDone) {
            myStarter = Objects.requireNonNull(myTerminalStarter);
            myOnDone = onDone;
        }

        @SuppressWarnings("removal")
        public void run() {
            TtyConnector ttyConnector = myStarter.getTtyConnector();
            try {
                //if (ttyConnector.init(myPreConnectHandler)) {
                //TODO
                if (ttyConnector.init(null)) {
                    //myTerminalPanel.removeCustomKeyListener(myPreConnectHandler);
                    myStarter.start();
                }
            } catch (Exception e) {
                JulLog.error("Exception running terminal", e);
            } finally {
                try {
                    ttyConnector.close();
                } catch (Exception ignored) {
                }
                try {
                    for (TerminalWidgetListener listener : myListeners) {
                        listener.allSessionsClosed(JediTermFxWidget.this);
                    }
                } catch (Exception e) {
                    JulLog.error("Unhandled exception when closing terminal", e);
                }
                try {
                    myOnDone.run();
                } catch (Exception e) {
                    JulLog.error("Unhandled exception when closing terminal", e);
                }
            }
        }

        void requestStop() {
            myStarter.requestEmulatorStop();
        }
    }

    /**
     * @deprecated use {@link #getTtyConnector()} to figure out if session started
     */
    @Deprecated
    public @Nullable TerminalStarter getTerminalStarter() {
        return myTerminalStarter;
    }

    protected void doWithTerminalStarter(@NotNull Consumer<TerminalStarter> consumer) {
        myTerminalStarterFuture.thenAccept(consumer);
    }

    public void addHyperlinkFilter(HyperlinkFilter filter) {
        myTextProcessing.addHyperlinkFilter(filter);
    }

    @Override
    public void addListener(TerminalWidgetListener listener) {
        myListeners.add(listener);
    }

    @Override
    public void removeListener(TerminalWidgetListener listener) {
        myListeners.remove(listener);
    }
}
