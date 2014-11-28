package jk_5.modupdater.updater;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import jk_5.jsonlibrary.JsonArray;
import jk_5.jsonlibrary.JsonElement;
import jk_5.jsonlibrary.JsonObject;
import jk_5.jsonlibrary.JsonParser;
import jk_5.jsonlibrary.JsonParserException;

/**
 * No description given
 *
 * @author jk-5
 */
public class Updater {

    private static final String modpackRepo = "https://raw.githubusercontent.com/jk-5-modpack/Modpack/master/";
    private static final String modpackInfoFile = modpackRepo + "modpack.json";
    public static final String forgeUniversalUrl = "http://files.minecraftforge.net/maven/net/minecraftforge/forge/{VERSION}/forge-{VERSION}-universal.jar";
    private static final File mcDir;
    private static final File profileDir;
    private static final File libsDir;
    private static final File localInfoFile;
    private static final String os;
    private static final Executor downloader = Executors.newFixedThreadPool(8, new ThreadFactory() {
        private final AtomicInteger nextId = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("Downloader #" + nextId.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });
    private static final Set<Runnable> taskQueue = new HashSet<Runnable>();
    private static final LaunchClassLoader classLoader = Launch.classLoader;
    private static final URLClassLoader parentLoader;
    private static final Method addUrlMethod;
    private static final Set<String> validPaths = new HashSet<String>();
    private static final Logger logger = LogManager.getLogger();

    public static void run(){
        logger.info("Checking for updates...");
        JsonObject remoteModpackInfo = null;
        JsonObject localModpackInfo;
        profileDir.mkdir();

        try{
            remoteModpackInfo = loadUrl(modpackInfoFile);
        }catch(JsonParserException e){
            e.printStackTrace();
            System.exit(0);
        }catch(RuntimeException e){
            e.printStackTrace();
            return;
        }

        if(localInfoFile.isFile()){
            FileReader fr = null;
            try{
                fr = new FileReader(localInfoFile);
                localModpackInfo = JsonParser.parse(fr).asObject();
                fr.close();
            }catch(Exception e){
                logger.error("Error while reading local modpack.json", e);
                logger.info("This is a non-fatal error. Resetting all data in modpack.json");
                localModpackInfo = new JsonObject();
            }finally{
                if(fr != null){
                    try{
                        fr.close();
                    }catch(IOException e){
                        e.printStackTrace();
                    }
                }
            }
        }else{
            localModpackInfo = new JsonObject();
        }

        if(localModpackInfo.get("mods") == null) localModpackInfo.add("mods", new JsonArray());
        if(localModpackInfo.get("libraries") == null) localModpackInfo.add("libraries", new JsonArray());
        if(localModpackInfo.get("ignore") == null) localModpackInfo.add("ignore", new JsonArray());

        for(JsonElement mod : remoteModpackInfo.get("mods").asArray()){
            JsonObject modObj = new JsonObject();
            JsonArray filesArray = new JsonArray();
            modObj.add("modid", mod.asObject().get("modid").asString());
            modObj.add("files", filesArray);
            localModpackInfo.get("mods").asArray().add(modObj);
            for(JsonElement e : mod.asObject().get("files").asArray()){
                JsonObject file = e.asObject();
                JsonObject info = new JsonObject();
                filesArray.add(info);
                info.set("path", file.get("path").asString());
                File path = new File(profileDir, file.get("path").asString());
                validPaths.add(path.getPath());
                if(!path.exists() || !HashUtils.hash(path).equals(file.get("checksum").asString())){
                    if(path.exists()) path.delete();
                    taskQueue.add(new DownloadTask(modpackRepo + file.get("path").asString(), path, info));
                }
            }
        }

        for(JsonElement ignored : remoteModpackInfo.get("ignore").asArray()){
            validPaths.add(new File(ignored.asString()).getPath());
        }

        recursiveFileDeletion(new File(profileDir, "mods"));
        recursiveFileDeletion(new File(profileDir, "config"));
        recursiveFileDeletion(new File(profileDir, "Flan"));

        String mcVersion = remoteModpackInfo.get("mcversion").asString();
        String forgeVersion = remoteModpackInfo.get("forgeversion").asString();

        JsonObject forgeObj = new JsonObject().add("path", "forge");
        File forgeDest = new File(libsDir, "forge-" + mcVersion + "-" + forgeVersion + ".jar");
        localModpackInfo.get("libraries").asArray().add(new JsonObject().add("path", forgeDest.getPath()));
        if(!forgeDest.exists() || !remoteModpackInfo.get("forgeversion").asString().equals(localModpackInfo.get("forgeversion").asString())){
            File oldDest = new File(libsDir, "forge-" + localModpackInfo.get("mcversion").asString() + "-" + localModpackInfo.get("forgeversion").asString() + ".jar");
            oldDest.delete();
            taskQueue.add(new DownloadTask(forgeUniversalUrl.replace("{VERSION}", mcVersion + "-" + forgeVersion), forgeDest, forgeObj));

            localModpackInfo.set("forgeversion", forgeVersion);
            localModpackInfo.set("mcversion", mcVersion);
        }

        File updater = new File(mcDir, "libraries/jk_5/modpack/Updater/1.0.0-SNAPSHOT/Updater-1.0.0-SNAPSHOT.jar");
        updater.getParentFile().mkdirs();
        if(!updater.exists() || !HashUtils.hash(updater).equals(remoteModpackInfo.get("updaterChecksum").asString())){
            updater.delete();
            final JsonObject o = new JsonObject();
            final JsonObject finalLocalModpackInfo = localModpackInfo;
            taskQueue.add(new DownloadTask(modpackRepo + "updater.jar", updater, o){
                @Override
                public void run() {
                    super.run();
                    finalLocalModpackInfo.set("updaterChecksum", o.get("checksum").asString());
                }
            });
        }

        final CountDownLatch latch = new CountDownLatch(taskQueue.size());
        for(final Runnable task : taskQueue){
            downloader.execute(new Runnable() {
                @Override
                public void run() {
                    task.run();
                    latch.countDown();
                }
            });
        }

        try{
            latch.await();
        }catch(InterruptedException e){
            e.printStackTrace();
        }

        inject(forgeDest);

        FileWriter fw = null;
        try{
            fw = new FileWriter(localInfoFile, false);
            fw.append(localModpackInfo.toPrettyString());
            fw.flush();
        }catch(IOException e){
            e.printStackTrace();
        }finally{
            if(fw != null){
                try{
                    fw.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }
    }

    private static void recursiveFileDeletion(File dir) {
        for(File file : dir.listFiles()){
            if(file.isFile() && !validPaths.contains(file.getPath())){
                logger.info("Removing invalid file " + file.getPath());
                file.delete();
            }
        }
    }

    public static JsonObject loadUrl(String url){
        InputStream is = null;
        try{
            is = getInputStream(url);
            return JsonParser.parse(new InputStreamReader(is)).asObject();
        }catch(JsonParserException e){
            throw e;
        }catch(Exception e){
            throw new RuntimeException("Exception while trying to load " + url, e);
        }finally{
            if(is != null){
                try{
                    is.close();
                }catch(Exception ignored){
                    //Meh
                }
            }
        }
    }

    public static InputStream getInputStream(String url){
        try{
            URL u = new URL(url);
            URLConnection conn = u.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            return conn.getInputStream();
        }catch(Exception e){
            new RuntimeException("Exception while trying to load " + url, e).printStackTrace();
            System.exit(1);
            return null;
        }
    }

    static {
        String userHome = System.getProperty("user.home", ".");
        String osType = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if(osType.contains("win") && System.getenv("APPDATA") != null){
            mcDir = new File(System.getenv("APPDATA"), ".minecraft");
            os = "windows";
        }else if(osType.contains("mac")){
            mcDir = new File(new File(new File(userHome, "Library"), "Application Support"), "minecraft");
            os = "osx";
        }else{
            mcDir = new File(userHome, ".minecraft");
            os = "linux";
        }
        profileDir = new File(new File(new File(mcDir, "profiles"), "jk-5-modpack"), "v1");
        localInfoFile = new File(profileDir, "modpack.json");
        libsDir = new File(profileDir, "libraries");
        parentLoader = (URLClassLoader) classLoader.getClass().getClassLoader();
        try{
            addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrlMethod.setAccessible(true);
        }catch(NoSuchMethodException e){
            throw new RuntimeException(e);
        }
    }

    public static void inject(File file){
        try{
            URL u = file.toURI().toURL();
            classLoader.addURL(u);
            addUrlMethod.invoke(parentLoader, u);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
