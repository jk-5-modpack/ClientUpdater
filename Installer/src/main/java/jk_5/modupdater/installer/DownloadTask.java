package jk_5.modupdater.installer;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import jk_5.jsonlibrary.JsonObject;

/**
 * No description given
 *
 * @author jk-5
 */
public class DownloadTask implements Runnable {

    public final String url;
    public final File dest;
    public final JsonObject info;

    public DownloadTask(String url, File dest, JsonObject info) {
        this.url = url;
        this.dest = dest;
        this.info = info;
    }

    @Override
    public void run() {
        try{
            dest.getParentFile().mkdirs();
            URL u = new URL(url);
            System.out.println("Downloading: " + u.toString());
            ReadableByteChannel rbc = Channels.newChannel(u.openStream());
            FileOutputStream fos = new FileOutputStream(dest);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            System.out.println("Downloaded: " + u.toString());
            info.set("checksum", HashUtils.hash(dest));
        }catch(Exception ignored){

        }
    }
}
