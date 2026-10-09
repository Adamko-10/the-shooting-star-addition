package dev.ss05.halley.content;

import dev.ss05.halley.HalleyAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The addon's own blocks, sounds and damage type. Nothing here depends on The Shooting Star. */
public final class HalleyContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HalleyAddon.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HalleyAddon.MOD_ID);

    /** The comet's nucleus, left standing in the crater: glowing blue ice that nothing breaks (like Gungnir's spire). */
    public static final RegistryObject<Block> COMET_HEART = BLOCKS.register("comet_heart", () -> new Block(BlockBehaviour.Properties.of()
        .strength(-1.0F, 3600000.8F)
        .mapColor(MapColor.ICE)
        .sound(SoundType.AMETHYST)
        .lightLevel(state -> 15)
        .emissiveRendering((state, level, pos) -> true)
        .noLootTable()
        .isValidSpawn((state, level, pos, type) -> false)
        .pushReaction(PushReaction.BLOCK)));

    /** The frozen streak the nucleus scored down the floor of the trench. */
    public static final RegistryObject<Block> COMET_TRAIL = BLOCKS.register("comet_trail", () -> new Block(BlockBehaviour.Properties.of()
        .strength(-1.0F, 3600000.8F)
        .mapColor(MapColor.COLOR_LIGHT_BLUE)
        .sound(SoundType.GLASS)
        .lightLevel(state -> 10)
        .emissiveRendering((state, level, pos) -> true)
        .friction(0.98F)
        .noLootTable()
        .isValidSpawn((state, level, pos, type) -> false)
        .pushReaction(PushReaction.BLOCK)));

    // Sounds. The remote's own clicks (the cover and the button) are The Shooting Star's, everything after is SS-05's.
    public static final RegistryObject<SoundEvent> MARK = sound("halley_mark");
    public static final RegistryObject<SoundEvent> COUNTDOWN = sound("halley_countdown");
    public static final RegistryObject<SoundEvent> SIGHT = sound("halley_sight");
    public static final RegistryObject<SoundEvent> APPROACH = sound("halley_approach");
    public static final RegistryObject<SoundEvent> BOOM = sound("halley_boom");
    public static final RegistryObject<SoundEvent> TOUCHDOWN = sound("halley_touchdown");
    public static final RegistryObject<SoundEvent> PLOUGH = sound("halley_plough");
    public static final RegistryObject<SoundEvent> IMPACT = sound("halley_impact");
    public static final RegistryObject<SoundEvent> FROST = sound("halley_frost");
    public static final RegistryObject<SoundEvent> HUM = sound("halley_hum");
    public static final RegistryObject<SoundEvent> EVAC = sound("halley_evac");
    public static final RegistryObject<SoundEvent> DUSK = sound("halley_dusk");
    public static final RegistryObject<SoundEvent> BURST = sound("halley_burst");

    /** Damage from the shock wave outside the crater (inside it, things are erased the way the other skills erase). */
    public static final ResourceKey<DamageType> WAKE = ResourceKey.create(Registries.DAMAGE_TYPE, HalleyAddon.id("halley_wake"));

    private HalleyContent() {
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HalleyAddon.id(name)));
    }

    public static void register(IEventBus modBus) {
        // (main, NeoForge 1.21.1, also aliases the pre-release ss05_halley block ids; no 1.20.1 world ever had them)
        BLOCKS.register(modBus);
        SOUNDS.register(modBus);
    }
}
