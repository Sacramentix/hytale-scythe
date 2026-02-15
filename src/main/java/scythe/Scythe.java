package scythe;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import scythe.commands.FillEnergy;
import scythe.commands.TillAndSowCommand;
import scythe.components.HarvestWindComponent;
import scythe.interactions.LaunchHarvestWind;
import scythe.interactions.RadialHarvest;
import scythe.systems.WindHarvestSystem;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import javax.annotation.Nonnull;

public class Scythe extends JavaPlugin {

    private static Scythe instance;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ComponentType<EntityStore, HarvestWindComponent> harvestWindComponentType;
    public ComponentType<EntityStore, HarvestWindComponent> getHarvestWindComponentType() {
        return harvestWindComponentType;
    };
    

    public static Scythe get() {
        return instance;
    }

    public Scythe(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Scythe has started registering interactions!");
        this.getCodecRegistry(Interaction.CODEC)
            .register("Radial_Harvest", RadialHarvest.class, RadialHarvest.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("Launch_Harvest_Wind", LaunchHarvestWind.class, LaunchHarvestWind.CODEC);
        this.getCommandRegistry().registerCommand(new TillAndSowCommand());
        this.getCommandRegistry().registerCommand(new FillEnergy());
        this.harvestWindComponentType = EntityStore.REGISTRY.registerComponent(HarvestWindComponent.class, "HarvestWind", HarvestWindComponent.CODEC);

        this.getEntityStoreRegistry().registerSystem(new WindHarvestSystem());
        
        LOGGER.atInfo().log("Scythe has finished registering interactions!");
    }
}