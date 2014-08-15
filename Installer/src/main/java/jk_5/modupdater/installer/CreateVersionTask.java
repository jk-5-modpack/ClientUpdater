package jk_5.modupdater.installer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jk_5.jsonlibrary.JsonArray;
import jk_5.jsonlibrary.JsonElement;
import jk_5.jsonlibrary.JsonObject;
import jk_5.jsonlibrary.JsonParser;

/**
 * No description given
 *
 * @author jk-5
 */
public class CreateVersionTask implements Runnable {

    private final File mcDir;
    private final String mcVersion;
    private final String forgeVersion;

    public CreateVersionTask(File mcDir, String mcVersion, String forgeVersion) {
        this.mcDir = mcDir;
        this.mcVersion = mcVersion;
        this.forgeVersion = forgeVersion;
    }

    //TODO: remove realms and forge!

    @Override
    public void run() {
        File dest = new File(new File(mcDir, "versions"), "jk-5-modpackI1");
        dest.mkdir();
        File versionFile = new File(dest, "jk-5-modpackI1.json");
        JsonObject version = new JsonObject();
        JsonArray libs = new JsonArray();
        version.add("id", "jk-5-modpackI1");
        version.add("time", "2014-01-01T00:00:00+0100");
        version.add("type", "release");
        version.add("inheritsFrom", mcVersion);
        version.add("jar", mcVersion);
        version.add("minecraftArguments", "--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userProperties ${user_properties} --userType ${user_type} --tweakClass jk_5.modupdater.updater.UpdatingTweaker");
        version.add("mainClass", "net.minecraft.launchwrapper.Launch");
        version.add("minimumLauncherVersion", "16");
        version.add("libraries", libs);

        libs.add(new JsonObject().add("name", "jk_5.modpack:Updater:1.0.0-SNAPSHOT"));

        JsonArray mcLibs = Installer.loadUrl(Installer.minecraftVersionInfoUrl.replace("{VERSION}", mcVersion)).get("libraries").asArray();
        JsonArray forgeLibs = loadForgeVersionData(mcVersion + "-" + forgeVersion).get("libraries").asArray();

        for(JsonElement e : forgeLibs){
            String name = e.asObject().get("name").asString();
            if(name.startsWith("com.mojang:realms")) continue; //Don't add realms. Forge has an outdated version
            if(name.startsWith("net.minecraftforge:forge")) continue; //Don't add forge. We'll add it to the classpath later on
            boolean add = true;
            for(JsonElement l : mcLibs){
                if(l.asObject().get("name").asString().equals(name)){
                    //Don't add it to the list. Vanilla has it
                    add = false;
                }
            }
            if(add){
                if(e.asObject().get("url") != null && e.asObject().get("url").asString().equals("http://files.minecraftforge.net/maven/")){
                    e.asObject().set("url", "http://repo1.maven.org/maven2/"); //Forge only hosts packed versions...
                }
                libs.add(e);
            }
        }

        FileWriter fw = null;
        try{
            fw = new FileWriter(versionFile, false);
            fw.append(version.toPrettyString());
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

    public static JsonObject loadForgeVersionData(String version){
        InputStream is = null;
        try{
            is = Installer.getInputStream(Installer.forgeInstallerUrl.replace("{VERSION}", version));
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            while((entry = zis.getNextEntry()) != null){
                if(entry.getName().equals("install_profile.json")){
                    return JsonParser.parse(new InputStreamReader(zis)).asObject().get("versionInfo").asObject();
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
}
