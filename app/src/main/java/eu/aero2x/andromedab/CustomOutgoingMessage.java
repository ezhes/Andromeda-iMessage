package eu.aero2x.andromedab;

import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.stfalcon.chatkit.utils.DateFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Salman on 2/17/17.
 */

public class CustomOutgoingMessage extends MessagesListAdapter.OutcomingMessageViewHolder<Message> {
    public CustomOutgoingMessage(View itemView) {
        super(itemView);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);

        TextView messageStatus = (TextView)itemView.findViewById(R.id.messageStatus);
        TextView messageText = (TextView)itemView.findViewById(R.id.messageText);

        messageText.setTextIsSelectable(true);
        Linkify.addLinks(messageText,Linkify.ALL);
        messageText.setLinksClickable(true);

        if (message.getId().equals(Conversation.lastMessageGUID)) { //Are we the last back?
            //Horray!
            if (message.isSent()) {
                if (message.isDelivered()) {
                    if (message.isRead()) {
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                        try {
                            Date parsed = sdf.parse(message.getTimeRead());
                            //We are read
                            messageStatus.setText("Read at " + new SimpleDateFormat("h:mm a").format(parsed));
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                    } else {
                        //We are delivered but not read
                        messageStatus.setText("Delivered");
                    }
                } else {
                    //Are we last back BUT not yet sent?
                    //Not delivered/no label
                    messageStatus.setText("Sent");
                }
            }else {
                //Message has not yet been sent
                messageStatus.setText("Sending...");
            }
        }else {
            //We're not last back so no label
            messageStatus.setText("");
        }


        time.setText(DateFormatter.format(message.getCreatedAt(), "h:mm a"));
    }


}
