package eu.aero2x.andromedab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.squareup.picasso.Picasso;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.models.IUser;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;

public class Conversation extends AppCompatActivity implements MessagesListAdapter.OnMessageClickListener<CustomMessage> {
    public static String lastMessageGUID = ""; //The "last back" message which will have our delivery status, etc
    MessagesListAdapter messagesListAdapter;
    String hash;
    public static String IDs;
    String displayName;
    boolean hashManualCustomName = false;

    JSONObject parentConversation;
    public static ArrayList<Message> messageDataStore; //Hold all our message data
    public SocketClient socketClient;
    private FirebaseAnalytics mFirebaseAnalytics;

    private String latestConversationData = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);
        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);


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
                    Picasso.with(Conversation.this).load(url).into(imageView);
                }
            };

            MessagesListAdapter.HoldersConfig holdersConfig = new MessagesListAdapter.HoldersConfig();
            holdersConfig.setOutcoming(CustomOutgoingMessage.class,R.layout.custom_outgoing_message_holder);
            holdersConfig.setIncoming(CustomIncomingMessage.class,R.layout.custom_incoming_message_holder);
            messagesListAdapter = new MessagesListAdapter<>("0",holdersConfig, imageLoader); //0 here is the sender id which is always 0 with RemoteMessages
            //Enable our tap to open images in browser
            messagesListAdapter.setOnMessageClickListener(this);
            messagesList.setAdapter(messagesListAdapter);

            //Now we're ready, setup!
        }else {
            UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Missing conversation hash, did you launch normally?");
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Missing conversation hash, did you launch normally?");
            mFirebaseAnalytics.logEvent("missing_conversation_hash", bundle);
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
                        "    \"date\" : "+ ((System.currentTimeMillis())/1000-978307200L) + ",\n" + //Convert epoch to cocoa
                        "    \"text\" : \"" + input.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") +"\",\n" + //manually escape for JSON. It's bad but unexploitable
                        "    \"is_from_me\" : 1,\n" +
                        "    \"error\" : -23813,\n" +
                        "    \"guid\" : \"notSent"+ (int)(Math.random()*2000) + "\",\n" + //random bit so we can be sure we replace the right GUID
                        "    \"date_delivered\" : 0,\n" +
                        "    \"is_sent\" : 0,\n" +
                        "    \"has_attachments\" : false\n " +
                        "  }";
                System.out.println("TIMESTAMP:" + ((System.currentTimeMillis())/1000-978307200L));
                Bundle bundle = new Bundle();
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Send message");
                mFirebaseAnalytics.logEvent("message_send", bundle);
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

                    RemoteMessagesInterface.sendMessage(recipients,input.toString(),parentConversation.getBoolean("has_manual_display_name"),Conversation.this,new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            //The message sent!
                            UITools.showSnackBar(findViewById(android.R.id.content),"Dispatched!", Snackbar.LENGTH_SHORT);


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
                    FirebaseCrash.log("JSON message bundle generator broke:"+ messageString);
                    FirebaseCrash.report(e);
                    UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Somethings really broke\nThe JSON send generator broke!");
                }
                return true;
            }
        });
    }



    @Override
    public void onPause() {
        super.onPause();  // Always call the superclass method first
        Log.d("onPauseConversation","SUSPENDING SOCKET");
        if (socketClient != null && socketClient.socketThread != null) {
            socketClient.socketThread.cancel(false);
        }
    }
    @Override
    public void onBackPressed() {
        Bundle bundle = new Bundle();
        if (!latestConversationData.equals("")) {
            bundle.putString("latestSocketConversationData",latestConversationData);
        }
        Intent mIntent = new Intent();
        mIntent.putExtras(bundle);
        setResult(UITools.DATA_NEEDS_REFRESH, mIntent);
        super.onBackPressed();
    }

    @Override
    public void onResume() {
        super.onResume();

        //Get our messages here. This is a very good place to reload messages since we get called at the end of onCreate
        //but we also get called when we come back from sleep or backgrounding so we ought to refresh anyways
        messagesListAdapter.clear();
        messagesListAdapter.notifyDataSetChanged();
        setupMessages();

        if (socketClient != null && socketClient.socketThread.isCancelled() == false) {
            socketClient.socketThread.cancel(false);
        }

        socketClient = new SocketClient(APP_CONSTANTS.SERVER_IP, APP_CONSTANTS.SERVER_SOCKET_PORT, new SocketResponseHandler() {

            @Override
            public void handleResponse(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            JSONObject bundle = new JSONObject(response);
                            if (bundle.getString("type").equals("newMessage")) {
                                //New message
                                JSONObject messageBundle = bundle.getJSONArray("content").getJSONObject(0);
                                if (messageBundle.getInt("chat_id") == Integer.parseInt(hash)) {
                                    //We have a message from our conversation...
                                    ArrayList<Message> newMessages = parseMessageBundle(messageBundle);
                                    messageDataStore.addAll(newMessages); //Build our new ones in
                                    for (Message m : newMessages) {
                                        messagesListAdapter.addToStart(m,true); //reverse true to get latest at bottom
                                    }

                                }
                            }else if (bundle.getString("type").equals("messageSent")) {
                                //We Sent!
                                JSONObject messageBundle = bundle.getJSONArray("content").getJSONObject(0);
                                System.out.println(Integer.parseInt(hash) + " " + messageBundle.getInt("chat_id"));
                                if (messageBundle.getInt("chat_id") == Integer.parseInt(hash)) {
                                    for (Message m : messageDataStore) {
                                        System.out.println(m.getText().trim() + "=="+messageBundle.getString("text"));
                                        if (m.getId().contains("notSent") && m.getText().trim().equals(messageBundle.getString("text").trim())) {
                                            System.out.println("!!! match");
                                            ArrayList<Message> newMessages = parseMessageBundle(messageBundle);
                                            messagesListAdapter.update(m.getId(),newMessages.get(0));
                                            for (Message updatePayloadMessage : newMessages) {
                                                messagesListAdapter.update(m.getId(),updatePayloadMessage);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }else if (bundle.getString("type").equals("messageSendFailure")) {
                                //We couldn't send
                            }else if (bundle.getString("type").equals("conversations")) {
                                //Since we close the parent socket each time we restart we need to keep tabs on the conversations for them, which means letting the parent know about knew conversation changes
                                latestConversationData = bundle.getJSONArray("content").toString();
                            }
                        }catch (JSONException e) {
                            Log.d("handleSocket","failed to parse bundle " + response);
                            e.printStackTrace();
                        }

                    }
                });
            }
        });

    }

    private void setupMessages() {
        //Now let's nab our messages. Setup data storage. We have to set this up ASAP because if we wait someone (will and has) tried to send messages before anything else loaded
        messageDataStore = new ArrayList<>();
        RemoteMessagesInterface.getMessagesForConversation(Integer.valueOf(hash), this, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                //We have our json messages, now we just need to parse it
                try {
                    JSONArray messageBundle = new JSONArray(response);

                    //Stuff our messages
                    for (int i = 0; i != messageBundle.length(); i++) {
                        messageDataStore.addAll(parseMessageBundle(messageBundle.getJSONObject(i))); //Build our message list
                    }
                    messagesListAdapter.addToEnd(messageDataStore,true); //reverse true to get latest at bottom
                }catch (JSONException exception) {
                    UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Unable to parse json data\n" + exception.toString());
                    FirebaseCrash.log("Couldn't parse messagebundle:" + response);
                    FirebaseCrash.report(exception);
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
            messages.add(new CustomMessage() {

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
                            message = Uri.parse(RemoteMessagesInterface.API_URL + "/attachment").buildUpon().appendQueryParameter("id", messageBundle.getString("attachment_id")).appendQueryParameter("t",APP_CONSTANTS.SERVER_PROTECTION_TOKEN).toString();
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

                @Override
                public boolean isSent() {
                    try {
                        return messageBundle.getInt("error") == 0 || messageBundle.getInt("is_sent") == 1;
                    }catch (JSONException e) {
                        return false;
                    }
                }

                @Override
                public String getImageUrl() {
                    try {
                        //Check if we have a resource that can by loaded inline
                        if (messageBundle.getString("uti").contains("png") || messageBundle.getString("uti").contains("jpeg")) {
                            //We have a valid loadable image. Let's build the uri
                            return RemoteMessagesInterface.API_URL + "/attachment?id=" + messageBundle.getString("attachment_id") + "&t=" + APP_CONSTANTS.SERVER_PROTECTION_TOKEN;
                        }else {
                            Log.d("GetImageURL","Didn't support the image type: " + messageBundle.getString("uri"));
                        }
                    }catch (JSONException e) {}
                    //If we're here we didn't have a valid image.
                    return null;
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

    @Override
    public void onMessageClick(CustomMessage message) {
        //Grab our URL if we have it
        String imageUrlIfExsists = message.getImageUrl();
        if (imageUrlIfExsists != null) {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Tapped image to launch it in browser");
            mFirebaseAnalytics.logEvent("message_image_tap", bundle);
            //We have an image. They've tapped the image so we want to open it in the browser
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(Conversation.this, Uri.parse(imageUrlIfExsists));
        }
    }
}
