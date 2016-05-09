/*
 *  Copyright (C) 2016 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package megan.chart.gui;

import jloda.gui.*;
import jloda.gui.MenuBar;
import jloda.gui.commands.CommandManager;
import jloda.gui.director.*;
import jloda.gui.find.FindToolBar;
import jloda.gui.find.JListSearcher;
import jloda.gui.find.SearchManager;
import jloda.util.CanceledException;
import jloda.util.Cursors;
import jloda.util.Pair;
import jloda.util.ProgramProperties;
import megan.chart.ChartColorManager;
import megan.chart.IChartDrawer;
import megan.chart.IMultiChartDrawable;
import megan.chart.data.IChartData;
import megan.chart.data.IData;
import megan.chart.data.IPlot2DData;
import megan.chart.drawers.*;
import megan.core.Director;
import megan.fx.NotificationsInSwing;
import megan.main.MeganProperties;
import megan.viewer.ClassificationViewer;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * chart viewer
 * Daniel Huson, 5.2012
 */
public class ChartViewer extends JFrame implements IDirectableViewer, IViewerWithFindToolBar, IViewerWithLegend, Printable {
    protected final ClassificationViewer parentViewer;
    protected final Director dir;
    private final IData chartData;
    private final CommandManager commandManager;
    private final ILabelGetter seriesLabelGetter;
    private boolean isUptoDate = true;
    private boolean locked = false;
    private boolean showFindToolBar = false;
    private final SearchManager searchManager;
    public final ToolBar toolbar;
    public final ToolBar toolBar4List;
    private JToolBar bottomToolBar;
    private final MenuBar jMenuBar;

    private final JTabbedPane listsTabbedPane;
    private final LabelsJList seriesList;
    private final LabelsJList classesList;

    private final JListSearcher seriesSearcher;
    private final JListSearcher classesSearcher;

    private final StatusBar statusbar;

    private final JPanel mainPanel;
    private final JScrollPane scrollPane;
    private final JPanel contentPanel;
    private final LegendPanel legendPanel;
    private final JScrollPane legendScrollPane;
    private final JSplitPane splitPane;

    public static Font defaultFont;

    int downX, downY;

    private boolean firstUpdate = true;

    private final Label2LabelMapper class2HigherClassMapper;
    protected String windowTitle = "Chart";
    private String chartTitle = "Chart";
    private double classLabelAngle = 0;
    private boolean transpose = false;
    protected ChartViewer.ScalingType scalingType = ChartViewer.ScalingType.LINEAR;
    private boolean showXAxis = true;
    private boolean showYAxis = true;

    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private boolean useRectangleShape = false;

    public enum ScalingType {LINEAR, LOG, SQRT, PERCENT, ZSCORE}

    public enum FontKeys {DrawFont, TitleFont, XAxisFont, YAxisFont, LegendFont, ValuesFont}

    private final Map<String, Pair<Font, Color>> fonts = new HashMap<>();

    private String showLegend = "none";
    private boolean showValues = false;
    private boolean showInternalLabels = true;

    private boolean showVerticalGridLines = ProgramProperties.get("ChartShowVerticalGridLines", true);
    private boolean showGapsBetweenBars = ProgramProperties.get("ChartShowGapsBetweenBars", true);

    private IChartDrawer chartDrawer;
    private final Map<String, IChartDrawer> name2DrawerInstance = new HashMap<>();

    private IPopupMenuModifier popupMenuModifier;

    /**
     * constructor
     * @param parentViewer
     * @param dir
     * @param seriesLabelGetter
     * @param chartData
     * @param useGUI
     */
    public ChartViewer(ClassificationViewer parentViewer, final Director dir, ILabelGetter seriesLabelGetter, IData chartData, boolean useGUI) {
        this.parentViewer = parentViewer;
        this.dir = dir;
        this.seriesLabelGetter = seriesLabelGetter;
        this.chartData = chartData;

        int[] geometry = ProgramProperties.get(MeganProperties.CHART_WINDOW_GEOMETRY, new int[]{100, 100, 800, 600});
        getFrame().setSize(geometry[2], geometry[3]);
        if (parentViewer != null)
            setLocationRelativeTo(parentViewer.getFrame());
        else
            getFrame().setLocation(geometry[0] + (dir.getID() - 1) * 20, geometry[1] + (dir.getID() - 1) * 20);

        name2DrawerInstance.putAll(DrawerManager.createChartDrawers());

        this.commandManager = new CommandManager(dir, this, new String[]{"megan.commands", "megan.chart.commands"}, !useGUI);
        // commandManager.addCommands(this, ChartCommandHelper.getChartDrawerCommands(), true);

        class2HigherClassMapper = new Label2LabelMapper();

        defaultFont = ProgramProperties.get("DefaultFont", Font.decode("Helvetica-PLAIN-12"));

        for (FontKeys target : FontKeys.values()) {
            fonts.put(target.toString(),
                    new Pair<>(ProgramProperties.get(target.toString(), defaultFont),
                            ProgramProperties.get(target.toString() + "Color", (Color) null)));
        }

        if (ProgramProperties.getProgramIcon() != null)
            setIconImage(ProgramProperties.getProgramIcon().getImage());
        MenuConfiguration menuConfig = GUIConfiguration.getMenuConfiguration();

        if (parentViewer == null)
            jMenuBar = null;
        else {
            this.jMenuBar = new MenuBar(menuConfig, getCommandManager());
            setJMenuBar(jMenuBar);
            ProjectManager.addAnotherWindowWithWindowMenu(dir, jMenuBar.getWindowMenu());
        }

        toolBar4List = new ToolBar(GUIConfiguration.getToolBar4DomainListConfiguration(), commandManager);
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BorderLayout());
        listPanel.add(toolBar4List, BorderLayout.NORTH);

        JSplitPane horizontalSP = new JSplitPane();
        horizontalSP.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        horizontalSP.setDividerLocation(150);
        horizontalSP.setOneTouchExpandable(true);

        listsTabbedPane = new JTabbedPane();
        listsTabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                getChartSelection().setSelectedBasedOnSeries(getActiveLabelsJList() == seriesList);
            }
        });

        SyncListener syncListenerClassesList = new SyncListener() {
            public void syncList2Viewer(LinkedList<String> enabledNames) {
                if (getChartData() instanceof IChartData) {
                    ((IChartData) getChartData()).setEnabledClassNames(enabledNames);
                }
                if (getChartColorManager().isColorByPosition()) {
                    getChartColorManager().setClassColorPositions(classesList.getEnabledLabels());
                }
            }
        };

        classesList = new LabelsJList(this, getDir().getDocument(), LabelsJList.WHAT.CLASS, syncListenerClassesList, new jloda.gui.PopupMenu(GUIConfiguration.getClassesListPopupConfiguration(), commandManager, false));

        classesList.setDragEnabled(true);
        classesList.setTransferHandler(new ListTransferHandler());
        if (!(chartData instanceof IPlot2DData)) {
            listsTabbedPane.addTab("Classes", new JScrollPane(classesList));
        }
        classesList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                if (!classesList.inSelection) {
                    classesList.inSelection = true;
                    try {
                        getChartSelection().clearSelectionClasses();
                        getChartSelection().setSelectedClass(classesList.getSelectedLabels(), true);
                    } finally {
                        classesList.inSelection = false;
                    }
                }
            }
        });

        getChartSelection().addClassesSelectionListener(new IChartSelectionListener() {
            public void selectionChanged(ChartSelection chartSelection) {
                if (!classesList.inSelection) {
                    classesList.inSelection = true;
                    try {
                        DefaultListModel model = (DefaultListModel) classesList.getModel();
                        for (int i = 0; i < model.getSize(); i++) {
                            String name = classesList.getModel().getElementAt(i);
                            if (chartSelection.isSelectedClass(name))
                                classesList.addSelectionInterval(i, i + 1);
                            else
                                classesList.removeSelectionInterval(i, i + 1);
                        }
                    } finally {
                        classesList.inSelection = false;
                    }
                }
            }
        });

        SyncListener syncListenerSeriesList = new SyncListener() {
            public void syncList2Viewer(LinkedList<String> enabledNames) {
                getChartData().setEnabledSeries(enabledNames);
            }
        };

        seriesList = new LabelsJList(this, getDir().getDocument(), LabelsJList.WHAT.SERIES, syncListenerSeriesList, new jloda.gui.PopupMenu(GUIConfiguration.getSeriesListPopupConfiguration(), commandManager, false));

        seriesList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                if (!seriesList.inSelection) {
                    seriesList.inSelection = true;
                    try {
                        getChartSelection().clearSelectionSeries();
                        getChartSelection().setSelectedSeries(seriesList.getSelectedLabels(), true);
                    } finally {
                        seriesList.inSelection = false;
                    }
                }
            }
        });
        seriesList.setDragEnabled(true);
        seriesList.setTransferHandler(new ListTransferHandler());
        getChartSelection().addSeriesSelectionListener(new IChartSelectionListener() {
            public void selectionChanged(ChartSelection chartSelection) {
                if (!seriesList.inSelection) {
                    seriesList.inSelection = true;
                    try {
                        DefaultListModel model = (DefaultListModel) seriesList.getModel();
                        for (int i = 0; i < model.getSize(); i++) {
                            String name = seriesList.getModel().getElementAt(i);
                            if (chartSelection.isSelectedSeries(name))
                                seriesList.addSelectionInterval(i, i + 1);
                            else
                                seriesList.removeSelectionInterval(i, i + 1);
                        }
                    } finally {
                        seriesList.inSelection = false;
                    }
                }
            }
        });

        listsTabbedPane.addTab("Series", new JScrollPane(seriesList));

        classesSearcher = new JListSearcher(classesList);
        seriesSearcher = new JListSearcher(seriesList);
        searchManager = new SearchManager(dir, this, seriesSearcher, false, true);

        listsTabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                if (!isSeriesTabSelected()) {
                    searchManager.setSearcher(classesSearcher);
                    searchManager.getFindDialogAsToolBar().clearMessage();
                    if (!seriesList.inSelection) {
                        seriesList.inSelection = true;
                        try {
                            getChartSelection().clearSelectionSeries();
                            ChartViewer.this.repaint();
                        } finally {
                            seriesList.inSelection = false;
                        }
                        // sync classes selection
                    }
                    if (!classesList.inSelection) {
                        classesList.inSelection = true;
                        try {
                            getChartSelection().clearSelectionClasses();
                            getChartSelection().setSelectedClass(classesList.getSelectedLabels(), true);
                            ChartViewer.this.repaint();
                        } finally {
                            classesList.inSelection = false;
                        }
                    }
                } else {
                    searchManager.setSearcher(seriesSearcher);
                    searchManager.getFindDialogAsToolBar().clearMessage();
                    if (!seriesList.inSelection) {
                        seriesList.inSelection = true;
                        try {
                            getChartSelection().clearSelectionSeries();
                            getChartSelection().setSelectedSeries(seriesList.getSelectedLabels(), true);
                            ChartViewer.this.repaint();
                        } finally {
                            seriesList.inSelection = false;
                        }
                    }
                    if (!classesList.inSelection) {
                        try {
                            classesList.inSelection = true;
                            getChartSelection().clearSelectionClasses();
                            ChartViewer.this.repaint();
                        } finally {
                            classesList.inSelection = false;
                        }
                    }
                }
            }
        });

        listPanel.add(listsTabbedPane, BorderLayout.CENTER);

        horizontalSP.setLeftComponent(listPanel);

        getContentPane().setLayout(new BorderLayout());

        toolbar = new ToolBar(GUIConfiguration.getToolBarConfiguration(), commandManager);

        getContentPane().add(toolbar, BorderLayout.NORTH);

        contentPanel = new JPanel();

        contentPanel.setLayout(new BorderLayout());

        contentPanel.addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
                    boolean xyLocked = getChartDrawer().isXYLocked();

                    boolean doScaleVertical = !e.isMetaDown() && !e.isAltDown() && !e.isShiftDown() && !xyLocked;
                    boolean doScaleHorizontal = !e.isMetaDown() && !e.isControlDown() && !e.isAltDown() && e.isShiftDown();
                    boolean doScrollVertical = !e.isMetaDown() && e.isAltDown() && !e.isShiftDown() && !xyLocked;
                    boolean doScrollHorizontal = !e.isMetaDown() && e.isAltDown() && e.isShiftDown() && !xyLocked;
                    boolean doScaleBoth = (e.isMetaDown() || xyLocked) && !e.isAltDown() && !e.isShiftDown();
                    boolean doRotate = !e.isShiftDown() && !e.isMetaDown() && !e.isControlDown() && e.isAltDown() && getChartDrawer() instanceof RadialSpaceFillingTreeDrawer;

                    if (doScrollVertical) {
                        getScrollPane().getVerticalScrollBar().setValue(getScrollPane().getVerticalScrollBar().getValue() + e.getUnitsToScroll());
                    } else if (doScaleVertical) {
                        double toScroll = 1.0 + (e.getUnitsToScroll() / 100.0);
                        double scale = (toScroll > 0 ? 1.0 / toScroll : toScroll);
                        if (scale >= 0 && scale <= 1000) {
                            zoom(1, (float) scale, e.getPoint());
                        }
                    } else if (doScaleBoth) {
                        double toScroll = 1.0 + (e.getUnitsToScroll() / 100.0);
                        double scale = (toScroll > 0 ? 1.0 / toScroll : toScroll);
                        if (scale >= 0 && scale <= 1000) {
                            zoom((float) scale, (float) scale, e.getPoint());
                        }
                    } else if (doScrollHorizontal) {
                        getScrollPane().getHorizontalScrollBar().setValue(getScrollPane().getHorizontalScrollBar().getValue() + e.getUnitsToScroll());
                    } else if (doScaleHorizontal && !xyLocked) { //scale
                        double toScroll = 1.0 + (e.getUnitsToScroll() / 100.0);
                        double scale = (toScroll > 0 ? 1.0 / toScroll : toScroll);
                        if (scale >= 0 && scale <= 1000) {
                            zoom((float) scale, 1, e.getPoint());
                        }
                    } else if (doRotate) {
                        RadialSpaceFillingTreeDrawer drawer = (RadialSpaceFillingTreeDrawer) getChartDrawer();
                        drawer.setAngleOffset(drawer.getAngleOffset() + e.getUnitsToScroll());
                        drawer.repaint();
                    }
                }
            }
        });
        contentPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                downX = me.getXOnScreen();
                downY = me.getYOnScreen();
                scrollPane.setCursor(Cursors.getOpenHand());
                scrollPane.grabFocus();
            }

            public void mouseReleased(MouseEvent mouseEvent) {
                scrollPane.setCursor(Cursor.getDefaultCursor());
            }

            public void mouseClicked(MouseEvent me) {
                boolean changed = false;
                if (!me.isControlDown() && !me.isShiftDown()) { // deselect all
                    if (getChartSelection().getSelectedSeries().size() > 0 || getChartSelection().getSelectedClasses().size() > 0)
                        changed = true;
                    getChartSelection().clearSelectionSeries();
                    getChartSelection().clearSelectionClasses();
                    getChartDrawer().repaint();
                }
                if (me.getClickCount() == 2 && chartDrawer instanceof CoOccurrenceDrawer) {
                    changed = ((CoOccurrenceDrawer) chartDrawer).selectComponent(me);
                } else if (chartDrawer.selectOnMouseDown(me, getChartSelection()))
                    changed = true;
                if (changed) {
                    updateView(IDirector.ENABLE_STATE);
                    if (seriesList.getSelectedIndices().length > 0) {
                        seriesList.ensureIndexIsVisible(seriesList.getSelectedIndex());
                    }
                    if (classesList.getSelectedIndices().length > 0) {
                        classesList.ensureIndexIsVisible(classesList.getSelectedIndex());
                    }
                }
            }
        });
        contentPanel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent me) {

                if (me.isPopupTrigger())
                    return;
                JScrollPane scrollPane = getScrollPane();
                scrollPane.setCursor(Cursors.getClosedHand());

                int dX = me.getXOnScreen() - downX;
                int dY = me.getYOnScreen() - downY;
                if (dY != 0) {
                    JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
                    scrollBar.setValue(scrollBar.getValue() - dY);
                }
                if (dX != 0) {
                    JScrollBar scrollBar = scrollPane.getHorizontalScrollBar();
                    scrollBar.setValue(scrollBar.getValue() - dX);
                }
                downX = me.getXOnScreen();
                downY = me.getYOnScreen();
            }

            public void mouseMoved(MouseEvent mouseEvent) {
                Pair<String, String> pair = getChartDrawer().getItemBelowMouse(mouseEvent, getChartSelection());
                String label = null;
                if (pair != null) {
                    if (pair.get1() != null && pair.get2() != null)
                        label = pair.get1() + ", " + pair.get2();
                    else if (pair.get1() != null)
                        label = pair.get1();
                    else
                        label = pair.get2();
                }
                ChartViewer.this.getContentPanel().setToolTipText(label);
                mainPanel.setToolTipText(label);
            }
        });

        scrollPane = new JScrollPane(contentPanel);

        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scrollPane.setWheelScrollingEnabled(false);
        scrollPane.setAutoscrolls(true);

        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        legendPanel = new LegendPanel(this);
        legendPanel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent me) {
                boolean changed = false;
                if (!me.isShiftDown()) {
                    getChartSelection().clearSelectionSeries();
                    getChartSelection().clearSelectionClasses();
                    changed = true;
                }
                if (selectByMouseInLegendPanel(legendPanel, me.getPoint(), getChartSelection())) {
                    changed = true;
                }
                if (changed)
                    updateView(IDirector.ENABLE_STATE);
            }
        });
        legendScrollPane = new JScrollPane(legendPanel);
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainPanel, legendScrollPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setEnabled(true);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerLocation(1.0);
        splitPane.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent componentEvent) {
                legendPanel.updateView();
                if (getShowLegend().equals("none"))
                    splitPane.setDividerLocation(1.0);
            }
        });

        horizontalSP.setRightComponent(splitPane);

        getContentPane().add(horizontalSP);

        if (chartData instanceof IChartData)
            chooseDrawer(BarChartDrawer.NAME);
        else
            chooseDrawer(Plot2DDrawer.NAME);

        statusbar = new StatusBar();
        getContentPane().add(statusbar, BorderLayout.SOUTH);

        contentPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    final jloda.gui.PopupMenu menu = new jloda.gui.PopupMenu(GUIConfiguration.getMainPanelPopupConfiguration(), commandManager, false);
                    if (popupMenuModifier != null)
                        popupMenuModifier.apply(menu, commandManager);
                    menu.show(contentPanel, me.getX(), me.getY());
                }
            }

            public void mouseReleased(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    final jloda.gui.PopupMenu menu = new jloda.gui.PopupMenu(GUIConfiguration.getMainPanelPopupConfiguration(), commandManager, false);
                    if (popupMenuModifier != null)
                        popupMenuModifier.apply(menu, commandManager);
                    menu.show(contentPanel, me.getX(), me.getY());
                }
            }
        });

        legendPanel.setPopupMenu(new jloda.gui.PopupMenu(GUIConfiguration.getLegendPanelPopupConfiguration(), commandManager, false));

        addWindowListener(new WindowListenerAdapter() {
            public void windowDeactivated(WindowEvent event) {
                ProjectManager.getPreviouslySelectedNodeLabels().clear();
                if (isSeriesTabSelected())
                    ProjectManager.getPreviouslySelectedNodeLabels().addAll(getChartSelection().getSelectedSeries());
                else
                    ProjectManager.getPreviouslySelectedNodeLabels().addAll(getChartSelection().getSelectedClasses());
            }
        });

        pack();
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        splitPane.setDividerLocation(1.0);
    }

    public ChartColorManager getChartColorManager() {
        return dir.getDocument().getChartColorManager();
    }

    /**
     * performs selection in legend panel
     *
     * @param legendPanel
     * @param point
     * @param chartSelection
     * @return true if something changed
     */
    private boolean selectByMouseInLegendPanel(LegendPanel legendPanel, Point point, ChartSelection chartSelection) {
        SelectionGraphics<String[]> selectionGraphics = new SelectionGraphics<>(getGraphics());
        selectionGraphics.setMouseLocation(point);
        legendPanel.paint(selectionGraphics);
        Set<String> seriesToSelect = new HashSet<>();
        Set<String> classesToSelect = new HashSet<>();

        for (String[] pair : selectionGraphics.getSelectedItems()) {
            if (pair[0] != null) {
                seriesToSelect.add(pair[0]);
            }
            if (pair[1] != null) {
                classesToSelect.add(pair[1]);
            }
        }
        if (seriesToSelect.size() > 0)
            chartSelection.setSelectedSeries(seriesToSelect, true);
        if (classesToSelect.size() > 0)
            chartSelection.setSelectedClass(classesToSelect, true);
        return seriesToSelect.size() > 0 || classesToSelect.size() > 0;
    }

    public void zoomToFit() {
        Dimension size = new Dimension(100, 100);
        contentPanel.setSize(size);
        contentPanel.setPreferredSize(size);
        getContentPane().validate();
    }

    /**
     * zoom in or out (making panel larger or smaller)
     *
     * @param factorX
     * @param factorY
     * @param center
     */
    public void zoom(float factorX, float factorY, Point center) {
        if (getChartDrawer().isXYLocked()) {
            if (factorX == 1)
                factorX = factorY; // yes. ok
            else if (factorY == 1)
                factorY = factorX;
        }

        if (getChartDrawer().getScrollBackReferenceRect() != null) {
            if (center == null)
                center = new Point((int) contentPanel.getBounds().getCenterX(), (int) contentPanel.getBounds().getCenterY());
            getChartDrawer().setScrollBackWindowPoint(center);
            getChartDrawer().setScrollBackReferencePoint(getChartDrawer().convertWindowToReference(center));
        }

        Dimension size = contentPanel.getSize();
        int newWidth = Math.max(100, (Math.round(factorX * size.width)));
        int newHeight = Math.max(100, (Math.round(factorY * size.height)));

        size = new Dimension(newWidth, newHeight);
        contentPanel.setSize(size);
        contentPanel.setPreferredSize(size);
        contentPanel.validate();
        updateScrollPane();
    }


    /**
     * update scroll pane after zoom to keep centered on mouse position
     *
     * @return true, if ok to paint
     */
    private void updateScrollPane() {
        if (chartDrawer.getScrollBackReferenceRect() != null) {
            chartDrawer.computeScrollBackReferenceRect();

            if (chartDrawer.getScrollBackReferencePoint() != null && chartDrawer.getScrollBackWindowPoint() != null) {
                Point2D apt = chartDrawer.convertReferenceToWindow(chartDrawer.getScrollBackReferencePoint());


                int scrollX = (int) Math.round(apt.getX() - chartDrawer.getScrollBackWindowPoint().getX());
                int scrollY = (int) Math.round(apt.getY() - chartDrawer.getScrollBackWindowPoint().getY());

                chartDrawer.setScrollBackReferencePoint(null);
                chartDrawer.setScrollBackWindowPoint(null);

                if (scrollX != 0) {
                    scrollPane.getHorizontalScrollBar().setValue(scrollPane.getHorizontalScrollBar().getValue() + scrollX);
                }
                if (scrollY != 0) {
                    scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getValue() + scrollY);
                }
            }
        }
    }


    /**
     * is viewer uptodate?
     *
     * @return uptodate
     */
    public boolean isUptoDate() {
        return isUptoDate;
    }

    /**
     * return the frame associated with the viewer
     *
     * @return frame
     */
    public JFrame getFrame() {
        return this;
    }

    /**
     * gets the associated command manager
     *
     * @return command manager
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

    /**
     * ask view to update itself. This is method is wrapped into a runnable object
     * and put in the swing event queue to avoid concurrent modifications.
     *
     * @param what what should be updated? Possible values: Director.ALL or Director.TITLE
     */
    public void updateView(String what) {
        if (!(what.equals(IDirector.ENABLE_STATE) || what.equals(IDirector.TITLE))) {

            if (getChartColorManager().isColorByPosition()) {
                getChartColorManager().setClassColorPositions(classesList.getEnabledLabels());
            }
            chartDrawer.updateView();
        }

        if (chartDrawer instanceof CoOccurrenceDrawer) {
            Set<String> visibleLabels = ((CoOccurrenceDrawer) chartDrawer).getAllVisibleLabels();

            if (transpose) {
                Set<String> toDisable = new HashSet<>();
                toDisable.addAll(seriesList.getAllLabels());
                toDisable.removeAll(visibleLabels);
                seriesList.disableLabels(toDisable);
            } else {
                Set<String> toDisable = new HashSet<>();
                toDisable.addAll(classesList.getAllLabels());
                toDisable.removeAll(visibleLabels);
                classesList.disableLabels(toDisable);
            }
        }
        if (chartDrawer instanceof BarChartDrawer) {
            ((BarChartDrawer) chartDrawer).setGapBetweenBars(showGapsBetweenBars);
            ((BarChartDrawer) chartDrawer).setShowVerticalGridLines(showVerticalGridLines);
        }

        if (chartData.getNumberOfSeries() <= 1) {
            if (firstUpdate) {
                firstUpdate = false;
            }
        }

        final FindToolBar findToolBar = searchManager.getFindDialogAsToolBar();
        if (isSeriesTabSelected() && searchManager.getSearcher() != seriesSearcher)
            searchManager.setSearcher(seriesSearcher);
        else if (!isSeriesTabSelected() && searchManager.getSearcher() != classesSearcher)
            searchManager.setSearcher(classesSearcher);

        if (findToolBar.isClosing()) {
            showFindToolBar = false;
            findToolBar.setClosing(false);
        }
        if (!findToolBar.isEnabled() && showFindToolBar) {
            mainPanel.add(findToolBar, BorderLayout.NORTH);
            findToolBar.setEnabled(true);
            getContentPane().validate();
        } else if (findToolBar.isEnabled() && !showFindToolBar) {
            mainPanel.remove(findToolBar);
            findToolBar.setEnabled(false);
            getContentPane().validate();
        }

        getCommandManager().updateEnableState();

        if (findToolBar.isEnabled())
            findToolBar.clearMessage();

        if (chartData instanceof IChartData)
            getStatusbar().setText2("Series=" + chartData.getNumberOfSeries() + " Classes=" + ((IChartData) chartData).getNumberOfClasses());
        else
            getStatusbar().setText2("Series=" + chartData.getNumberOfSeries());

        if (getChartData().getNumberOfSeries() == 0 || getShowLegend().equals("none"))
            splitPane.setDividerLocation(1.0);

        legendPanel.updateView();
        legendPanel.repaint();
        setWindowTitle(windowTitle);
        repaint();
    }

    /**
     * ask view to prevent user input
     */
    public void lockUserInput() {
        locked = true;
        statusbar.setText1("");
        statusbar.setText2("Busy...");
        searchManager.getFindDialogAsToolBar().setEnableCritical(false);
        if (bottomToolBar != null)
            bottomToolBar.setEnabled(false);
        getCommandManager().setEnableCritical(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        getContentPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    /**
     * ask view to allow user input
     */
    public void unlockUserInput() {
        locked = false;
        getCommandManager().setEnableCritical(true);
        searchManager.getFindDialogAsToolBar().setEnableCritical(true);
        if (bottomToolBar != null)
            bottomToolBar.setEnabled(true);
        setCursor(Cursor.getDefaultCursor());
        getContentPane().setCursor(Cursor.getDefaultCursor());
    }

    /**
     * is viewer currently locked?
     *
     * @return true, if locked
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * ask view to destroy itself
     */
    public void destroyView() throws CanceledException {
        ProgramProperties.put(MeganProperties.CHART_WINDOW_GEOMETRY, new int[]{
                getLocation().x, getLocation().y, getSize().width, getSize().height});
        MeganProperties.removePropertiesListListener(getJMenuBar().getRecentFilesListener());
        executorService.shutdownNow();
        boolean ok = false;
        try {
            ok = executorService.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!ok)
            NotificationsInSwing.showInternalError(getFrame(), "Failed to terminate runaway threads... (consider restarting MEGAN)");
        getChartDrawer().close();
        if (searchManager != null && searchManager.getFindDialogAsToolBar() != null)
            searchManager.getFindDialogAsToolBar().close();
        dir.removeViewer(this);
        dispose();
    }


    public void setWindowTitle(String title) {
        windowTitle = title;
        String newTitle = windowTitle + " - " + dir.getDocument().getTitle();
        if (dir.getDocument().isDirty())
            newTitle += "*";
        if (dir.getID() == 1)
            newTitle += " - " + ProgramProperties.getProgramVersion();
        else
            newTitle += " - [" + dir.getID() + "] - " + ProgramProperties.getProgramVersion();

        if (!getTitle().equals(newTitle)) {
            super.setTitle(newTitle);
            ProjectManager.updateWindowMenus();
        }
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public String getChartTitle() {
        return chartTitle;
    }

    public void setChartTitle(String chartTitle) {
        this.chartTitle = chartTitle;
        chartDrawer.setChartTitle(chartTitle);
    }

    /**
     * set uptodate state
     *
     * @param flag
     */
    public void setUptoDate(boolean flag) {
        isUptoDate = flag;
    }

    public boolean isShowFindToolBar() {
        return showFindToolBar;
    }

    public void setShowFindToolBar(boolean show) {
        this.showFindToolBar = show;
    }

    public SearchManager getSearchManager() {
        return searchManager;
    }

    public IChartDrawer getChartDrawer() {
        return chartDrawer;
    }

    public void setChartDrawer(IChartDrawer chartDrawer) {
        if (bottomToolBar != null) {
            mainPanel.remove(bottomToolBar);
            bottomToolBar = null;
        }
        this.chartDrawer = chartDrawer;
        setPopupMenuModifier(chartDrawer.getPopupMenuModifier());
        contentPanel.removeAll();
        contentPanel.add(chartDrawer.getJPanel());
        contentPanel.revalidate();
    }

    public IData getChartData() {
        return chartData;
    }

    public void chooseDrawer(String drawerType) {
        if (!getChartDrawerName().equals(drawerType)) {
            IChartDrawer drawer = name2DrawerInstance.get(drawerType);
            drawer.setViewer(this);
            drawer.setChartData(chartData);
            drawer.setClass2HigherClassMapper(class2HigherClassMapper);
            drawer.setSeriesLabelGetter(seriesLabelGetter);
            drawer.setExecutorService(executorService);

            if (!drawer.canTranspose())
                setTranspose(false);

            if (drawer instanceof IMultiChartDrawable) {
                drawer.setViewer(this);
                drawer.setChartData(chartData);
                drawer.setClass2HigherClassMapper(class2HigherClassMapper);
                drawer.setSeriesLabelGetter(seriesLabelGetter);
                setChartDrawer(new MultiChartDrawer((IMultiChartDrawable) drawer));
            } else
                setChartDrawer(drawer);

            bottomToolBar = getChartDrawer().getBottomToolBar();
            if (bottomToolBar != null)
                mainPanel.add(bottomToolBar, BorderLayout.SOUTH);

            for (String target : fonts.keySet()) {
                Pair<Font, Color> pair = fonts.get(target);
                chartDrawer.setFont(target, pair.get1(), pair.get2());
            }
            chartDrawer.setShowValues(showValues);
            chartDrawer.setShowInternalLabels(showInternalLabels);
            chartDrawer.setTranspose(transpose);
            chartDrawer.setClassLabelAngle(classLabelAngle);
            chartDrawer.setChartTitle(getChartTitle());

            scalingType = chartDrawer.getScalingTypePreference();
            chartDrawer.setScalingType(scalingType);

            showXAxis = chartDrawer.getShowXAxisPreference();
            showYAxis = chartDrawer.getShowYAxisPreference();


            if (!getChartDrawer().canShowLegend())
                setShowLegend("none");
        }
        //repaint();
    }

    public String getChartDrawerName() {
        return chartDrawer != null ? chartDrawer.getChartDrawerName() : "None";
    }

    /**
     * get name for this type of viewer
     *
     * @return name
     */
    public String getClassName() {
        return "ChartViewer";
    }

    public StatusBar getStatusbar() {
        return statusbar;
    }

    public int print(Graphics gc0, PageFormat format, int pagenumber) throws PrinterException {
        if (pagenumber == 0) {
            Graphics2D gc = ((Graphics2D) gc0);
            gc.setFont(getFont());

            Dimension dim = contentPanel.getSize();

            int image_w = dim.width;
            int image_h = dim.height;

            double paper_x = format.getImageableX() + 1;
            double paper_y = format.getImageableY() + 1;
            double paper_w = format.getImageableWidth() - 2;
            double paper_h = format.getImageableHeight() - 2;

            double scale_x = paper_w / image_w;
            double scale_y = paper_h / image_h;
            double scale = (scale_x <= scale_y) ? scale_x : scale_y;

            double shift_x = paper_x + (paper_w - scale * image_w) / 2.0;
            double shift_y = paper_y + (paper_h - scale * image_h) / 2.0;

            gc.translate(shift_x, shift_y);
            gc.scale(scale, scale);

            gc.setStroke(new BasicStroke(1.0f));
            gc.setColor(Color.BLACK);

            contentPanel.paint(gc);

            return Printable.PAGE_EXISTS;
        } else
            return Printable.NO_SUCH_PAGE;
    }

    public ToolBar getToolbar() {
        return toolbar;
    }

    public boolean isShowValues() {
        return showValues;
    }

    public void setShowValues(boolean showValues) {
        if (chartDrawer != null)
            chartDrawer.setShowValues(showValues);
        this.showValues = showValues;
    }

    public boolean isShowInternalLabels() {
        return showInternalLabels;
    }

    public void setShowInternalLabels(boolean showInternalLabels) {
        if (chartDrawer != null)
            chartDrawer.setShowInternalLabels(showInternalLabels);
        this.showInternalLabels = showInternalLabels;
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public boolean isTranspose() {
        return transpose;
    }

    public void setTranspose(boolean transpose) {
        if (chartDrawer != null)
            chartDrawer.setTranspose(transpose);
        this.transpose = transpose;
    }

    public double getClassLabelAngle() {
        return classLabelAngle;
    }

    public void setClassLabelAngle(double classLabelAngle) {
        if (chartDrawer != null)
            chartDrawer.setClassLabelAngle(classLabelAngle);
        this.classLabelAngle = classLabelAngle;
    }

    public ScalingType getScalingType() {
        return scalingType;
    }

    public void setScalingType(ScalingType scalingType) {
        if (chartDrawer != null)
            chartDrawer.setScalingType(scalingType);
        this.scalingType = scalingType;
    }

    public MenuBar getJMenuBar() {
        return jMenuBar;
    }

    /**
     * synchronize data
     */
    public void sync() throws CanceledException {
        getChartDrawer().forceUpdate();

        seriesList.sync(getChartData().getSeriesNames(), getChartData().getSamplesTooltips(), false);
        if (getChartData() instanceof IChartData) {
            classesList.sync(((IChartData) getChartData()).getClassNames(), getChartData().getClassesTooltips(), false);
            classesList.fireSyncToViewer();
        }
    }

    /**
     * gets the active labels list
     *
     * @return active labels list
     */
    public LabelsJList getActiveLabelsJList() {
        if (listsTabbedPane.getSelectedComponent() != null) {
            JViewport viewport = ((JViewport) ((JScrollPane) listsTabbedPane.getSelectedComponent()).getComponent(0));
            if (viewport != null)
                return (LabelsJList) viewport.getComponent(0);
        }
        return null;
    }

    public LabelsJList getSeriesList() {
        return seriesList;
    }

    public LabelsJList getClassesList() {
        return classesList;
    }

    public boolean isShowXAxis() {
        return showXAxis;
    }

    public void setShowXAxis(boolean showXAxis) {
        chartDrawer.setShowXAxis(showXAxis);
        this.showXAxis = showXAxis;
    }

    public boolean isShowYAxis() {
        return showYAxis;
    }

    public void setShowYAxis(boolean showYAxis) {
        chartDrawer.setShowYAxis(showYAxis);
        this.showYAxis = showYAxis;
    }

    public void setUseRectangleShape(boolean useRectangleShape) {
        if (getChartDrawer() instanceof WordCloudDrawer) {
            WordCloudDrawer drawer = (WordCloudDrawer) getChartDrawer();
            drawer.setUseRectangleShape(useRectangleShape);
        } else if (getChartDrawer() instanceof MultiChartDrawer && ((MultiChartDrawer) getChartDrawer()).getBaseDrawer() instanceof WordCloudDrawer) {
            final MultiChartDrawer drawer = (MultiChartDrawer) getChartDrawer();
            ((WordCloudDrawer) drawer.getBaseDrawer()).setUseRectangleShape(useRectangleShape);
        }
        this.useRectangleShape = useRectangleShape;
    }

    public boolean isUseRectangleShape() {
        return useRectangleShape;
    }


    public ChartSelection getChartSelection() {
        return chartData.getChartSelection();
    }

    public boolean isSeriesTabSelected() {
        return listsTabbedPane.getTabCount() == 1 || listsTabbedPane.getSelectedIndex() == 1;
    }

    public Label2LabelMapper getClass2HigherClassMapper() {
        return class2HigherClassMapper;
    }

    public void setFont(String target, Font font, Color color) {
        fonts.put(target, new Pair<>(font, color));
        chartDrawer.setFont(target, font, color);
    }

    public Font getFont(String target) {
        Pair<Font, Color> pair = fonts.get(target);
        if (pair == null || pair.get1() == null)
            return getFont();
        else
            return pair.get1();
    }

    /**
     * gets the series color getter
     *
     * @return series color getter
     */
    public ChartColorManager.ColorGetter getSeriesColorGetter() {
        return new ChartColorManager.ColorGetter() {
            private final ChartColorManager.ColorGetter colorGetter = getChartColorManager().getSeriesColorGetter();

            public Color get(String label) {
                if (transpose && getChartData().getNumberOfSeries() == 1 && getChartDrawerName().equals(BarChartDrawer.NAME))
                    return Color.WHITE;
                if ((!transpose || (chartDrawer instanceof HeatMapDrawer)) && !(chartDrawer instanceof Plot2DDrawer))
                    return Color.WHITE;
                else
                    return colorGetter.get(label);
            }
        };
    }

    /**
     * gets the class color getter
     *
     * @return class color getter
     */
    public ChartColorManager.ColorGetter getClassColorGetter() {
        return new ChartColorManager.ColorGetter() {
            private final ChartColorManager.ColorGetter colorGetter = getChartColorManager().getClassColorGetter();

            public Color get(String label) {
                label = class2HigherClassMapper.get(label);
                if (transpose && getChartData().getNumberOfSeries() == 1 && getChartDrawerName().equals(BarChartDrawer.NAME))
                    return colorGetter.get(label);
                if (transpose || (chartDrawer instanceof HeatMapDrawer))
                    return Color.WHITE;
                else {
                    return colorGetter.get(label);
                }
            }
        };
    }

    public ILabelGetter getSeriesLabelGetter() {
        return seriesLabelGetter;
    }

    public LegendPanel getLegendPanel() {
        return legendPanel;
    }

    public JScrollPane getLegendScrollPane() {
        return legendScrollPane;
    }

    /**
     * show the legend horizontal, vertical or none
     *
     * @param showLegend
     */
    public void setShowLegend(String showLegend) {
        this.showLegend = showLegend;
        if (showLegend.equalsIgnoreCase("horizontal")) {
            splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
            Dimension size = new Dimension();
            splitPane.validate();
            legendPanel.setSize(splitPane.getWidth(), 50);
            legendPanel.draw((Graphics2D) legendPanel.getGraphics(), size);
            int height = (int) size.getHeight() + 10;
            legendPanel.setPreferredSize(new Dimension(splitPane.getWidth(), height));
            legendPanel.validate();
            splitPane.setDividerLocation(splitPane.getSize().height - splitPane.getInsets().right - splitPane.getDividerSize() - height);
        } else if (showLegend.equalsIgnoreCase("vertical")) {
            splitPane.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
            Dimension size = new Dimension();
            splitPane.validate();
            legendPanel.setSize(20, splitPane.getHeight());
            legendPanel.draw((Graphics2D) legendPanel.getGraphics(), size);
            int width = (int) size.getWidth() + 5;
            legendPanel.setPreferredSize(new Dimension(width, splitPane.getHeight()));
            legendPanel.validate();
            splitPane.setDividerLocation(splitPane.getSize().width - splitPane.getInsets().right - splitPane.getDividerSize() - width);
        } else {
            splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
            splitPane.setDividerLocation(1.0);
        }
    }

    public String getShowLegend() {
        return showLegend;
    }

    public Point getZoomCenter() {
        Rectangle rect = getFrame().getBounds();
        return new Point((int) rect.getCenterX() - rect.x + getScrollPane().getViewport().getViewPosition().x, (int) rect.getCenterY() - rect.y + getScrollPane().getViewport().getViewPosition().y);
    }

    public Director getDir() {
        return dir;
    }

    public Collection<String> getChartDrawerNames() {
        return name2DrawerInstance.keySet();
    }

    public boolean isShowGapsBetweenBars() {
        return showGapsBetweenBars;
    }

    public void setShowGapsBetweenBars(boolean showGapsBetweenBars) {
        this.showGapsBetweenBars = showGapsBetweenBars;
        ProgramProperties.put("ChartShowGapsBetweenBars", showGapsBetweenBars);
    }

    public boolean isShowVerticalGridLines() {
        return showVerticalGridLines;
    }

    public void setShowVerticalGridLines(boolean showVerticalGridLines) {
        this.showVerticalGridLines = showVerticalGridLines;
        ProgramProperties.put("ChartShowVerticalGridLines", showVerticalGridLines);
    }

    public IPopupMenuModifier getPopupMenuModifier() {
        return popupMenuModifier;
    }

    public void setPopupMenuModifier(IPopupMenuModifier popupMenuModifier) {
        this.popupMenuModifier = popupMenuModifier;
    }

    public ClassificationViewer getParentViewer() {
        return parentViewer;
    }
}