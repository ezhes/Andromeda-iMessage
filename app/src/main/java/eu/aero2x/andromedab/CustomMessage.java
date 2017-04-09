package eu.aero2x.andromedab;

import com.stfalcon.chatkit.commons.models.MessageContentType;

/**
 * An invisible class which provides MessageContentType in addition to the standard message
 */
public abstract class CustomMessage implements Message, MessageContentType.Image {}
