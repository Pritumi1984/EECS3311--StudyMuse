package com.studymuse.ui;

import com.studymuse.agent.AgentResponse;
import com.studymuse.agent.AgentStep;
import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.ChatMessage;
import com.studymuse.domain.Course;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * F12: chat with the StudyMuse agent. The student types a goal in plain language ("quiz me on
 * Observer", "make a plan for my exam on Dec 10"); the agent picks tools, runs them and answers.
 * Each reply lists the tools it used so the student can see what the agent actually did.
 */
class AgentChatPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient StudyAgentController controller;
    private final transient WorkspaceContext ctx;
    private final transient Runnable onAgentFinished;
    private final JTextArea transcript = new JTextArea();
    private final JTextField input = new JTextField();
    private final JButton send = new JButton("Send");

    AgentChatPanel(StudyAgentController controller, WorkspaceContext ctx, Runnable onAgentFinished) {
        super(new BorderLayout(4, 4));
        this.controller = controller;
        this.ctx = ctx;
        this.onAgentFinished = onAgentFinished;

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Ask the agent to do things for the selected course, e.g. \"Quiz me on Observer with 5 "
                + "questions\" or \"Turn my quiz mistakes into flashcards\"."));
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        transcript.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JButton tools = new JButton("Tools");
        tools.setToolTipText("Show the tools the agent can use");
        tools.addActionListener(e -> showTools());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(send);
        buttons.add(tools);
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        send.addActionListener(e -> onSendClicked());
        input.addActionListener(e -> onSendClicked());

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(transcript), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /** Shows the saved conversation for the newly selected course. */
    void showHistory() {
        Course course = ctx.selectedCourse();
        transcript.setText("");
        if (course == null) {
            return;
        }
        for (ChatMessage m : controller.getChatHistory(course.getId())) {
            append(("user".equals(m.role()) ? "You" : "Agent") + ": " + m.text());
        }
        if (transcript.getText().isEmpty()) {
            append("Agent: Hi! I can search your notes, make quizzes and flashcards, find weak topics, build a "
                    + "study plan and report your progress. What would you like to do?");
        }
    }

    void onSendClicked() {
        Course course = ctx.selectedCourse();
        if (course == null) {
            ctx.showError("Select a course first.");
            return;
        }
        String message = input.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        input.setText("");
        append("You: " + message);
        send.setEnabled(false);
        input.setEnabled(false);
        ctx.runInBackground("Agent is working with " + controller.getLlmName() + " ...",
                () -> controller.chat(course.getId(), message),
                (AgentResponse r) -> {
                    StringBuilder sb = new StringBuilder("Agent: ").append(r.answer());
                    if (!r.steps().isEmpty()) {
                        sb.append("\n   tools used:");
                        for (AgentStep s : r.steps()) {
                            sb.append("\n   - ").append(s.describe());
                        }
                    }
                    append(sb.toString());
                    finish();
                    onAgentFinished.run();
                },
                this::finish);
    }

    private void showTools() {
        StringBuilder sb = new StringBuilder("Agent tools:");
        controller.listTools().forEach(t -> sb.append("\n   - ").append(t.name()).append(": ").append(t.description()));
        append(sb.toString());
    }

    private void finish() {
        send.setEnabled(true);
        input.setEnabled(true);
        input.requestFocusInWindow();
    }

    private void append(String text) {
        if (!transcript.getText().isEmpty()) {
            transcript.append("\n\n");
        }
        transcript.append(text);
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }
}
