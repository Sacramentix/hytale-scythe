package scythe.interactions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.RotationMode;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsConfig;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.asset.type.model.config.camera.CameraSettings;
import com.hypixel.hytale.server.core.asset.type.model.config.camera.CameraAxis;
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues;
import com.hypixel.hytale.protocol.CameraNode;
import com.hypixel.hytale.protocol.ModelTrail;
import com.hypixel.hytale.protocol.Phobia;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.Rangef;
import com.hypixel.hytale.protocol.Direction;

import scythe.components.HarvestWindComponent;

public class LaunchHarvestWind extends SimpleInstantInteraction {
    
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

   @Nonnull
   public static final BuilderCodec<LaunchHarvestWind> CODEC = BuilderCodec.builder(
        LaunchHarvestWind.class, LaunchHarvestWind::new
      )
      .append(new KeyedCodec<>("Reach",  Codec.DOUBLE), (scythe, reach) -> scythe.reach = reach, scythe -> scythe.reach).add()
      .build();
   public Double reach;



   public LaunchHarvestWind(@Nonnull String id) {
      super(id);
   }

   protected LaunchHarvestWind() {
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
        var creator = ctx.getEntity();
        var command_buffer = ctx.getCommandBuffer();

        spawnProjectile(creator, command_buffer);

        var signatureEnergyIndex = DefaultEntityStatTypes.getSignatureEnergy();
		var statMap = command_buffer.getStore().getComponent(creator, EntityStatMap.getComponentType());

		statMap.minimizeStatValue(signatureEnergyIndex);
		statMap.update();
   }

    @Nonnull
    public void spawnProjectile(
        Ref<EntityStore> creatorRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        var ABOVE_GROUND_Y = 0.1;
        var HARVEST_REACH = this.reach;
        var HARVEST_HEIGHT = 3;
        var SPEED = 25;
        var LIFESPAN = Duration.ofSeconds(1L);

        var holder = EntityStore.REGISTRY.newHolder();
        
        var creatorTransform = commandBuffer.getComponent(creatorRef, TransformComponent.getComponentType());
        var creatorHeadRot = commandBuffer.getComponent(creatorRef, HeadRotation.getComponentType());
        
        if (creatorTransform == null || creatorHeadRot == null) return;

        var rotation = new Vector3f();
        var position = creatorTransform.getPosition().clone();
        position.y += ABOVE_GROUND_Y;

        var yaw = creatorHeadRot.getRotation().getYaw();
        var pitch = 0;
        
        var direction = new Vector3d();
        PhysicsMath.vectorFromAngles(yaw, pitch, direction);
        direction.normalize();
        
        rotation.setYaw(yaw);
        rotation.setPitch(pitch);
        
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        var projectile_rotation = new HeadRotation();
        projectile_rotation.setRotation(rotation);
        projectile_rotation.setRotation(rotation);
        holder.addComponent(HeadRotation.getComponentType(), projectile_rotation);
        holder.ensureComponent(Projectile.getComponentType());
        
        var velocity = direction.clone().scale(SPEED);
        holder.addComponent(Velocity.getComponentType(), new Velocity(velocity));
        
        var physicsConfig = new WindPhysicsConfig();
        // Physical hitbox of the projectile, doesn't control the harvest at all
        var box = new Box(-0.5, 0, -0.5, 0.5, 2.0, 0.5); 
        var bbox = new BoundingBox(box);
        holder.addComponent(BoundingBox.getComponentType(), bbox);
        holder.addComponent(HarvestWindComponent.getComponentType(), new HarvestWindComponent(HARVEST_REACH, HARVEST_HEIGHT));
        
        physicsConfig.apply(holder, creatorRef, velocity, commandBuffer, false);
        
        var model = createTornadoModel();
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(commandBuffer.getExternalData().takeNextNetworkId()));
        
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        holder.addComponent(
            DespawnComponent.getComponentType(),
            new DespawnComponent(commandBuffer.getResource(TimeResource.getResourceType()).getNow().plus(LIFESPAN))
        );

        commandBuffer.addEntity(holder, AddReason.SPAWN);
    }
    
    private Model createTornadoModel() {
        CameraAxis pitchCtx = new CameraAxis(new Rangef(0f, 0f), new CameraNode[]{CameraNode.Head});
        CameraAxis yawCtx = new CameraAxis(new Rangef(0f, 0f), new CameraNode[]{CameraNode.Head});
        CameraSettings camera = new CameraSettings(null, yawCtx, pitchCtx);

        // Animation
        ConfiguredAnimation spinAnim = new ConfiguredAnimation(
            null,
            "Items/Projectiles/Tornado_Spin.blockyanim",
            1f,
            0f,
            true, // Looping
            1f, // Weight
            new int[0], // footstepIntervals
            "SFX_Tornado"
        );
        
        ModelAsset.AnimationSet idleSet = new ModelAsset.AnimationSet(
            new ModelAsset.Animation[] { spinAnim },
            new Rangef(2.0f, 10.0f)
        );
        Map<String, ModelAsset.AnimationSet> animMap = new HashMap<>();
        animMap.put("Idle", idleSet);
        
        // Particles
        String[] particleNodes = {"4A", "4B", "3A", "3B", "2A", "2B", "1A", "1B"};
        ModelParticle[] particles = new ModelParticle[particleNodes.length];
        for(int i=0; i<particleNodes.length; i++) {
            particles[i] = new ModelParticle(
                "Ice_Staff",
                EntityPart.Self,
                particleNodes[i],
                null, 
                1.0f, 
                null, 
                null, 
                false
            );
        }
        
        // Trails
        List<ModelTrail> trailsList = new ArrayList<>();
        // Simple trails
        String[] simpleTrailNodes = {"GA", "GB", "1A", "1B", "2A", "2B", "3A", "3B", "4A", "4B"};
        for(String node : simpleTrailNodes) {
             trailsList.add(new ModelTrail(
                 "Wind",
                 EntityPart.Self,
                 node,
                 null,
                 null,
                 false
             ));
        }
        
        // Offset trails
        String[] offsetNodes = {"4A", "4B", "3A", "3B", "2A", "2B", "1A", "1B"};
        for(int i=0; i<offsetNodes.length; i++) {
            // Alternating offsets based on node suffix A / B somewhat in JSON but simplified pattern
            // JSON pattern: 
            // 4A: y=-0.1
            // 4B: y=0.1
            // 3A: y=-0.1
            // ...
            float yOffset = offsetNodes[i].endsWith("A") ? -0.1f : 0.1f;
            
            com.hypixel.hytale.protocol.Vector3f posOffset = new com.hypixel.hytale.protocol.Vector3f(0f, yOffset, 0f);
            Direction rotOffset = new Direction(0, 90, 0); // Yaw, Pitch, Roll. JSON: Pitch 90.
            // Direction constructor: float yaw, float pitch, float roll.
            
            trailsList.add(new ModelTrail(
                 "Wind",
                 EntityPart.Self,
                 offsetNodes[i],
                 posOffset,
                 rotOffset,
                 false
             ));
        }

        return new Model(
             null, // modelAssetId
             2.0f, // scale
             Collections.emptyMap(), // random attachments
             new ModelAttachment[0], // attachments
             new Box(-1, 0, -1, 1, 2, 1), // boundingBox
             "Items/Projectiles/Tornado.blockymodel",
             "Items/Projectiles/Tornado_Texture.png",
             null, // gradientSet
             null, // gradientId
             3.0f, // eyeHeight
             0.0f, // crouchOffset
             animMap,
             camera,
             null, // light
             particles,
             trailsList.toArray(new ModelTrail[0]),
             new PhysicsValues(68.0, 0.5, false), 
             Collections.emptyMap(), 
             Phobia.None,
             null 
        );
    }
    
    public static class ConfiguredAnimation extends ModelAsset.Animation {
        public ConfiguredAnimation(String id, String animation, float speed, float blendingDuration, boolean looping, float weight, int[] footstepIntervals, String soundEventId) {
            super(id, animation, speed, blendingDuration, looping, weight, footstepIntervals, soundEventId);
            this.processConfig();
        }
    }
    
    public static class WindPhysicsConfig extends StandardPhysicsConfig {
         public WindPhysicsConfig() {
             this.gravity = 0.02; // Small gravity 
             this.densityAir = 1.2; 
             this.terminalVelocityAir = 20.0;
             this.moveOutOfSolidSpeed = 0.1;
             this.bounciness = 0.0;
             this.rotationMode = RotationMode.Velocity; 
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
      Query.any();
      return true;
   }

   @Nonnull
   @Override
   public String toString() {
      return "LaunchHarvestWind{} " + super.toString();
   }
}
