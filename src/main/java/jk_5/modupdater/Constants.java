package jk_5.modupdater;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jk_5.modupdater.maven.MavenDependency;
import jk_5.modupdater.maven.MavenResolver;
import jk_5.modupdater.shaded.com.eclipsesource.json.JsonArray;
import jk_5.modupdater.shaded.com.eclipsesource.json.JsonObject;
import jk_5.modupdater.shaded.com.eclipsesource.json.JsonValue;

/**
 * No description given
 *
 * @author jk-5
 */
public final class Constants {

    private Constants(){
        //Singleton
    }

    public static final File mcDir;
    public static final String modpackRepo = "https://raw.githubusercontent.com/jk-5-modpack/Modpack/master/";
    public static final String modpackInfoFile = modpackRepo + "modpack.json";
    public static final String minecraftVersionInfoUrl = "https://s3.amazonaws.com/Minecraft.Download/versions/{VERSION}/{VERSION}.json";
    public static final String forgeInstallerUrl = "http://files.minecraftforge.net/maven/net/minecraftforge/forge/{VERSION}/forge-{VERSION}-installer.jar";

    public static Set<MavenDependency> libraries = new HashSet<MavenDependency>();
    public static Set<MavenDependency> extract = new HashSet<MavenDependency>();
    public static List<String> tweakers = new ArrayList<String>();

    public static final MavenResolver resolver = new MavenResolver().addMavenCentral().addRepository("https://libraries.minecraft.net/");

    public static final String arch = System.getProperty("sun.arch.data.model");
    public static final String os;

    public static String launchTarget;

    public static final Executor downloadExecutor = Executors.newFixedThreadPool(16, new ThreadFactory() {
        private final AtomicInteger threadId = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("DownloadThread #" + threadId.getAndIncrement());
            return t;
        }
    });

    public static final boolean isServer = System.getProperty("server", "false").equalsIgnoreCase("true");

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
    }

    public static void loadVersionInfo() {
        JsonObject modpackData = loadUrl(modpackInfoFile);
        String mcversion = modpackData.get("mcversion").asString();
        String forgeversion = mcversion + "-" + modpackData.get("forge").asString();
        JsonObject mcdata = loadUrl(minecraftVersionInfoUrl.replace("{VERSION}", mcversion));
        JsonObject forgeData = loadForgeVersionData(forgeversion).get("versionInfo").asObject();

        if(isServer){
            launchTarget = modpackData.get("launchTarget").asObject().get("server").asString();
        }else{
            launchTarget = modpackData.get("launchTarget").asObject().get("client").asString();
        }

        for(JsonValue v : modpackData.get("tweakers").asArray()){
            if(v.isObject()){
                JsonObject o = v.asObject();
                if(isServer){
                    tweakers.add(o.get("server").asString());
                }else{
                    tweakers.add(o.get("client").asString());
                }
            }else{
                tweakers.add(v.asString());
            }
        }

        libraries.add(new MinecraftDependency(modpackData.get("mcversion").asString(), isServer));
        libraries.add(resolver.dependency("net.minecraftforge:forge:" + forgeversion + ":universal"));

        for(JsonValue v : modpackData.get("repositories").asArray()){
            resolver.addRepository(v.asString());
        }

        System.out.println("Parsing minecraft libraries");
        for(JsonValue v : mcdata.get("libraries").asArray()){
            JsonObject o = v.asObject();
            if(o.get("url") != null){
                resolver.addRepository(o.get("url").asString());
            }
            if(o.get("rules") != null){
                Set<String> allowed = new HashSet<String>();
                JsonArray rules = o.get("rules").asArray();
                for(JsonValue v1 : rules){
                    JsonObject ob = v1.asObject();
                    if(ob.get("os") == null){
                        if(ob.get("action").asString().equals("allow")){
                            allowed.add("osx");
                            allowed.add("windows");
                            allowed.add("linux");
                        }else if(ob.get("action").asString().equals("disallow")){
                            allowed.clear();
                        }
                    }else{
                        String os = ob.get("os").asObject().get("name").asString();
                        if(ob.get("action").asString().equals("allow")){
                            allowed.add(os);
                        }else if(ob.get("action").asString().equals("disallow")){
                            allowed.remove(os);
                        }
                    }
                }
                boolean isAllowed = false;
                if(!allowed.isEmpty()){
                    isAllowed = Constants.os.equals(allowed.iterator().next());
                }
                if(!isAllowed) continue;
            }
            MavenDependency dep;
            if(o.get("natives") != null){
                String nativeName = o.get("natives").asObject().get(os).asString().replace("${arch}", arch);
                libraries.add(dep = resolver.dependency(o.get("name").asString() + ":" + nativeName));
            }else{
                libraries.add(dep = resolver.dependency(o.get("name").asString()));
            }
            if(o.get("extract") != null){
                extract.add(dep);
            }
        }

        System.out.println("Parsing forge libraries");
        for(JsonValue v : forgeData.get("libraries").asArray()){
            JsonObject o = v.asObject();
            if(o.get("url") != null){
                resolver.addRepository(o.get("url").asString());
            }
            if(o.get("rules") != null){
                Set<String> allowed = new HashSet<String>();
                JsonArray rules = o.get("rules").asArray();
                for(JsonValue v1 : rules){
                    JsonObject ob = v1.asObject();
                    if(ob.get("os") == null){
                        if(ob.get("action").asString().equals("allow")){
                            allowed.add("osx");
                            allowed.add("windows");
                            allowed.add("linux");
                        }else if(ob.get("action").asString().equals("disallow")){
                            allowed.clear();
                        }
                    }else{
                        String os = ob.get("os").asObject().get("name").asString();
                        if(ob.get("action").asString().equals("allow")){
                            allowed.add(os);
                        }else if(ob.get("action").asString().equals("disallow")){
                            allowed.remove(os);
                        }
                    }
                }
                boolean isAllowed = false;
                if(!allowed.isEmpty()){
                    isAllowed = Constants.os.equals(allowed.iterator().next());
                }
                if(!isAllowed) continue;
            }
            MavenDependency dep;
            if(o.get("natives") != null){
                String nativeName = o.get("natives").asObject().get(os).asString().replace("${arch}", arch);
                libraries.add(dep = resolver.dependency(o.get("name").asString() + ":" + nativeName));
            }else{
                libraries.add(dep = resolver.dependency(o.get("name").asString()));
            }
            if(o.get("extract") != null){
                extract.add(dep);
            }
            Iterator<MavenDependency> it = libraries.iterator();
            while(it.hasNext()){
                MavenDependency d = it.next();
                if(d.matches(dep) && !d.getVersion().equals(dep.getVersion())){
                    it.remove();
                    System.out.println("Forge overrode minecraft dependency " + d.getGroup() + ":" + d.getArtifact() + " (version " + d.getVersion() + " updated to " + dep.getVersion() + ")");
                }
            }
            libraries.add(dep);
        }

        System.out.println("Parsing modpack libraries");
        for(JsonValue v : modpackData.get("libraries").asArray()){
            JsonObject o = v.asObject();
            MavenDependency dep = resolver.dependency(o.get("name").asString());
            Iterator<MavenDependency> it = libraries.iterator();
            while(it.hasNext()){
                MavenDependency d = it.next();
                if(d.matches(dep) && !d.getVersion().equals(dep.getVersion())){
                    it.remove();
                    System.out.println("Modpack overrode minecraft dependency " + d.getGroup() + ":" + d.getArtifact() + " (version " + d.getVersion() + " updated to " + dep.getVersion() + ")");
                }
            }
            libraries.add(dep);
        }
    }

    public static JsonObject loadForgeVersionData(String version){
        InputStream is = null;
        try{
            is = getInputStream(forgeInstallerUrl.replace("{VERSION}", version));
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            while((entry = zis.getNextEntry()) != null){
                if(entry.getName().equals("install_profile.json")){
                    return JsonObject.readFrom(new InputStreamReader(zis));
                }
            }
            throw new RuntimeException("install_profile.json not found in forge jar");
        }catch(Exception e){
            throw new RuntimeException("Exception while trying to load forge data for version " + version, e);
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

    public static JsonObject loadUrl(String url){
        InputStream is = null;
        try{
            is = getInputStream(url);
            return JsonObject.readFrom(new InputStreamReader(is));
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
}
