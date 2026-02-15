package scythe.interactions;

import java.util.ArrayList;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.adventure.farming.FarmingUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

public class RadialHarvest extends SimpleInstantInteraction {

   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

   @Nonnull
   public static final BuilderCodec<RadialHarvest> CODEC = BuilderCodec.builder(
         RadialHarvest.class, RadialHarvest::new
      )
      .append(new KeyedCodec<>("Reach",  Codec.DOUBLE), (scythe, reach) -> scythe.reach = reach, scythe -> scythe.reach).add()
      .build();
   public Double reach;



   public RadialHarvest(@Nonnull String id) {
      super(id);
   }

   protected RadialHarvest() {
   }

   @Nonnull
   @Override
   public WaitForDataFrom getWaitForDataFrom() {
      return WaitForDataFrom.Client;
   }

   @Override
   protected void firstRun(
      @Nonnull InteractionType type, @Nonnull InteractionContext ctx, @Nonnull CooldownHandler cooldownHandler
   ) {

      
      var player = ctx.getEntity();
      
      var commandBuffer = ctx.getCommandBuffer();
      
      assert commandBuffer != null;
      
      var world = commandBuffer.getExternalData().getWorld();

      var store = commandBuffer.getStore();

      var transform = store.getComponent(player, TransformComponent.getComponentType());
      var position = transform.getPosition();
      var headRotation = store.getComponent(player, HeadRotation.getComponentType());
      var blocksToHarvest = new ArrayList<Vector3i>();

      // Cylinder parameters
      var reach = this.reach.doubleValue();
      var reachSq = reach * reach;
      var fov = 150.0;
      var minDot = Math.cos(Math.toRadians(fov / 2.0));

      var yawRad = headRotation.getRotation().getYaw();
      var lookX = -Math.sin(yawRad); 
      var lookZ = -Math.cos(yawRad);

      var minX = (int) Math.floor(position.x - reach);
      var maxX = (int) Math.ceil(position.x + reach);
      var minZ = (int) Math.floor(position.z - reach);
      var maxZ = (int) Math.ceil(position.z + reach);
      var midY = (int) Math.floor(position.y);

      // 1. Spatial Filter: Bounding Box
      for (var x = minX; x <= maxX; x++) {
         for (var z = minZ; z <= maxZ; z++) {
            var blockCenterX = x + 0.5;
            var blockCenterZ = z + 0.5;
            
            var dx = blockCenterX - position.x;
            var dz = blockCenterZ - position.z;
            var distSq = dx * dx + dz * dz;

            // 2. Distance Filter (Cylinder Radius)
            if (distSq > reachSq) continue;

            // 3. Angle Filter (Cone/Slice)
            var dist = Math.sqrt(distSq);
            if (dist > 1e-4) {
               // Dot product to find angle cosine
               var dot = (lookX * dx + lookZ * dz) / dist;
               if (dot < minDot) continue; // Outside FOV
            }

            // Add blocks within vertical reach (e.g., feet and head level)
            blocksToHarvest.add(new Vector3i(x, midY - 1, z));
            blocksToHarvest.add(new Vector3i(x, midY, z));
            blocksToHarvest.add(new Vector3i(x, midY + 1, z));
         }
      }

      var crop_harvested_amount = 0.0f;

      for (var targetBlock : blocksToHarvest) {
         var chunkStore = world.getChunkStore();
         var chunkIndex = ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z);
         var chunkRef = chunkStore.getChunkReference(chunkIndex);
         
         if (chunkRef == null || !chunkRef.isValid()) continue;
         var blockChunk = chunkStore.getStore().getComponent(chunkRef, BlockChunk.getComponentType());
         if (blockChunk == null) continue;

         var blockSection = blockChunk.getSectionAtBlockY(targetBlock.y);
         if (blockSection == null) continue;
         var worldChunkComponent = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());
         if (worldChunkComponent == null) continue;
         var blockType = worldChunkComponent.getBlockType(targetBlock);
         if (blockType == null) continue;
         var farmData = blockType.getFarming();
         if (farmData == null) continue;
         
         var below = new Vector3i(targetBlock.x, targetBlock.y - 1, targetBlock.z);
         var blockBelow = worldChunkComponent.getBlockType(below);
         if (blockBelow != null && blockType.getId().equals(blockBelow.getId())) continue;

         // LOGGER.atInfo().log(blockType.toString());
         if (blockType.getGathering().getHarvest() == null) continue;
         var rotationIndex = blockSection.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
         FarmingUtil.harvest(world, commandBuffer, player, blockType, rotationIndex, targetBlock);
         crop_harvested_amount++;
      }

      // Decrease item durability for each crop harvested
      var item = ctx.getHeldItem();
      var container = ctx.getHeldItemContainer();
      if (item != null && container != null && crop_harvested_amount > 0 && item.getMaxDurability() > 0) {
         // Reduce durability by 0.01 per crop
         var newDurability = item.getDurability() - (crop_harvested_amount * 0.01);
         if (newDurability < 0) newDurability = 0;
         
         container.setItemStackForSlot((short) ctx.getHeldItemSlot(), item.withDurability(newDurability));
      }

      // Increase signature energy for each crop harvested
      var signatureEnergyIndex = DefaultEntityStatTypes.getSignatureEnergy();
		var statMap = store.getComponent(player, EntityStatMap.getComponentType());

		statMap.addStatValue(signatureEnergyIndex, crop_harvested_amount);
		statMap.update();
      
   }


   @Nonnull
   @Override
   protected com.hypixel.hytale.protocol.Interaction generatePacket() {
      return new com.hypixel.hytale.protocol.SimpleBlockInteraction();
   }

   @Override
   protected void configurePacket(com.hypixel.hytale.protocol.Interaction packet) {
      super.configurePacket(packet);
   }

   @Override
   public boolean needsRemoteSync() {
      return true;
   }

   @Nonnull
   @Override
   public String toString() {
      return "RadialHarvest{} " + super.toString();
   }
}
