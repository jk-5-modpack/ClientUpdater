package jk_5.modupdater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import jk_5.modupdater.maven.MavenDependency;

/**
 * No description given
 *
 * @author jk-5
 */
public class UpdatingTweaker implements ITweaker {

    private static final Method addUrl;
    private static File libDir;
    private static byte[] buffer = new byte[1024];

    static {
        try{
            addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void acceptOptions(List<String> args, final File gameDir, File assetsDir, String profile) {
        libDir = new File(gameDir, "libraries");
        Constants.loadVersionInfo();

        final CountDownLatch latch = new CountDownLatch(Constants.libraries.size() + 1); //1 more for the configs
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

        File nativesDir = new File(gameDir, "natives");
        nativesDir.mkdir();
        for(MavenDependency dep : Constants.extract){
            File loc = dep.getLibraryLocation(libDir);
            ZipInputStream zis = null;
            try{
                zis = new ZipInputStream(new FileInputStream(loc));
                ZipEntry entry;
                while((entry = zis.getNextEntry()) != null){
                    if(!entry.getName().startsWith("META-INF")){
                        File dest = new File(loc, entry.getName());
                        dest.getParentFile().mkdirs();
                        FileOutputStream fos = new FileOutputStream(dest);
                        int len;
                        while((len = zis.read(buffer)) > 0){
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                        dest.deleteOnExit();
                    }
                }
            }catch(IOException ignored){

            }finally{
                if(zis != null) try{
                    zis.close();
                }catch(Exception ignored){

                }
            }
        }

        //noinspection unchecked
        List<String> tweakers = (List<String>) Launch.blackboard.get("TweakClasses");
        tweakers.addAll(Constants.tweakers);

        Constants.downloadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                ZipInputStream zis = null;
                try{
                    zis = new ZipInputStream(Constants.getInputStream(Constants.modpackRepo + "data.zip"));
                    ZipEntry entry;
                    while((entry = zis.getNextEntry()) != null){
                        File dest = new File(gameDir, entry.getName());
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

        try{
            latch.await();
        }catch(InterruptedException e){
            //Just try to go on. Shouldn't happen though
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        classLoader.addClassLoaderExclusion("jk_5.modupdater.");
        for(MavenDependency dep : Constants.libraries){
            inject(dep.getLibraryLocation(libDir));
        }
    }

    @Override
    public String getLaunchTarget() {
        return Constants.launchTarget;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    private static void inject(File file){
        try{
            URL url = file.toURI().toURL();
            LaunchClassLoader cl = Launch.classLoader;
            URLClassLoader pcl = (URLClassLoader) LaunchClassLoader.class.getClassLoader();
            addUrl.invoke(pcl, url);
            cl.addURL(url);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}
