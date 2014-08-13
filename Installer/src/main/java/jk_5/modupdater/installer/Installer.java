package jk_5.modupdater.installer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

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
public class Installer {

    private static final boolean server = System.getProperty("server", "false").equalsIgnoreCase("true");
    private static final SwingDelegate delegate = server ? new HeadlessDelegate() : new DefaultDelegate();
    private static final String modpackRepo = "https://raw.githubusercontent.com/jk-5-modpack/Modpack/master/";
    private static final String modpackInfoFile = modpackRepo + "modpack.json";
    public static final String minecraftVersionInfoUrl = "https://s3.amazonaws.com/Minecraft.Download/versions/{VERSION}/{VERSION}.json";
    public static final String forgeInstallerUrl = "http://files.minecraftforge.net/maven/net/minecraftforge/forge/{VERSION}/forge-{VERSION}-installer.jar";
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

    public static void main(String[] args){
        int ret = delegate.showConfirmDialog("The updater will install the required profiles into your launcher. Press OK to continue", "Installer", SwingDelegate.OK_CANCEL_OPTION);
        if(ret == SwingDelegate.CANCEL_OPTION){
            System.exit(0);
        }
        JsonObject remoteModpackInfo = null;
        final JsonObject localModpackInfo = new JsonObject();
        localModpackInfo.add("mods", new JsonArray());
        profileDir.mkdir();

        if(localInfoFile.isFile()) localInfoFile.delete();

        try{
            remoteModpackInfo = loadUrl(modpackInfoFile);
        }catch(JsonParserException e){
            delegate.showMessageDialog("Remote data file has invalid syntax: " + e.getMessage(), "Error", SwingDelegate.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(0);
        }catch(RuntimeException e){
            delegate.showMessageDialog("An error has occurred. Are you connected to the internet? (" + e.getMessage() + ")", "Error", SwingDelegate.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(0);
        }

        File modDir = new File(profileDir, "mods");
        File configDir = new File(profileDir, "config");

        if(modDir.isDirectory()) modDir.delete();
        if(configDir.isDirectory()) configDir.delete();
        if(libsDir.isDirectory()) configDir.delete();

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
                taskQueue.add(new DownloadTask(modpackRepo + file.get("path").asString(), path, info));
            }
        }

        localModpackInfo.add("libraries", new JsonArray());

        String mcVersion = remoteModpackInfo.get("mcversion").asString();
        String forgeVersion = remoteModpackInfo.get("forgeversion").asString();

        localModpackInfo.set("mcversion", mcVersion);
        localModpackInfo.set("forgeversion", forgeVersion);

        JsonObject forgeObj = new JsonObject().add("path", "forge");
        File forgeDest = new File(libsDir, "forge-" + mcVersion + "-" + forgeVersion + ".jar");
        localModpackInfo.get("libraries").asArray().add(new JsonObject().add("path", forgeDest.getPath()));
        taskQueue.add(new CreateVersionTask(mcDir, mcVersion, forgeVersion));
        taskQueue.add(new DownloadTask(forgeUniversalUrl.replace("{VERSION}", mcVersion + "-" + forgeVersion), forgeDest, forgeObj));

        File updater = new File(mcDir, "libraries/jk_5/modpack/Updater/1.0.0-SNAPSHOT/Updater-1.0.0-SNAPSHOT.jar");
        updater.delete();
        updater.getParentFile().mkdirs();
        if(!updater.exists() || !HashUtils.hash(updater).equals(remoteModpackInfo.get("updaterChecksum").asString())){
            final JsonObject o = new JsonObject();
            taskQueue.add(new DownloadTask(modpackRepo + "updater.jar", updater, o){
                @Override
                public void run() {
                    super.run();
                    localModpackInfo.set("updaterChecksum", o.get("checksum").asString());
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

        FileReader fr = null;
        fw = null;
        try{
            fr = new FileReader(new File(mcDir, "launcher_profiles.json"));
            JsonObject profiles = JsonParser.safeParse(fr).asObject();
            JsonObject profile = new JsonObject();
            profile.add("name", "jk-5-modpack V1 (1.7.10)");
            profile.add("gameDir", profileDir.getAbsolutePath());
            profile.add("lastVersionId", "jk-5-modpackI1");
            profile.add("javaArgs", "-Xmx1G -Xms1G -XX:PermSize=256M -XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode -XX:-UseAdaptiveSizePolicy -Xmn128M");
            profile.add("launcherVisibilityOnGameClose", "keep the launcher open");
            profiles.get("profiles").asObject().set("jk-5-modpack V1 (1.7.10)", profile);
            profiles.set("selectedProfile", "jk-5-modpack V1 (1.7.10)");
            fw = new FileWriter(new File(mcDir, "launcher_profiles.json"));
            fw.write(profiles.toPrettyString());
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            if(fr != null){
                try{
                    fr.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
            if(fw != null){
                try{
                    fw.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }
        delegate.showMessageDialog("Modpack profile is installed into your launcher", "Done", SwingDelegate.INFORMATION_MESSAGE);
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
    }
}
