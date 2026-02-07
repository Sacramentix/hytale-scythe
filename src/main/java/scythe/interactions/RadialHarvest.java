package scythe.interactions;

import java.util.ArrayList;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.adventure.farming.FarmingUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

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
      
      Ref<EntityStore> player = ctx.getEntity();

      CommandBuffer<EntityStore> commandBuffer = ctx.getCommandBuffer();

      assert commandBuffer != null;

      World world = commandBuffer.getExternalData().getWorld();

      var transform = commandBuffer.getStore().getComponent(player, TransformComponent.getComponentType());
      var position = transform.getPosition();
      var headRotation = commandBuffer.getStore().getComponent(player, HeadRotation.getComponentType());
      var blocksToHarvest = new ArrayList<Vector3i>();

      // LOGGER.atInfo().log("RadialHarvest | \n x: "+position.x+" y: "+position.y+" z: "+position.z+" \n rotation: yaw: " + headRotation.getRotation().getYaw());
      // LOGGER.atInfo().log("HOT RELOAD BIS!");
      // Cylinder parameters
      double reach = this.reach.doubleValue();
      double reachSq = reach * reach;
      double fov = 150.0;
      double minDot = Math.cos(Math.toRadians(fov / 2.0));

      // Player orientation (Yaw 0 = +Z usually, rotating clockwise or counter-clockwise)
      // Assuming standard mapping: dx = -sin(yaw), dz = cos(yaw)
      double yawRad = headRotation.getRotation().getYaw();
      double lookX = -Math.sin(yawRad); 
      double lookZ = -Math.cos(yawRad);

      int minX = (int) Math.floor(position.x - reach);
      int maxX = (int) Math.ceil(position.x + reach);
      int minZ = (int) Math.floor(position.z - reach);
      int maxZ = (int) Math.ceil(position.z + reach);
      int midY = (int) Math.floor(position.y);

      // 1. Spatial Filter: Bounding Box
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            double blockCenterX = x + 0.5;
            double blockCenterZ = z + 0.5;
            
            double dx = blockCenterX - position.x;
            double dz = blockCenterZ - position.z;
            double distSq = dx * dx + dz * dz;

            // 2. Distance Filter (Cylinder Radius)
            if (distSq > reachSq) continue;

            // 3. Angle Filter (Cone/Slice)
            double dist = Math.sqrt(distSq);
            if (dist > 1e-4) {
               // Dot product to find angle cosine
               double dot = (lookX * dx + lookZ * dz) / dist;
               if (dot < minDot) continue; // Outside FOV
            }

            // Add blocks within vertical reach (e.g., feet and head level)
            blocksToHarvest.add(new Vector3i(x, midY, z));
            blocksToHarvest.add(new Vector3i(x, midY + 1, z));
         }
      }

      
      
      // LOGGER.atInfo().log("RadialHarvest | " + blocksToHarvest);


      for (Vector3i targetBlock : blocksToHarvest) {
         ChunkStore chunkStore = world.getChunkStore();
         long chunkIndex = ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z);
         Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
         
         if (chunkRef == null || !chunkRef.isValid()) continue;
         BlockChunk blockChunk = chunkStore.getStore().getComponent(chunkRef, BlockChunk.getComponentType());
         if (blockChunk == null) continue;

         BlockSection blockSection = blockChunk.getSectionAtBlockY(targetBlock.y);
         if (blockSection == null) continue;
         WorldChunk worldChunkComponent = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());
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
         int rotationIndex = blockSection.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
         FarmingUtil.harvest(world, commandBuffer, player, blockType, rotationIndex, targetBlock);
      }

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
