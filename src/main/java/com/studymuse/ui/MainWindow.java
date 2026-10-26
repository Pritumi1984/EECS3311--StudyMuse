package com.studymuse.ui;

import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.Answer;
import com.studymuse.domain.Course;
import com.studymuse.domain.Material;
import com.studymuse.domain.Summary;
import com.studymuse.domain.SummaryLength;
import com.studymuse.services.DuplicateMaterialException;
import com.studymuse.services.StudyException;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Swing graphical interface. Left: courses and their materials. Right: one tab per feature.
 * Like the CLI it only collects input and shows results; all logic goes through the
 * StudyAgentController facade. Slow AI calls run on a background SwingWorker so the window stays responsive.
 */
public final class MainWindow implements StudyView {

    private final StudyAgentController controller;
    private final JFrame frame = new JFrame("StudyMuse - AI Study Agent");
    private final JPanel root = new JPanel(new BorderLayout());

    private final DefaultListModel<Course> courseModel = new DefaultListModel<>();
    private final JList<Course> courseList = new JList<>(courseModel);
    private final DefaultListModel<Material> materialModel = new DefaultListModel<>();
    private final JList<Material> materialList = new JList<>(materialModel);

    private final JComboBox<SummaryLength> lengthBox = new JComboBox<>(SummaryLength.values());
    private final JTextArea summaryArea = outputArea();
    private final JTextField questionField = new JTextField();
    private final JTextArea answerArea = outputArea();
    private final JLabel status = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();

    public MainWindow(StudyAgentController controller) {
        this.controller = controller;
        buildLayout();
        refreshCourses(null);
    }

    /** Shows the window (must be called on the Swing event thread). */
    public void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setSize(1000, 650);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    JPanel getRootPanel() {
        return root;
    }

    JTabbedPane getTabs() {
        return tabs;
    }

    // ------------------------------------------------------------------ layout

    private void buildLayout() {
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeft(), buildTabs());
        split.setDividerLocation(330);
        root.add(split, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
        setStatus("Ready. AI provider: " + controller.getLlmName());
        root.add(status, BorderLayout.SOUTH);
    }

    private JPanel buildLeft() {
        courseList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        courseList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshMaterials();
            }
        });
        materialList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton newCourse = new JButton("New Course");
        newCourse.addActionListener(e -> onNewCourseClicked());
        JButton upload = new JButton("Upload Material");
        upload.addActionListener(e -> onUploadClicked());

        JPanel courseBox = titled("Courses", new JScrollPane(courseList));
        JPanel courseButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        courseButtons.add(newCourse);
        courseBox.add(courseButtons, BorderLayout.SOUTH);

        JPanel materialBox = titled("Materials in selected course", new JScrollPane(materialList));
        JPanel materialButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        materialButtons.add(upload);
        materialBox.add(materialButtons, BorderLayout.SOUTH);

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 8));
        left.add(courseBox);
        left.add(materialBox);
        left.setPreferredSize(new Dimension(320, 500));
        return left;
    }

    private JTabbedPane buildTabs() {
        // F02 Summarize
        JButton summarize = new JButton("Summarize");
        summarize.addActionListener(e -> onSummarizeClicked());
        JPanel sumTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sumTop.add(new JLabel("Select a material, choose a length:"));
        sumTop.add(lengthBox);
        sumTop.add(summarize);
        lengthBox.setSelectedItem(SummaryLength.MEDIUM);
        JPanel sumPanel = new JPanel(new BorderLayout(4, 4));
        sumPanel.add(sumTop, BorderLayout.NORTH);
        sumPanel.add(new JScrollPane(summaryArea), BorderLayout.CENTER);
        tabs.addTab("Summarize (F02)", sumPanel);

        // F03 Ask
        JButton ask = new JButton("Ask");
        ask.addActionListener(e -> onAskClicked());
        questionField.addActionListener(e -> onAskClicked());
        JPanel askTop = new JPanel(new BorderLayout(4, 4));
        askTop.add(new JLabel("Question about the selected course: "), BorderLayout.WEST);
        askTop.add(questionField, BorderLayout.CENTER);
        askTop.add(ask, BorderLayout.EAST);
        JPanel askPanel = new JPanel(new BorderLayout(4, 4));
        askPanel.add(askTop, BorderLayout.NORTH);
        askPanel.add(new JScrollPane(answerArea), BorderLayout.CENTER);
        tabs.addTab("Ask (F03)", askPanel);
        return tabs;
    }

    // ------------------------------------------------------------------ actions (F01-F03)

    void onNewCourseClicked() {
        String name = JOptionPane.showInputDialog(frame, "Course name (e.g. EECS3311):", "New Course",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        try {
            Course c = controller.createCourse(name);
            refreshCourses(c.getId());
            setStatus("Created course " + c.getName());
        } catch (StudyException e) {
            showError(e.getMessage());
        }
    }

    void onUploadClicked() {
        Course course = courseList.getSelectedValue();
        if (course == null) {
            showError("Select or create a course first.");
            return;
        }
        JFileChooser chooser = new JFileChooser(new File("sample-data").isDirectory() ? new File("sample-data") : null);
        chooser.setFileFilter(new FileNameExtensionFilter("Study materials (.txt, .md)", "txt", "md", "markdown"));
        chooser.setAcceptAllFileFilterUsed(true);
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        upload(course, file, false);
    }

    private void upload(Course course, File file, boolean replace) {
        try {
            Material m = controller.uploadMaterial(course.getId(), file.toPath(), replace);
            refreshCourses(course.getId());
            if (!m.hasText()) {
                showError("No text found in " + m.getFileName() + " (empty or scanned file). It was saved without chunks.");
            }
            setStatus("Uploaded " + m);
        } catch (DuplicateMaterialException e) {
            int choice = JOptionPane.showConfirmDialog(frame, e.getMessage() + "\nReplace it?", "Duplicate file",
                    JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                upload(course, file, true);
            }
        } catch (StudyException e) {
            showError(e.getMessage());
        }
    }

    void onSummarizeClicked() {
        Course course = courseList.getSelectedValue();
        Material material = materialList.getSelectedValue();
        if (course == null || material == null) {
            showError("Select a course and one of its materials first.");
            return;
        }
        SummaryLength length = (SummaryLength) lengthBox.getSelectedItem();
        summaryArea.setText("Summarizing " + material.getFileName() + " ...");
        runInBackground("Summarizing with " + controller.getLlmName() + " ...",
                () -> controller.summarize(course.getId(), material.getId(), length),
                (Summary s) -> {
                    summaryArea.setText(material.getFileName() + " (" + length.name().toLowerCase() + ")\n\n" + s.toText());
                    summaryArea.setCaretPosition(0);
                },
                summaryArea);
    }

    void onAskClicked() {
        Course course = courseList.getSelectedValue();
        String question = questionField.getText();
        if (course == null) {
            showError("Select a course first.");
            return;
        }
        if (question.isBlank()) {
            showError("Type a question first.");
            return;
        }
        answerArea.setText("Q: " + question + "\n\nSearching your materials ...");
        runInBackground("Asking " + controller.getLlmName() + " ...",
                () -> controller.askQuestion(course.getId(), question),
                (Answer a) -> {
                    answerArea.setText("Q: " + question + "\n\n" + a.toText());
                    answerArea.setCaretPosition(0);
                },
                answerArea);
    }

    // ------------------------------------------------------------------ helpers

    private <T> void runInBackground(String busyText, Callable<T> work, Consumer<T> onDone, JTextArea errorTarget) {
        setStatus(busyText);
        frame.getRootPane().setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                frame.getRootPane().setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    onDone.accept(get());
                    setStatus("Done. AI provider: " + controller.getLlmName());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String msg = cause instanceof StudyException || cause instanceof IllegalArgumentException
                            ? cause.getMessage() : "Unexpected error: " + cause;
                    errorTarget.setText("");
                    showError(msg);
                }
            }
        }.execute();
    }

    private void refreshCourses(String selectId) {
        courseModel.clear();
        try {
            for (Course c : controller.listCourses()) {
                courseModel.addElement(c);
            }
        } catch (StudyException e) {
            showError(e.getMessage());
        }
        for (int i = 0; i < courseModel.size(); i++) {
            if (courseModel.get(i).getId().equals(selectId)) {
                courseList.setSelectedIndex(i);
                return;
            }
        }
        if (!courseModel.isEmpty() && courseList.getSelectedIndex() < 0) {
            courseList.setSelectedIndex(0);
        }
        refreshMaterials();
    }

    private void refreshMaterials() {
        materialModel.clear();
        Course c = courseList.getSelectedValue();
        if (c != null) {
            for (Material m : c.getMaterials()) {
                materialModel.addElement(m);
            }
            if (!materialModel.isEmpty()) {
                materialList.setSelectedIndex(0);
            }
        }
    }

    private static JTextArea outputArea() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        a.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return a;
    }

    private static JPanel titled(String title, java.awt.Component content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    @Override
    public void showResult(String text) {
        setStatus(text);
    }

    @Override
    public void showError(String message) {
        setStatus("Error: " + message);
        if (frame.isVisible()) {
            JOptionPane.showMessageDialog(frame, message, "StudyMuse", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Convenience launcher used by Main. */
    public static void launch(StudyAgentController controller) {
        SwingUtilities.invokeLater(() -> new MainWindow(controller).show());
    }
}
