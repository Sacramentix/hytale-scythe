package scythe.commands;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class FillEnergy extends AbstractPlayerCommand {

    

    public FillEnergy() {
        super("fillenergy", "Refill signature energy for testing purpose.");
    }

    @Nullable
    @Override
    protected void execute(
         @Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world
    ) {
        
      var signatureEnergyIndex = DefaultEntityStatTypes.getSignatureEnergy();
      var statMap = store.getComponent(ref, EntityStatMap.getComponentType());

      statMap.maximizeStatValue(signatureEnergyIndex);
      statMap.update();

    }

}
