package jk_5.modupdater.updater;

import java.io.File;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/**
 * No description given
 *
 * @author jk-5
 */
public class UpdatingTweaker implements ITweaker {

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");
        tweakClasses.add("cpw.mods.fml.common.launcher.FMLTweaker"); //TODO
        Updater.run();
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {

    }

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
