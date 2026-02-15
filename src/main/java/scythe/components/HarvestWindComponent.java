package scythe.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import scythe.Scythe;

import javax.annotation.Nonnull;

public class HarvestWindComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<HarvestWindComponent> CODEC = BuilderCodec
        .builder(HarvestWindComponent.class, HarvestWindComponent::new)
        .append(new KeyedCodec<Double>("Radius", Codec.DOUBLE), (o, i) -> o.radius = i, o -> o.radius)
        .add()
        .append(new KeyedCodec<Integer>("Height", Codec.INTEGER), (o, i) -> o.height = i , o -> o.height)
        .add()
        .build();

    public double radius;
    public int height;

    public HarvestWindComponent() {
    }

    public HarvestWindComponent(double radius, int height) {
        this.radius = radius;
        this.height = height;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new HarvestWindComponent(radius, height);
    }

    public static ComponentType<EntityStore, HarvestWindComponent>
    getComponentType() {
        return Scythe.get().getHarvestWindComponentType();
    }
}
