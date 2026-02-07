package dev.hytalemodding.commands;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class TillAndSowCommand extends AbstractPlayerCommand {

    

    public TillAndSowCommand() {
        super("tillandsow", "Tills grass and sows wheat in a radius");
    }

    @Nullable
    @Override
    protected void execute(
         @Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world
    ) {

        runTillAndSow(context, playerRef, store, world);

    }

    private static final String GRASS_ID = "Soil_Grass";
    private static final String DIRT_ID = "Soil_Dirt";
    private static final String FARMLAND_ID = "Soil_Dirt_Tilled";
    private static final String WHEAT_ID = "Plant_Crop_Wheat_Block_Eternal";

    private void runTillAndSow(CommandContext context, PlayerRef player, Store<EntityStore> store, World world) {
        var transform = player.getTransform();
        var position = transform.getPosition();

        var x = position.x;
        var y = position.y;
        var z = position.z;

        var radius = 25;
        var heightBelow = 3;
        var heightAbove = 3;
        var radiusSq = radius * radius;

        var minX = (int) Math.floor(x - radius);
        var maxX = (int) Math.ceil( x + radius);
        var minZ = (int) Math.floor(z - radius);
        var maxZ = (int) Math.ceil( z + radius);
        var minY = (int) Math.floor(y - heightBelow);
        var maxY = (int) Math.floor(y + heightAbove);

        context.sendMessage(Message.raw("Tilling and sowing in radius " + radius));
        
        var assetMap        = BlockType.getAssetMap();
        var farmlandType    = assetMap.getAsset(FARMLAND_ID);
        var wheatType       = assetMap.getAsset(WHEAT_ID);
        
        var farmlandIntId   = assetMap.getIndex(FARMLAND_ID);
        var wheatIntId      = assetMap.getIndex(WHEAT_ID);

        for (var xx = minX; xx <= maxX; xx++) {
            for (var zz = minZ; zz <= maxZ; zz++) {
                var blockCenterX = xx + 0.5;
                var blockCenterZ = zz + 0.5;
                var dx = blockCenterX - position.x;
                var dz = blockCenterZ - position.z;
                var distSq = dx * dx + dz * dz;

                if (distSq > radiusSq) continue;

                for (int yy = maxY; yy >= minY; yy--) {
                    processBlock(world, xx, yy, zz, farmlandType, farmlandIntId, wheatType, wheatIntId, context);
                }
            }
        }
        
        context.sendMessage(Message.raw("Finished processing."));
    }

    private void processBlock(World world, int x, int y, int z, BlockType farmlandType, int farmlandIntId, BlockType wheatType, int wheatIntId, CommandContext ctx) {
        ChunkStore chunkStore = world.getChunkStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);

        if (chunkRef == null || !chunkRef.isValid()) return;

        WorldChunk worldChunk = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) return;

        
        BlockType currentBlock = worldChunk.getBlockType(x, y, z);
        if (currentBlock == null) return;
        
        String currentId = currentBlock.getId();
        
        boolean isGrass = currentId.contains("Soil"); 
        boolean isFarmland = currentId.contains("Farmland");
        boolean isDefaultBlock = currentId.contains("Rock_Chalk_Brick_Decorative");

        if (!isGrass && !isFarmland && !isDefaultBlock) return;

        
        BlockType aboveBlock = worldChunk.getBlockType(x, y + 1, z);
        boolean aboveIsAir = aboveBlock == null || aboveBlock.getId().equals("Empty"); 
        boolean aboveIsCrop = aboveBlock != null && aboveBlock.getId().contains("Plant_Crop");

        
        if (!aboveIsAir && !aboveIsCrop && aboveBlock != null) return;

        
        if (!isFarmland) {
            worldChunk.setBlock(x, y, z, farmlandIntId, farmlandType, 0, 0, 0); 
        }

        // Complex code copied from ChangeFarmingStage interaction to grow planted crop

        // region: ChangeFarmingStage

        worldChunk.setBlock(x, y+1, z, wheatIntId, wheatType, 0, 0, 0);
        var farm = wheatType.getFarming().getStages();
        LOGGER.atInfo().log(farm.toString());
        BlockComponentChunk blockComponentChunk = chunkStore.getStore().getComponent(chunkRef, BlockComponentChunk.getComponentType());
        int blockIndexColumn = ChunkUtil.indexBlockInColumn(x, y+1, z);
        Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(blockIndexColumn);
        if (blockRef == null) {
            LOGGER.atInfo().log("[ChangeFarmingStage] Creating new block entity (harvest0 pattern)");
            var blockEntity = ChunkStore.REGISTRY.newHolder();
            blockEntity.putComponent(BlockModule.BlockStateInfo.getComponentType(), new BlockModule.BlockStateInfo(blockIndexColumn, chunkRef));
            // blockEntity.addComponent(FarmingBlock.getComponentType(), farmingBlock);
            blockRef = chunkStore.getStore().addEntity(blockEntity, AddReason.SPAWN);
        }
        var farmingBlock = chunkStore.getStore().getComponent(blockRef, FarmingBlock.getComponentType());
        var worldTimeResource = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        var now = worldTimeResource.getGameTime();
        farmingBlock.setCurrentStageSet("Harvested");
        farmingBlock.setGrowthProgress(4);
        farmingBlock.setExecutions(0);
        farmingBlock.setGeneration(farmingBlock.getGeneration() + 1);
        farmingBlock.setLastTickGameTime(now);
        worldChunk.setTicking(x, y+1, z, true);
        Ref<ChunkStore> sectionRef = world.getChunkStore()
            .getChunkSectionReference(ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(y), ChunkUtil.chunkCoordinate(z));
        if (sectionRef != null) {
            BlockSection section = chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
            if (section != null) {
                section.scheduleTick(ChunkUtil.indexBlock(x, y+1, z), now);
            }
            farm.get("Harvested")[3].apply(chunkStore.getStore(), sectionRef, blockRef, x, y+1, z, farm.get("Harvested")[2]);
            // stages[stageIndex].apply(chunkStore, sectionRef, blockRef, x, y, z, previousStage);
            // LOGGER.atInfo().log("[ChangeFarmingStage] Applied stage %d via stages[%d].apply()", stageIndex, stageIndex);
        } else {
            LOGGER.atWarning().log("[ChangeFarmingStage] sectionRef was null - could not apply stage!");
        }
        worldChunk.markNeedsSaving();

    }
}
