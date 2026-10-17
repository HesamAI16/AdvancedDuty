package me.hesamai.advancedduty.api;

/** Outcome of an enable / disable request. */
public enum DutyResult {
    SUCCESS,
    ALREADY_IN_STATE,
    NO_PERMISSION,
    ON_COOLDOWN,
    DATA_NOT_READY,
    CANCELLED,
    WORLD_BLOCKED,
    REASON_REQUIRED,
    ERROR;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
