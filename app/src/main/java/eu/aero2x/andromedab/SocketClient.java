package eu.aero2x.andromedab;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * A client for the OSX Message Proxy socket server (exists only in v1.2+)
 */

public class SocketClient {
    public AsyncTask socketThread;
    //Our socket parameters
    private String IP;
    private int PORT;
    public SocketResponseHandler responseHandler;

    /**
     * Create a socket client for OSX Message Proxy v1.2+
     * @param ipIn The raw IP (i.e. 184.123.143.102)
     * @param portIn The port the socket servr is running at (usually 8736)
     * @param responseHandlerIn A response handler for the socket
     */
    public SocketClient(String ipIn,int portIn,SocketResponseHandler responseHandlerIn) {
        IP = ipIn; PORT = portIn; responseHandler = responseHandlerIn;
        startSocket();
    }

    private void startSocket() {
        socketThread = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                //Keep going until we're canceled
                while (!isCancelled()) {
                    try {
                        Log.d("Socket", "Starting");
                        Socket socket = new Socket(IP, PORT);
                        socket.setSoTimeout(30*1000); //We should be sending heartbeat every 2 seconds on poll so this shouldn't be a concern.
                        BufferedReader socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        PrintWriter socketWriter = new PrintWriter(socket.getOutputStream(), true);

                        boolean isAuthenticated = false;
                        while (socket.isConnected() && !isCancelled()) {
                            final String response = socketReader.readLine();
                            Log.d("SocketPrep", response);
                            //If we've already successfully logged in, auto pass messages to our handler
                            if (isAuthenticated && !response.equals("TCPALIVE")) {
                                responseHandler.handleResponse(response);
                            }
                            //Check if we got our socket alive and it's ready for our token
                            else if (response.equals("ACK")) {
                                //Write our token. We have to do this ASAP or else we are failed
                                socketWriter.println(APP_CONSTANTS.SERVER_PROTECTION_TOKEN);
                            } else if (response.equals("READY")) {
                                Log.d("SocketPrep", "Socket server has accepted our login! Ready");
                                //We're ready, so set our ready so we don't handle these in our prepare anymore.
                                isAuthenticated = true;

                            }
                        }
                        //Try to close just in case
                        socket.close();
                    } catch (Exception e) {
                        Log.d("SocketPrep", "Failed, shutting down! ---> Will retry in 5 seconds");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e1) {}
                        e.printStackTrace();
                    }
                }
                return null;
            }
        };
        socketThread.execute();
    }
}
