package jk_5.modupdater;

import java.io.File;

import jk_5.modupdater.maven.MavenDependency;
import jk_5.modupdater.maven.MavenResolver;

/**
 * No description given
 *
 * @author jk-5
 */
public class MinecraftDependency extends MavenDependency {

    private final boolean server;

    public MinecraftDependency(String version, boolean server) {
        super(new MavenResolver().addRepository("http://s3.amazonaws.com/Minecraft.Download/versions/"), "net.minecraft", server ? "minecraft_server" : "", version, null);
        this.server = server;
    }

    @Override
    public CharSequence getFolderPath() {
        return new StringBuilder().append(this.version).append('/').append(server ? "minecraft_server." : "").append(this.version).append(".jar");
    }

    @Override
    public File getLibraryLocation(File baseDir) {
        return new File(baseDir, "net/minecraft/" + this.artifact + "/" + this.version + "/" + this.artifact + "-" + this.version + ".jar");
    }
}
