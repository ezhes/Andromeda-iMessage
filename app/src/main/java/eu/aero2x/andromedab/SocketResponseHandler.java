package eu.aero2x.andromedab;

/**
 * Used as a callback holder for SocketClient
 */

public interface SocketResponseHandler {
    /**
     * A callback for when the socket returns some data while properly authenticated.
     * @param response The socket line read
     */
    void handleResponse(final String response);
}
