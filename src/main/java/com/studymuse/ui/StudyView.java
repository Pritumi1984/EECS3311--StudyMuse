package com.studymuse.ui;

/** What every user interface (GUI and CLI) can do: show a result or an error. */
public interface StudyView {

    void showResult(String text);

    void showError(String message);
}
