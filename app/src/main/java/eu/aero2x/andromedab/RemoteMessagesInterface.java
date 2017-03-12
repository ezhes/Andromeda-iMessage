package eu.aero2x.andromedab;

import android.content.Context;
import android.util.Log;

import com.android.internal.http.multipart.MultipartEntity;
import com.android.internal.http.multipart.Part;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Salman on 11/27/16.
 */

public class RemoteMessagesInterface {
    public final static String API_URL = APP_CONSTANTS.API_URL; //API URL WITHOUT THE TRAILING SLASH. Example: http://yourdomain:port

    public static void messagesEndPointReachable(Context context, Response.Listener<String> onResponse,Response.ErrorListener onError) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);
        //                  Send an invalid request which should return (Love, Jess)
        StringRequest stringRequester = new StringRequest(Request.Method.GET,API_URL + "/isUp",onResponse,onError);

        // Add the request to the RequestQueue.
        queue.add(stringRequester);
    }

    public static void getConversations (Context context, Response.Listener<String> onResponse,Response.ErrorListener onError) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        StringRequest stringRequester = new StringRequest(Request.Method.GET,API_URL + "/conversations",onResponse,onError);

        // Add the request to the RequestQueue.
        queue.add(stringRequester);
    }


    public static void getMessagesForConversation (final int conversationID, Context context, Response.Listener<String> onResponse, Response.ErrorListener onError) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest stringRequester = new StringRequest(Request.Method.POST, API_URL + "/messages", onResponse, onError){
                @Override
                public Map<String, String> getParams() throws AuthFailureError {
                    Map<String, String>  params = new HashMap<String, String>();
                    //afterID is how we send what we want in our request
                    params.put("conversationID", "" + conversationID);

                    return params;
                }
            };
            // Add the request to the RequestQueue.
            queue.add(stringRequester);

    }

    public static void sendMessage (final String recipients, final String message, Context context, Response.Listener<String> onResponse, Response.ErrorListener onError) {
        RequestQueue queue = Volley.newRequestQueue(context);
        StringRequest stringRequester = new StringRequest(Request.Method.POST, API_URL + "/send", onResponse, onError) {

            protected Map<String, String> getParams() throws com.android.volley.AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("participants",recipients);
                params.put("message", message);
                return params;
            }
        };
        // Add the request to the RequestQueue.
        queue.add(stringRequester);
    }


}
