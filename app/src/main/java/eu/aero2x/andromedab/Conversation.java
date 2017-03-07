package eu.aero2x.andromedab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.models.IUser;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;

public class Conversation extends AppCompatActivity {
    public static String lastMessageGUID = ""; //The "last back" message which will have our delivery status, etc
    MessagesListAdapter messagesListAdapter;
    String hash;
    public static String IDs;
    String displayName;
    boolean hashManualCustomName = false;

    JSONObject parentConversation;
    public static ArrayList<Message> messageDataStore; //Hold all our message data
    private WebSocketClient mWebSocketClient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);
        //Start to pull out our data
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            try {
                JSONObject conversation = new JSONObject(extras.getString("conversationJSONString"));
                parentConversation = conversation;
                hash = conversation.getString("chat_id");
                IDs = conversation.getString("IDs");
                displayName = conversation.getString("display_name");
                hashManualCustomName = conversation.getBoolean("has_manual_display_name");

                setTitle(displayName); //and setup our title!
            }catch (JSONException e) {
                e.printStackTrace();
                UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Intent Parse Error\n" + e.getMessage());
            }


            MessagesList messagesList = (MessagesList) findViewById(R.id.messagesList);
            ImageLoader imageLoader = new ImageLoader() {
                @Override
                public void loadImage(ImageView imageView, String url) {
                    //If you using another library - write here your way to load image
                }
            };

            MessagesListAdapter.HoldersConfig holdersConfig = new MessagesListAdapter.HoldersConfig();
            holdersConfig.setOutcoming(CustomOutgoingMessage.class,R.layout.custom_outgoing_message_holder);
            holdersConfig.setIncoming(CustomIncomingMessage.class,R.layout.custom_incoming_message_holder);
            messagesListAdapter = new MessagesListAdapter<>("0",holdersConfig, imageLoader); //0 here is the sender id which is always 0 with RemoteMessages
            messagesList.setAdapter(messagesListAdapter);

            //Now we're ready, setup!
            setupMessages();
        }else {
            UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Missing conversation hash, did you launch normally?");
        }

        //Setup our input bar
        final MessageInput inputView = (MessageInput)findViewById(R.id.input);
        inputView.setInputListener(new MessageInput.InputListener() {

            @Override
            public boolean onSubmit(final CharSequence input) {
                //Create our pseudo message string
                String messageString = "{\n" +
                        "    \"sender\" : \"0\",\n" +
                        "    \"human_name\" : \" \",\n" +
                        "    \"date_read\" : 0,\n" +
                        "    \"message_id\" : 0000,\n" +
                        "    \"date\" : "+ ((System.currentTimeMillis()/1000)-978307200L) + ",\n" + //Convert epoch to cocoa
                        "    \"text\" : \"" + input.toString().replace("\"","\\\"") +"\",\n" +
                        "    \"is_from_me\" : 1,\n" +
                        "    \"error\" : 0,\n" +
                        "    \"guid\" : \"----3-------\",\n" +
                        "    \"date_delivered\" : 0,\n" +
                        "    \"has_attachments\" : false\n " +
                        "  }";
                try {
                    JSONObject messageBundle = new JSONObject(messageString);
                    //"convert" our json into messages. This is wasteful but updates everything I've already set up to keep last back properly
                    ArrayList<Message> newMessages = parseMessageBundle(messageBundle);
                    //Add it to the view
                    messagesListAdapter.addToStart(newMessages.get(0), true);
                    messageDataStore.add(0,newMessages.get(0));
                    String recipients;
                    if (hashManualCustomName) {
                        recipients = IDs;
                        System.out.println("WE HAVE A CUSTOM NAME " + IDs);
                    }else {
                        recipients = displayName;
                        System.out.println("NO HAVE A CUSTOM NAME " + displayName);
                    }

                    RemoteMessagesInterface.sendMessage(recipients,input.toString(),Conversation.this,new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            //The message sent!
                            UITools.showSnackBar(findViewById(android.R.id.content),"Sent!", Snackbar.LENGTH_SHORT);


                        }
                    },  new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            //We couldn't connect, die.
                            String err = (error.toString()==null)?"Generic network error":error.toString();
                            UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Unable to send message! Copied!\n" + err);
                            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Copied message",input.toString()));
                        }
                    });
                }catch (JSONException e) {
                    //We should never get here. The json is manually generated and works
                    e.printStackTrace();
                    UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Somethings really broke\nThe JSON send generator broke!");
                }
                return true;
            }
        });
    }


    @Override
    public void onStop(){
        super.onStop();
        Log.d("Conversation","ON STOP");
        //Close our websocket
        if (mWebSocketClient != null) {
            mWebSocketClient.close();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("Conversation","On resume");
    }

    private void setupMessages() {

        RemoteMessagesInterface.getMessagesForConversation(Integer.valueOf(hash), this, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                //We have our json messages, now we just need to parse it
                try {
                    JSONArray messageBundle = new JSONArray(response);


                    //Now let's nab our messages. Setup data storage
                    messageDataStore = new ArrayList<>();
                    for (int i = 0; i != messageBundle.length(); i++) {
                        messageDataStore.addAll(parseMessageBundle(messageBundle.getJSONObject(i))); //Build our message list
                    }
                    messagesListAdapter.addToEnd(messageDataStore,true); //reverse true to get latest at bottom
                }catch (JSONException exception) {
                    UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Unable to parse json data\n" + exception.toString());
                    exception.printStackTrace();
                    Log.w("MessageParserDis","Could parse message " + exception.toString());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                String err = (error.toString()==null)?"Generic network error":error.toString();
                UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Unable to get messages!\n" + err);
            }
        });
    }

    private ArrayList<Message> parseMessageBundle (final JSONObject messageBundle) {
        final ArrayList<Message> messages = new ArrayList<>();
        try {
                //We have a message. Let's add it.
                messages.add(new Message() {
                    @Override
                    public String getId() {
                        try {
                            return messageBundle.getString("guid");
                        }catch (JSONException e) {
                            return "no id error";
                        }
                    }

                    @Override
                    public String getText() {
                        String message = "";
                        try {
                            //Build our message
                            if (messageBundle.getBoolean("has_attachments")) {
                                //Build our url to show the attachment in the browser
                                message = Uri.parse(RemoteMessagesInterface.API_URL + "/attachment").buildUpon().appendQueryParameter("id", messageBundle.getString("attachment_id")).toString();
                            }

                            String messageText = messageBundle.getString("text");

                            if (!messageText.equals("")) {
                                if (messageBundle.getBoolean("has_attachments")) {
                                    //Text + attachment
                                    message = message + "\n" + messageText;
                                }else {
                                    //We have text and there is no attachment
                                    message = messageText;
                                }
                            }//No text, nothing to do

                        }catch (JSONException e) {
                            e.printStackTrace();
                            message = "Couldn't read message text!";
                        }
                        return message;

                    }

                    @Override
                    public IUser getUser() {
                        return new IUser() {
                            @Override
                            public String getId() {
                                try {
                                    if (messageBundle.getInt("is_from_me") == 1) {
                                        return "0";
                                    }else {
                                        return messageBundle.getString("sender");
                                    }
                                }catch (JSONException e) {
                                    Log.w("Conversation,getID","No part id");
                                    return "no part id error";
                                }

                            }

                            @Override
                            public String getName() {
                                String id = getId();
                                try {
                                    return messageBundle.getString("human_name");
                                }catch (JSONException e) {
                                    return "???:" + id;
                                }
                            }

                            @Override
                            public String getAvatar() {
                                return null;
                            }
                        };

                    }

                    @Override
                    public Date getCreatedAt() {
                        try {
                            return new Date((messageBundle.getInt("date") + 978307200L)*1000);
                        }catch (JSONException e) {
                            Log.w("Conversation,getDate","No date");
                            return new Date();
                        }
                    }
                    @Override
                    public String getTimeRead() {
                        try {
                            return ""+messageBundle.getInt("date_read");
                        }catch (JSONException e) {
                            return null; //No time read, null
                        }
                    }

                    @Override
                    public boolean isRead() {
                        try {

                            return messageBundle.getInt("date_read") > 0; //if this we have a date value and it's positive we've been read
                        }catch (JSONException e) {
                            return false;
                        }
                    }
                    @Override
                    public boolean isDelivered() {
                        try {
                            return messageBundle.getInt("date_delivered") > 0;
                        }catch (JSONException e) {
                            return false;
                        }
                    }

                });

            //Store our last back GUID so we know who to display labels on
            String finalMessage = messageBundle.getString("guid");
            //This works because we will ALWAYS parse our messages through this function and so the last one parsed will always be our "lastback"
            lastMessageGUID = finalMessage;

            return messages;
        }catch (JSONException e) {
            UITools.showDismissableSnackBar(findViewById(android.R.id.content),"JSON error:\n" + e.toString());
            return messages;
        }
    }


}
