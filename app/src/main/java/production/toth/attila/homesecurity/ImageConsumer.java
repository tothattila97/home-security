package production.toth.attila.homesecurity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

import production.toth.attila.homesecurity.Kryonet.SignalClient;

public class ImageConsumer implements Runnable {

    //private BlockingQueue<Image> queue;
    public static final String ImageConsumerTAG = "somethingHappened";
    private BlockingQueue<Bitmap> queue;
    private Image first;
    private Bitmap firstbitmap;
    private Image second;
    private Bitmap secondbitmap;
    public  long starttime, difference;
    public Activity a;

    public static ImageConsumer instance = null;
    protected ImageConsumer(){}
    public static ImageConsumer getInstance(){
        return instance;
    }

    public ImageConsumer(/*BlockingQueue<Image> q*/ BlockingQueue<Bitmap> q, Activity activity) {
            callback = (IRingtoneCallback) activity;
            this.a = activity;
            this.queue = q;
    }

    @Override
    public void run() {
        try{
            //second = queue.take();
            secondbitmap = queue.take();
            while(true){
                //if(queue.size() > 2){//TODO: Ha a queueban kettőnél több kép van akkor kivenni majd Bitmappá alakítani és elvégezni az összehasonlítást}
                //first = queue.take();
                //secondbitmap = convertImageToBitmap(second);
                //second.close();
                firstbitmap = queue.take();
                //firstbitmap = convertImageToBitmap(first);
                starttime = System.currentTimeMillis();
                //Log.i("homesecurity" ,"Elindult, a start time: " + starttime);
                //compareTheDifferenceBetweenBitmaps(firstbitmap,secondbitmap);
                double percent = getDifferenceInPercent(firstbitmap, secondbitmap);
                if(percent > 10) {
                    callback.playRingtone();
                    File uploadFile = persistImage(firstbitmap, "betoromegtalalva");
                    //new RetrofitUploadImpl(uploadFile);  //TODO: már elérhető az Azure de kredit spórolás céljából ne töltse fel a képeket.
                    //asyncUploadImage(firstbitmap);  // .jpg vagy .bmp formatum kellene
                    //SignalServer server = new SignalServer();
                    //SignalClient client = new SignalClient();
                    new Thread( new SignalClient()).start();
                    //callback.sendRingtoneSignal();
                }
                Log.i("homesecurity", "Ekkora volt a két bitmap közötti eltérés %-ban: "+ percent);
                difference = System.currentTimeMillis() - starttime;
                Log.i("homesecurity","Megtörtént az összehasonlítás, ennyi idő volt: " + difference + "ms");
                //second = first;
                secondbitmap = firstbitmap;
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private Bitmap convertImageToBitmap(Image image){
        Bitmap bitmap = null;
        if(image != null){
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);
            //return bitmap;
        }
        return bitmap;
    }

    private void compareTheDifferenceBetweenBitmaps(Bitmap firstbitmap, Bitmap secondbitmap){
        if(firstbitmap!=null && secondbitmap!=null && firstbitmap.getWidth() == secondbitmap.getWidth() && firstbitmap.getHeight() == secondbitmap.getHeight()){
            int fsumA = 0;int fsumR = 0; int fsumG= 0; int fsumB=0;
            int ssumA = 0;int ssumR = 0; int ssumG= 0; int ssumB=0;
            for(int height = 0;height < firstbitmap.getHeight(); height++){
                for (int width = 0; width < firstbitmap.getWidth(); width++){
                    int firstcolor = firstbitmap.getPixel(width,height);
                    //int A = (firstcolor >> 24) & 0xff; fsumA+=A; // or color >>> 24
                    int R = (firstcolor >> 16) & 0xff; fsumR+=R;
                    int G = (firstcolor >>  8) & 0xff; fsumG+=G;
                    int B = (firstcolor      ) & 0xff; fsumB+=B;

                    int secondcolor = secondbitmap.getPixel(width,height);
                    //int a = (secondcolor >> 24) & 0xff; ssumA+=a; // or color >>> 24
                    int r = (secondcolor >> 16) & 0xff; ssumR+=r;
                    int g = (secondcolor >>  8) & 0xff; ssumG+=g;
                    int b = (secondcolor      ) & 0xff; ssumB+=b;


                    if( width == firstbitmap.getWidth()-1 && (height+1) % 20 == 0 && height+1 != firstbitmap.getHeight()){
                        if(isDifferent(fsumR,fsumG,fsumB,ssumR,ssumG,ssumB)){
                            callback.playRingtone();
                            return;
                            //sendEmailNotification();
                        }
                        height++;
                        width = 0;
                    }
                    else if( (width+1) % 20 == 0 && (height+1) % 20  == 0){
                        if(isDifferent(fsumR,fsumG,fsumB,ssumR,ssumG,ssumB)){
                            callback.playRingtone();
                            return;
                            //sendEmailNotification();
                        }
                        width++;
                        height = height - 19;
                    }
                    else if( (width+1) % 20 == 0){
                        height++;
                        width = width - 19;
                    }
                }
            }
        }
    }

    private boolean isDifferent(int firstRed, int firstGreen, int firstBlue, int secondRed, int secondGreen, int secondBlue){
        double firstAverageR = firstRed/400; double firstAverageG = firstGreen/400; double firstAverageB = firstBlue/400;
        double secondAverageR = secondRed/400; double secondAverageG = secondGreen/400; double secondAverageB = secondBlue/400;
        return Math.abs((firstAverageR/secondAverageR) - 1) > 0.30 ||
                Math.abs((firstAverageG/secondAverageG) - 1) > 0.30 ||
                Math.abs((firstAverageB/secondAverageB) - 1) > 0.30;
    }

    private double getDifferenceInPercent(Bitmap bmp1, Bitmap bmp2){
        if(bmp1!=null && bmp2!=null && bmp1.getWidth() == bmp2.getWidth() && bmp1.getHeight() == bmp2.getHeight()){
            int height = bmp1.getHeight(); int width = bmp1.getWidth();
            long diff = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    diff += pixelDiff(bmp1.getPixel(x,y), bmp2.getPixel(x, y));
                }
            }
            long maxDiff = 3L * 255 * width * height;

            return 100.0 * diff / maxDiff;
        }

        return 0;
    }

    private int pixelDiff(int rgb1 , int rgb2){
        int r1 = (rgb1 >> 16) & 0xff;
        int g1 = (rgb1 >>  8) & 0xff;
        int b1 =  rgb1        & 0xff;
        int r2 = (rgb2 >> 16) & 0xff;
        int g2 = (rgb2 >>  8) & 0xff;
        int b2 =  rgb2        & 0xff;
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }



    private File persistImage(Bitmap bitmap, String name) {
        File filesDir = a.getBaseContext().getFilesDir();
        File imageFile = new File(filesDir, name + ".jpg");

        OutputStream os;
        try {
            os = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.flush();
            os.close();
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Error writing bitmap", e);
        }
        return imageFile;
    }

    public IRingtoneCallback callback;

    public interface IRingtoneCallback{
        void playRingtone();
        void sendRingtoneSignal();
    }
}