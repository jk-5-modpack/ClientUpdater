package jk_5.modupdater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.*;

import jk_5.modupdater.maven.MavenDependency;

/**
 * No description given
 *
 * @author jk-5
 */
public class Installer {

    private static byte[] buffer = new byte[1024];

    public static void main(String[] args) throws InterruptedException {
        Constants.loadVersionInfo();

        final boolean server = Constants.isServer;
        File dp;
        if(server){
            dp = new File(".");
        }else{
            dp = Constants.mcDir;
            int ret = JOptionPane.showConfirmDialog(null, "The installer will install the required profile in your minecraft launcher. Make sure it is closed!", "Installing", JOptionPane.OK_CANCEL_OPTION);
            if(ret == JOptionPane.CANCEL_OPTION){
                System.exit(0);
                return;
            }
        }
        final File destPath = dp;

        File myJar = null;
        URLClassLoader ucl = (URLClassLoader) Installer.class.getClassLoader();
        for(URL u : ucl.getURLs()){
            if(u.getFile().contains("ModUpdater")){
                myJar = new File(u.getPath());
            }
        }
        if(myJar != null){
            File dest = new File(new File(new File(new File(new File(new File(destPath, "libraries"), "jk_5"), "modupdater"), "ModUpdater"), "1.0.0"), "ModUpdater-1.0.0.jar");
            dest.getParentFile().mkdirs();
            FileInputStream fis = null;
            try{
                fis = new FileInputStream(myJar);
                FileOutputStream fos = new FileOutputStream(dest);
                int len;
                while((len = fis.read(buffer)) > 0){
                    fos.write(buffer, 0, len);
                }
                fos.close();
                fis.close();
            }catch(Exception ignored){

            }finally{
                if(fis != null){
                    try{
                        fis.close();
                    }catch(Exception ignored){

                    }
                }
            }
        }

        final CountDownLatch latch = new CountDownLatch(Constants.libraries.size() + (server ? 2 : 1));
        final File packDir = new File(new File(Constants.mcDir, "profiles"), "jk-5-modpack1");
        final File libDir = server ? new File(destPath, "libraries") : new File(packDir, "libraries");
        for(final MavenDependency dep : Constants.libraries){
            Constants.downloadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    File dest = dep.getLibraryLocation(libDir);
                    if(!dest.isFile()){
                        dep.download(dest);
                    }
                    latch.countDown();
                }
            });
        }

        Constants.downloadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println("Updating mods and configurations...");
                ZipInputStream zis = null;
                try{
                    zis = new ZipInputStream(Constants.getInputStream(Constants.modpackRepo + "data.zip"));
                    ZipEntry entry;
                    System.out.println("Force-updating mods and configuration");
                    if(server){
                        new File(destPath, "config").delete();
                        new File(destPath, "mods").delete();
                    }else{
                        new File(packDir, "config").delete();
                        new File(packDir, "mods").delete();
                    }
                    while((entry = zis.getNextEntry()) != null){
                        File dest = server ? new File(destPath, entry.getName()) : new File(packDir, entry.getName());
                        if(entry.isDirectory()){
                            dest.mkdirs();
                        }else{
                            dest.getParentFile().mkdirs();
                            FileOutputStream fos = new FileOutputStream(dest);
                            int len;
                            while((len = zis.read(buffer)) > 0){
                                fos.write(buffer, 0, len);
                            }
                            fos.close();
                        }
                    }
                }catch(IOException e){
                    e.printStackTrace();
                }finally{
                    if(zis != null){
                        try{
                            zis.close();
                        }catch(Exception ignored){

                        }
                    }
                }
                latch.countDown();
            }
        });

        if(server){
            Constants.downloadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    PrintWriter pw = null;
                    try{
                        pw = new PrintWriter(new File(destPath, "start.sh"));
                        pw.write("java -server -Dserver=true -Djava.awt.headless=true ");
                        pw.write("-Xmx768m -Xms768m -XX:PermSize=256m -XX:+AggressiveOpts -XX:+UseBiasedLocking -XX:+UseFastAccessorMethods -XX:+UseStringCache -XX:+OptimizeStringConcat -Dio.netty.noResourceLeakDetection ");
                        pw.write("-cp libraries/jk_5/modupdater/ModUpdater/1.0.0/ModUpdater-1.0.0.jar");
                        for(MavenDependency dep : Constants.libraries){
                            pw.write(':');
                            pw.write(dep.getLibraryLocation(libDir).getPath());
                        }
                        pw.write(" net.minecraft.launchwrapper.Launch --tweakClass jk_5.modupdater.UpdatingTweaker --nogui");
                        pw.flush();
                        pw.close();
                        pw = null;
                        pw = new PrintWriter(new File(destPath, "eula.txt"));
                        pw.write("eula=true");
                        pw.flush();
                        pw.close();
                        pw = null;
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }finally{
                        if(pw != null) try{
                            pw.close();
                        }catch(Exception ignored){

                        }
                        latch.countDown();
                    }
                }
            });
        }else{
            File profileLocation = new File(new File(Constants.mcDir, "versions"), "jk-5Modpack-Auto-Updater");
            profileLocation.mkdirs();
            InputStream is = Installer.class.getResourceAsStream("/dummy.jar");
            try{
                FileOutputStream fos = new FileOutputStream(new File(profileLocation, "jk-5Modpack-Auto-Updater.jar"));
                int len;
                while((len = is.read(buffer)) > 0){
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }catch(Exception e){
                //meh
            }finally{
                try{
                    is.close();
                }catch(IOException e){
                    //meh
                }
            }
            InputStream is2 = Installer.class.getResourceAsStream("/profile.json");
            try{
                FileOutputStream fos = new FileOutputStream(new File(profileLocation, "jk-5Modpack-Auto-Updater.json"));
                int len;
                while((len = is2.read(buffer)) > 0){
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }catch(Exception e){
                //meh
            }finally{
                try{
                    is2.close();
                }catch(IOException e){
                    //meh
                }
            }
        }

        System.out.println("Waiting until all downloads are done...");
        latch.await();

        if(!server){
            JOptionPane.showMessageDialog(null, "Successfully installed client profile", "Done", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
