package scythe.systems;

import java.util.ArrayList;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.adventure.farming.FarmingUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import scythe.components.HarvestWindComponent;

public class WindHarvestSystem extends EntityTickingSystem<EntityStore> {

    public WindHarvestSystem() {
        super();
    }
    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref != null && ref.isValid()) {
            tickOne(ref, commandBuffer);
        }
    }
    
    public void tickOne(
        @Nonnull Ref<EntityStore> projectileRef, @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        var transform = commandBuffer.getComponent(projectileRef, TransformComponent.getComponentType());
        var windComp = commandBuffer.getComponent(projectileRef, HarvestWindComponent.getComponentType());
        var physics = commandBuffer.getComponent(projectileRef, StandardPhysicsProvider.getComponentType());

        if (transform == null || windComp == null || physics == null) return;
        var world = commandBuffer.getExternalData().getWorld();
        if (world == null) return;

        // Resolve Creator
        var creatorUuid = physics.getCreatorUuid();
        var creatorRef = creatorUuid != null ? commandBuffer.getExternalData().getRefFromUUID(creatorUuid) : null;

        // Even if creator is null (maybe disconnected), we might still strictly require it for harvest attribution?
        // Method signature: FarmingUtil.harvest(World, CommandBuffer, Ref<EntityStore> player, ...)
        if (creatorRef == null || !creatorRef.isValid()) return;

        var position = transform.getPosition();
        var blocksToHarvest = new ArrayList<Vector3i>();

        var reach = windComp.radius;
        var reachSq = reach * reach;
        var height = windComp.height;

        var minX = (int) Math.floor(position.x - reach);
        var maxX = (int) Math.ceil(position.x + reach);
        var minZ = (int) Math.floor(position.z - reach);
        var maxZ = (int) Math.ceil(position.z + reach);
        var midY = (int) Math.floor(position.y);
        var minY = midY - 1; // Start from 1 block below
        var maxY = midY + height;

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

                for (var y = minY; y <= maxY; y++) {
                     blocksToHarvest.add(new Vector3i(x, y, z));
                }
            }
        }

        for (var targetBlock : blocksToHarvest) {
            var chunkStore = world.getChunkStore();
            var chunkIndex = ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z);
            var chunkRef = chunkStore.getChunkReference(chunkIndex);
            
            if (chunkRef == null || !chunkRef.isValid()) continue;
            BlockChunk blockChunk = chunkStore.getStore().getComponent(chunkRef, BlockChunk.getComponentType());
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

            if (blockType.getGathering().getHarvest() == null) continue;
            var rotationIndex = blockSection.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
            
            FarmingUtil.harvest(world, commandBuffer, creatorRef, blockType, rotationIndex, targetBlock);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

}
