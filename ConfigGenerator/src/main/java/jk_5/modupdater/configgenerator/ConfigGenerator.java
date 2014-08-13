package jk_5.modupdater.configgenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
public class ConfigGenerator {

    private static BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
    private static final String[] potentialModInfoFiles = {"mcmod.info", "cccmod.info", "neimod.info"};

    public static void main(String[] args) throws IOException {
        File modpackFile = new File("modpack.json");
        File configFile = new File("config.json");
        FileReader fr = new FileReader(configFile);
        JsonObject config = JsonParser.parse(fr).asObject();
        fr.close();
        JsonObject modpack;
        if(modpackFile.exists()){
            fr = new FileReader(modpackFile);
            modpack = JsonParser.parse(fr).asObject();
            fr.close();
        }else{
            modpack = new JsonObject();
        }
        if(modpack.get("mods") == null){
            modpack.set("mods", new JsonArray());
        }
        if(modpack.get("ignore") == null){
            modpack.set("ignore", new JsonArray());
        }
        if(modpack.get("forgeversion") != null) modpack.remove("forgeversion");
        if(modpack.get("mcversion") != null) modpack.remove("mcversion");
        if(modpack.get("libraries") != null) modpack.remove("libraries");
        modpack.add("libraries", config.get("libraries"));
        modpack.add("mcversion", config.get("mcversion"));
        modpack.add("forgeversion", config.get("forgeversion"));

        recursiveModDiscovery(new File("mods"), modpack);
        recursiveModDiscovery(new File("config"), modpack);
        modpack.set("updaterChecksum", HashUtils.hash(new File("updater.jar")));

        Iterator<JsonElement> it = modpack.get("mods").asArray().iterator();
        while(it.hasNext()){
            JsonObject mod = it.next().asObject();
            Iterator<JsonElement> it2 = mod.get("files").asArray().iterator();
            while(it2.hasNext()){
                JsonObject file = it2.next().asObject();
                if(!new File(file.get("path").asString()).isFile()){
                    System.out.println("Cleaning up file " + file.get("path").asString());
                    it2.remove();
                }
            }
            if(mod.get("files").asArray().isEmpty()){
                System.out.println("Cleaning up mod " + mod.get("modid").asString());
                it.remove();
            }
        }

        FileWriter fw = new FileWriter(modpackFile, false);
        fw.append(modpack.toPrettyString());
        fw.flush();
        fw.close();
    }

    private static void recursiveModDiscovery(File base, JsonObject modpack){
        for(File file : base.listFiles()){
            if(file.isFile()){
                discoverFile(file, modpack);
            }else if(file.isDirectory()){
                recursiveModDiscovery(file, modpack);
            }
        }
    }

    private static void discoverFile(File file, JsonObject modpack){
        JsonArray modsArray = modpack.get("mods").asArray();
        for(JsonElement e : modpack.get("ignore").asArray()){
            if(e.asString().equals(file.getPath())){
                return;
            }
        }

        String modid = null;
        for(JsonElement e : modsArray){
            for(JsonElement fe : e.asObject().get("files").asArray()){
                JsonObject fileObject = fe.asObject();
                if(fileObject.get("path").asString().equals(file.getPath())){
                    modid = e.asObject().get("modid").asString();
                    String checksum = fileObject.get("checksum").asString();
                    String fileChecksum = HashUtils.hash(file);
                    if(!checksum.equals(fileChecksum)){
                        fileObject.set("checksum", fileChecksum);
                        System.out.println("Updated " + file.getPath());
                    }
                }
            }
        }
        if(modid == null){
            modid = getModId(file);
            if(modid == null || modid.isEmpty()){
                System.out.println("Not adding file " + file.getName());
                modpack.get("ignore").asArray().add(file.getPath());
            }else{
                System.out.println("File " + file.getName() + " has modid " + modid);
                JsonObject modObject = null;
                for(JsonElement e : modsArray){
                    if(e.asObject().get("modid").asString().equals(modid)){
                        modObject = e.asObject();
                    }
                }
                if(modObject == null){
                    modObject = new JsonObject();
                    modObject.set("modid", modid);
                    modObject.set("files", new JsonArray());
                    modsArray.add(modObject);
                }
                JsonObject fo = new JsonObject();
                fo.set("path", file.getPath());
                fo.set("checksum", HashUtils.hash(file)); //TODO: url
                modObject.get("files").asArray().add(fo);
            }
        }
    }

    private static String attemptReadModidFromEntry(ZipFile zf, String name) throws IOException {
        ZipEntry entry = zf.getEntry(name);
        if(entry != null){
            JsonElement e = JsonParser.parse(zf.getInputStream(entry));
            JsonArray modArray;
            if(e.isObject()){
                modArray = e.asObject().get("modList").asArray();
            }else{
                modArray = e.asArray();
            }
            return modArray.get(0).asObject().get("modid").asString();
            /*if(modArray.size() > 1){
                System.out.println("Mod file " + zf.getName() + " has multiple modids: ");
                int i = 0;
                for(JsonElement el : modArray){
                    System.out.println((i++) + ") " + el.asObject().get("modid").asString());
                }
                System.out.println("Which one should we use? Enter the number");
                return modArray.get(Integer.parseInt(readInput())).asObject().get("modid").asString();
            }else{
                return modArray.get(0).asObject().get("modid").asString();
            }*/
        }
        return null;
    }

    public static String getModId(File modJar){
        if(modJar.getName().endsWith(".tml")){
            return "AS_Ruins";
        }else if(modJar.getName().endsWith(".zip") || modJar.getName().endsWith(".jar")){
            ZipFile zf = null;
            try{
                zf = new ZipFile(modJar);
                String modid = null;
                int i = 0;
                while(modid == null && i < potentialModInfoFiles.length){
                    String reading = potentialModInfoFiles[i++];
                    try{
                        modid = attemptReadModidFromEntry(zf, reading);
                    }catch(JsonParserException e){
                        System.out.println("Mod " + modJar.getName() + " has invalid json syntax in " + reading + ":");
                        System.out.println("  " + e.getMessage());
                    }
                }
                if(modid != null){
                    return modid;
                }
            }catch(Exception e){
                e.printStackTrace();
            }finally{
                if(zf != null){
                    try{
                        zf.close();
                    }catch(IOException e){
                        e.printStackTrace();
                    }
                }
            }
        }else{
            System.out.println("To which modid does file " + modJar.getPath() + " belong?");
            return readInput();
        }
        System.out.println("Was not able to find modid for " + modJar.getPath() + ". Please enter the modid for this jar:");
        return readInput();
    }

    public static String readInput(){
        System.out.print("> ");
        try{
            return inReader.readLine().trim();
        }catch(IOException e){
            return "";
        }
    }
}
