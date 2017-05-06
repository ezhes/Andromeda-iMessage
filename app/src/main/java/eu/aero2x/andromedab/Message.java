package eu.aero2x.andromedab;

import com.stfalcon.chatkit.commons.models.IMessage;

import java.util.Date;

/**
 * An extension of the IMessage interface
 * I've added more iMessage required features like delivery status and read status.
 */

public interface Message extends IMessage {
    /**
     * Returns the read status
     * @return has this message been read
     */
    boolean isRead();

    /**
     * If the message has been read, when?
     * @return The time string given by remotemessages for read.
     */
    String getTimeRead();

    /**
     * Has the message delivered yet?
     * @return Delivery status
     */
    boolean isDelivered();

    /**
     * Has the message been sent on the server?
     * @return
     */
    boolean isSent();
}
