package me.hesamai.advancedduty.api.event;

import me.hesamai.advancedduty.api.DutyCause;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired <b>before</b> a player goes on or off duty. Cancelling it keeps the player in the current
 * state. Forced causes ({@link DutyCause#isForced()}) cannot be cancelled.
 */
public class DutyToggleEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean enabling;
    private final String reason;
    private final DutyCause cause;
    private final CommandSender initiator;
    private boolean cancelled;

    public DutyToggleEvent(@NotNull Player player, boolean enabling, @Nullable String reason,
                           @NotNull DutyCause cause, @Nullable CommandSender initiator) {
        super(player);
        this.enabling = enabling;
        this.reason = reason;
        this.cause = cause;
        this.initiator = initiator;
    }

    /** {@code true} when the player is about to go ON duty. */
    public boolean isEnabling() { return enabling; }

    @Nullable public String getReason() { return reason; }

    @NotNull public DutyCause getCause() { return cause; }

    /** The sender that requested the change (may be null for automatic changes). */
    @Nullable public CommandSender getInitiator() { return initiator; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel && !cause.isForced(); }

    @NotNull @Override
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}
