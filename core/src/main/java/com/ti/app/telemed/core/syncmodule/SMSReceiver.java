package com.ti.app.telemed.core.syncmodule;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SMSReceiver extends BroadcastReceiver {

    private static final String TAG = "SMSReceiver";
    // TODO definire il mittente
    private static final String SENDER="TODO";

    @Override
    public void onReceive(Context context, Intent intent) {

    /*
        // Retrieve the sms message chunks from the intent
        SmsMessage[] rawSmsChunks;
        try {
            rawSmsChunks = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (NullPointerException ignored) { return; }

        // Gather all sms chunks for each sender separately
        Map<String, StringBuilder> sendersMap = new HashMap<>();
        for (SmsMessage rawSmsChunk : rawSmsChunks) {
            if (rawSmsChunk != null) {
                String sender = rawSmsChunk.getDisplayOriginatingAddress();
                String smsChunk = rawSmsChunk.getDisplayMessageBody();
                StringBuilder smsBuilder;
                if ( ! sendersMap.containsKey(sender) ) {
                    // For each new sender create a separate StringBuilder
                    smsBuilder = new StringBuilder();
                    sendersMap.put(sender, smsBuilder);
                } else {
                    // Sender already in map. Retrieve the StringBuilder
                    smsBuilder = sendersMap.get(sender);
                }
                // Add the sms chunk to the string builder
                smsBuilder.append(smsChunk);
            }
        }

        // Loop over every sms thread and concatenate the sms chunks to one piece
        for ( Map.Entry<String, StringBuilder> smsThread : sendersMap.entrySet() ) {
            String sender  = smsThread.getKey();
            StringBuilder smsBuilder = smsThread.getValue();
            String message = smsBuilder.toString();
            handler.handleSms(sender, message);
        }
     */


        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            SmsMessage[] smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            for (SmsMessage smsMessage : smsMessages) {
                String phoneNumber = smsMessage.getDisplayOriginatingAddress();
                String message = smsMessage.getDisplayMessageBody();
                String body = smsMessage.getMessageBody();
                String originAddress = smsMessage.getOriginatingAddress();
                if (SENDER.equals(phoneNumber))
                    Log.i(TAG, "Valid SMS Received: sender="+phoneNumber+" - message="+message+" - body="+body+" - originAddr="+originAddress);
                else
                    Log.i(TAG, "Not Valid SMS Received: sender="+phoneNumber+" - message="+message+" - body="+body+" - originAddr="+originAddress);
                setResultCode(Activity.RESULT_OK);
            }
        } else {
            setResultCode(Activity.RESULT_CANCELED);
        }
    }
}
