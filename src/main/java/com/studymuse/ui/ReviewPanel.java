package com.studymuse.ui;

import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.Course;
import com.studymuse.domain.Flashcard;
import com.studymuse.domain.Rating;
import com.studymuse.services.StudyException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * F06 review mode: shows one due card at a time, "Flip" reveals the answer, and the
 * Again / Hard / Good / Easy buttons reschedule it with spaced repetition.
 */
class ReviewPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient StudyAgentController controller;
    private final transient WorkspaceContext ctx;

    private final JLabel progress = new JLabel("Press Start Review to see the cards due today.");
    private final JTextArea cardArea = new JTextArea();
    private final JButton flip = new JButton("Flip");
    private final transient List<JButton> rateButtons = new ArrayList<>();

    private transient List<Flashcard> due = new ArrayList<>();
    private int position;
    private int reviewed;
    private int remembered;
    private String courseId;

    ReviewPanel(StudyAgentController controller, WorkspaceContext ctx) {
        super(new BorderLayout(6, 6));
        this.controller = controller;
        this.ctx = ctx;

        JButton start = new JButton("Start Review");
        start.addActionListener(e -> onStartClicked());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(start);
        top.add(progress);

        cardArea.setEditable(false);
        cardArea.setLineWrap(true);
        cardArea.setWrapStyleWord(true);
        cardArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        cardArea.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(150, 160, 180)),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        flip.addActionListener(e -> onFlipClicked());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        bottom.add(flip);
        for (Rating r : Rating.values()) {
            String label = r.name().charAt(0) + r.name().substring(1).toLowerCase();
            JButton b = new JButton(label);
            b.setHorizontalAlignment(SwingConstants.CENTER);
            b.addActionListener(e -> onRate(r));
            rateButtons.add(b);
            bottom.add(b);
        }
        add(top, BorderLayout.NORTH);
        add(cardArea, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setControls(false, false);
    }

    void onStartClicked() {
        Course course = ctx.selectedCourse();
        if (course == null) {
            ctx.showError("Select a course first.");
            return;
        }
        courseId = course.getId();
        try {
            due = new ArrayList<>(controller.getDueCards(courseId, LocalDate.now()));
        } catch (StudyException e) {
            ctx.showError(e.getMessage());
            return;
        }
        position = 0;
        reviewed = 0;
        remembered = 0;
        if (due.isEmpty()) {
            String msg = controller.nextDueDate(courseId)
                    .map(d -> "No cards are due today. Next review: " + d + ".")
                    .orElse("No flashcards yet. Create a deck in the Flashcards tab.");
            progress.setText(msg);
            cardArea.setText("");
            setControls(false, false);
            return;
        }
        showFront();
    }

    private void showFront() {
        Flashcard c = due.get(position);
        progress.setText("Card " + (position + 1) + " of " + due.size() + "   |   topic: " + c.getTopic());
        cardArea.setText(c.getFront());
        setControls(true, false);
    }

    void onFlipClicked() {
        Flashcard c = due.get(position);
        cardArea.setText(c.getFront() + "\n\n----------------\n\n" + c.getBack());
        setControls(false, true);
    }

    void onRate(Rating rating) {
        Flashcard c = due.get(position);
        try {
            Flashcard updated = controller.reviewCard(courseId, c.getId(), rating);
            reviewed++;
            if (rating.getCorrectness() >= 1.0) {
                remembered++;
            }
            ctx.setStatus("Saved: '" + c.getFront() + "' next review " + updated.getNextReview()
                    + " (" + updated.getIntervalDays() + " day(s)).");
        } catch (StudyException e) {
            ctx.showError(e.getMessage());
            return;
        }
        position++;
        if (position < due.size()) {
            showFront();
        } else {
            cardArea.setText(String.format("Session complete!%n%n%d card(s) reviewed, %d%% remembered (Good/Easy).",
                    reviewed, Math.round(100.0 * remembered / reviewed)));
            progress.setText("All due cards reviewed.");
            setControls(false, false);
        }
    }

    private void setControls(boolean canFlip, boolean canRate) {
        flip.setEnabled(canFlip);
        for (JButton b : rateButtons) {
            b.setEnabled(canRate);
        }
    }
}
