package dev.ss05.halley.content;

import dev.ss05.halley.HalleyAddon;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The addon's own blocks, sounds and damage type. Nothing here depends on The Shooting Star. */
public final class HalleyContent {
    /** The comet's nucleus, left standing in the crater: glowing blue ice that nothing breaks (like Gungnir's spire). */
    public static final Block COMET_HEART = block("comet_heart", p -> p
        .strength(-1.0F, 3600000.8F)
        .mapColor(MapColor.ICE)
        .sound(SoundType.AMETHYST)
        .lightLevel(state -> 15)
        .emissiveRendering(state -> true)
        .noLootTable()
        .isValidSpawn((state, level, pos, type) -> false)
        .pushReaction(PushReaction.IMMOVEABLE));

    /** The frozen streak the nucleus scored down the floor of the trench. */
    public static final Block COMET_TRAIL = block("comet_trail", p -> p
        .strength(-1.0F, 3600000.8F)
        .mapColor(MapColor.COLOR_LIGHT_BLUE)
        .sound(SoundType.GLASS)
        .lightLevel(state -> 10)
        .emissiveRendering(state -> true)
        .friction(0.98F)
        .noLootTable()
        .isValidSpawn((state, level, pos, type) -> false)
        .pushReaction(PushReaction.IMMOVEABLE));

    // Sounds. The remote's own clicks (the cover and the button) are The Shooting Star's, everything after is SS-05's.
    public static final SoundEvent MARK = sound("halley_mark");
    public static final SoundEvent COUNTDOWN = sound("halley_countdown");
    public static final SoundEvent SIGHT = sound("halley_sight");
    public static final SoundEvent APPROACH = sound("halley_approach");
    public static final SoundEvent BOOM = sound("halley_boom");
    public static final SoundEvent TOUCHDOWN = sound("halley_touchdown");
    public static final SoundEvent PLOUGH = sound("halley_plough");
    public static final SoundEvent IMPACT = sound("halley_impact");
    public static final SoundEvent FROST = sound("halley_frost");
    public static final SoundEvent HUM = sound("halley_hum");
    public static final SoundEvent EVAC = sound("halley_evac");
    public static final SoundEvent DUSK = sound("halley_dusk");
    public static final SoundEvent BURST = sound("halley_burst");

    /**
     * Keeps the chunks under a strike loaded and ticking until it is over (the trench and the crater are cut a chunk
     * at a time, and things in them have to be there to be hit). Never saved, never times out: released by the strike.
     */
    public static final TicketType CHUNKS = Registry.register(BuiltInRegistries.TICKET_TYPE, HalleyAddon.id("halley"),
        new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    /** Damage from the shock wave outside the crater (inside it, things are erased the way the other skills erase). */
    public static final ResourceKey<DamageType> WAKE = ResourceKey.create(Registries.DAMAGE_TYPE, HalleyAddon.id("halley_wake"));

    private HalleyContent() {
    }

    private static Block block(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, HalleyAddon.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.apply(BlockBehaviour.Properties.of()).setId(key)));
    }

    private static SoundEvent sound(String name) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, HalleyAddon.id(name), SoundEvent.createVariableRangeEvent(HalleyAddon.id(name)));
    }

    /** Registers everything (loading this class does it); called once while mods initialise. */
    public static void register() {
        HalleyAddon.LOG.debug("SS-05 Halley's blocks and sounds are registered ({}, {})", COMET_HEART, MARK.location());
    }
}
