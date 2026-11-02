package com.studymuse.ui;

import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.Course;
import com.studymuse.domain.Difficulty;
import com.studymuse.domain.Question;
import com.studymuse.domain.QuestionType;
import com.studymuse.domain.Quiz;
import com.studymuse.domain.QuizRequest;
import com.studymuse.domain.QuizResult;
import com.studymuse.services.QuizService;
import com.studymuse.services.StudyException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F07 "New Quiz" form plus the quiz itself (F08: Submit grades it): multiple-choice questions as radio buttons and short-answer
 * questions as text fields. Previously generated quizzes can be reopened from the drop-down.
 */
class QuizPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient StudyAgentController controller;
    private final transient WorkspaceContext ctx;

    private final JTextField topicsField = new JTextField(16);
    private final JSpinner countSpinner = new JSpinner(
            new SpinnerNumberModel(5, QuizService.MIN_QUESTIONS, QuizService.MAX_QUESTIONS, 1));
    private final JComboBox<Difficulty> difficultyBox = new JComboBox<>(Difficulty.values());
    private final JCheckBox mcBox = new JCheckBox("Multiple choice", true);
    private final JCheckBox saBox = new JCheckBox("Short answer", true);
    private final JComboBox<Quiz> quizBox = new JComboBox<>();
    protected final JPanel questionsPanel = new WidthTrackingPanel();
    protected final JLabel info = new JLabel("Enter topics from your notes (comma-separated) and click Generate Quiz.");

    /** Answer inputs of the quiz on screen, by question id (radio group or text field). */
    protected final transient Map<String, Object> answerInputs = new LinkedHashMap<>();
    protected transient Quiz currentQuiz;
    private boolean loadingQuizzes;

    QuizPanel(StudyAgentController controller, WorkspaceContext ctx) {
        super(new BorderLayout(4, 4));
        this.controller = controller;
        this.ctx = ctx;

        JButton generate = new JButton("Generate Quiz");
        generate.addActionListener(e -> onGenerateClicked());
        difficultyBox.setSelectedItem(Difficulty.MEDIUM);
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        form.add(new JLabel("Topics:"));
        form.add(topicsField);
        form.add(new JLabel("Questions:"));
        form.add(countSpinner);
        form.add(new JLabel("Difficulty:"));
        form.add(difficultyBox);
        JPanel form2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        form2.add(new JLabel("Question types:"));
        form2.add(mcBox);
        form2.add(saBox);
        form2.add(generate);

        JButton refresh = new JButton("Reload");
        refresh.addActionListener(e -> reloadQuizzes(null));
        quizBox.addActionListener(e -> {
            if (!loadingQuizzes && quizBox.getSelectedItem() instanceof Quiz q) {
                showQuiz(q);
            }
        });
        JPanel pick = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        pick.add(new JLabel("Saved quizzes:"));
        pick.add(quizBox);
        pick.add(refresh);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        form2.setAlignmentX(Component.LEFT_ALIGNMENT);
        pick.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(form);
        top.add(form2);
        top.add(pick);

        questionsPanel.setLayout(new BoxLayout(questionsPanel, BoxLayout.Y_AXIS));
        questionsPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JScrollPane scroll = new JScrollPane(questionsPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);
    }

    private final JButton submitButton = new JButton("Submit Answers");

    /** Bottom bar with the F08 Submit button. */
    protected JComponent buildBottom() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        submitButton.setEnabled(false);
        submitButton.addActionListener(e -> onSubmitClicked());
        bottom.add(submitButton);
        bottom.add(info);
        return bottom;
    }

    /** F08: collects the answers from the form and grades them in the background. */
    void onSubmitClicked() {
        Course course = ctx.selectedCourse();
        Quiz quiz = currentQuiz;
        if (course == null || quiz == null) {
            return;
        }
        Map<String, String> answers = new LinkedHashMap<>();
        int blank = 0;
        for (Map.Entry<String, Object> e : answerInputs.entrySet()) {
            String a = "";
            if (e.getValue() instanceof ButtonGroup g && g.getSelection() != null) {
                a = g.getSelection().getActionCommand();
            } else if (e.getValue() instanceof JTextField f) {
                a = f.getText().trim();
            }
            if (a.isEmpty()) {
                blank++;
            }
            answers.put(e.getKey(), a);
        }
        if (blank > 0 && JOptionPane.showConfirmDialog(ctx.window(), blank + " question(s) are unanswered and will score 0. Submit anyway?",
                "Submit quiz", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        submitButton.setEnabled(false);
        info.setText("Grading ...");
        ctx.runInBackground("Grading with " + controller.getLlmName() + " ...",
                () -> controller.submitQuiz(course.getId(), quiz.getId(), answers),
                (QuizResult r) -> showResult(r),
                () -> {
                    submitButton.setEnabled(true);
                    info.setText("Grading failed - your answers are still on screen.");
                });
    }

    private void showResult(QuizResult r) {
        questionsPanel.removeAll();
        JTextArea area = new JTextArea(r.toText());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        questionsPanel.add(area);
        questionsPanel.revalidate();
        questionsPanel.repaint();
        info.setText(String.format("Score: %.0f%%. Pick the quiz again from the list to retake it.", r.getScorePercent()));
        ctx.setStatus(String.format("Quiz graded: %.0f%%", r.getScorePercent()));
    }

    /** A panel that always matches the scroll pane's width, so long questions and feedback wrap. */
    private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return r.height - 16;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    void setTopics(String topics) {
        topicsField.setText(topics);
    }

    void onGenerateClicked() {
        Course course = ctx.selectedCourse();
        if (course == null) {
            ctx.showError("Select a course first.");
            return;
        }
        EnumSet<QuestionType> types = EnumSet.noneOf(QuestionType.class);
        if (mcBox.isSelected()) {
            types.add(QuestionType.MULTIPLE_CHOICE);
        }
        if (saBox.isSelected()) {
            types.add(QuestionType.SHORT_ANSWER);
        }
        QuizRequest request = new QuizRequest(Arrays.asList(topicsField.getText().split(",")),
                (Integer) countSpinner.getValue(), types, (Difficulty) difficultyBox.getSelectedItem());
        info.setText("Generating quiz ...");
        ctx.runInBackground("Generating quiz with " + controller.getLlmName() + " ...",
                () -> controller.generateQuiz(course.getId(), request),
                (Quiz quiz) -> {
                    reloadQuizzes(quiz.getId());
                    if (quiz.getQuestions().size() < request.count()) {
                        info.setText("Only " + quiz.getQuestions().size() + " valid questions were created (asked for "
                                + request.count() + ").");
                    }
                },
                () -> info.setText("Quiz generation failed."));
    }

    /** Refills the saved-quiz drop-down for the selected course and shows the given (or newest) quiz. */
    void reloadQuizzes(String selectId) {
        Course course = ctx.selectedCourse();
        loadingQuizzes = true;
        quizBox.removeAllItems();
        Quiz toShow = null;
        try {
            if (course != null) {
                for (Quiz q : controller.listQuizzes(course.getId())) {
                    quizBox.addItem(q);
                    if (q.getId().equals(selectId)) {
                        toShow = q;
                    }
                }
            }
        } catch (StudyException e) {
            ctx.showError(e.getMessage());
        } finally {
            loadingQuizzes = false;
        }
        if (toShow == null && quizBox.getItemCount() > 0) {
            toShow = quizBox.getItemAt(quizBox.getItemCount() - 1);
        }
        if (toShow != null) {
            quizBox.setSelectedItem(toShow);
            showQuiz(toShow);
        }
    }

    /** Renders the quiz as an answerable form. */
    protected void showQuiz(Quiz quiz) {
        currentQuiz = quiz;
        answerInputs.clear();
        questionsPanel.removeAll();
        JLabel title = new JLabel(quiz.getTitle() + "  -  " + quiz.getDifficulty().name().toLowerCase());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        questionsPanel.add(title);
        int n = 1;
        for (Question q : quiz.getQuestions()) {
            JTextArea text = new JTextArea(n++ + ". " + q.getText() + "   [" + q.getTopic() + "]");
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setOpaque(false);
            text.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            text.setAlignmentX(Component.LEFT_ALIGNMENT);
            questionsPanel.add(Box.createVerticalStrut(10));
            questionsPanel.add(text);
            if (q.getType() == QuestionType.MULTIPLE_CHOICE) {
                ButtonGroup group = new ButtonGroup();
                List<JRadioButton> buttons = new ArrayList<>();
                char letter = 'A';
                for (String option : q.getOptions()) {
                    JRadioButton rb = new JRadioButton(letter++ + ") " + option);
                    rb.setActionCommand(option);
                    rb.setAlignmentX(Component.LEFT_ALIGNMENT);
                    group.add(rb);
                    buttons.add(rb);
                    questionsPanel.add(rb);
                }
                answerInputs.put(q.getId(), group);
            } else {
                JTextField field = new JTextField();
                field.setAlignmentX(Component.LEFT_ALIGNMENT);
                field.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 28));
                questionsPanel.add(field);
                answerInputs.put(q.getId(), field);
            }
        }
        questionsPanel.revalidate();
        questionsPanel.repaint();
        info.setText(quiz.getQuestions().size() + " questions. Answer them, then Submit Answers.");
        submitButton.setEnabled(true);
    }
}
