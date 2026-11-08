package com.studymuse.progress;

/**
 * Design pattern: OBSERVER (observer interface).
 * Anything that wants to react to progress changes (the GUI dashboard, the CLI) implements this and
 * subscribes to the ProgressTracker. The services that report progress never know who is listening.
 */
public interface ProgressObserver {

    void onProgressUpdated(ProgressEvent event);
}
