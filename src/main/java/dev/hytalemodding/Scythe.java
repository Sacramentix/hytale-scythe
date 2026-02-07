package dev.hytalemodding;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import dev.hytalemodding.commands.TillAndSowCommand;
import dev.hytalemodding.interactions.RadialHarvest;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import javax.annotation.Nonnull;

public class Scythe extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public Scythe(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Scythe has started registering interactions!");
        this.getCodecRegistry(Interaction.CODEC)
            .register("Radial_Harvest", RadialHarvest.class, RadialHarvest.CODEC);
        this.getCommandRegistry().registerCommand(new TillAndSowCommand());
        
        LOGGER.atInfo().log("Scythe has finished registering interactions!");
    }
}