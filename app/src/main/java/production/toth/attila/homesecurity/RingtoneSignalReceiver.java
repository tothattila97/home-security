package production.toth.attila.homesecurity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

public class RingtoneSignalReceiver extends BroadcastReceiver{

    public RingtoneSignalReceiver(){}

    @Override
    public void onReceive(Context context, Intent intent) {
        String stringExtra = intent.getStringExtra(ImageConsumer.ImageConsumerTAG);
        if(stringExtra.equals("ringtoneIntent")){
            playRingt(context);
        }
    }

    void playRingt(Context context){
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(context, notification);
            ringtone.play();
            //TODO: Valahol leállítani is a ringtonet stop()-al
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
