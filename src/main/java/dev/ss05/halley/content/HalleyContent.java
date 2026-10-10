package dev.ss05.halley.content;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.MoonConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The addon's own blocks, sounds and damage type. Nothing here depends on The Shooting Star. */
public final class HalleyContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HalleyAddon.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HalleyAddon.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HalleyAddon.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, HalleyAddon.MOD_ID);

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

    /**
     * What the fallen moon is made of (SS-06). Mined like soft stone; its item is eaten like a golden carrot.
     * (Properties are refined in content; the item and its food live with the other moon cheese pieces.)
     */
    public static final RegistryObject<Block> MOON_CHEESE = BLOCKS.register("moon_cheese", () -> new Block(BlockBehaviour.Properties.of()
        .strength(1.2F, 6.0F)
        .mapColor(MapColor.COLOR_YELLOW)
        .sound(SoundType.TUFF)
        // drawn self-lit (only how it looks, no light is cast), so a fallen moon still reads as a moon at night
        .emissiveRendering((state, level, pos) -> true)));

    /** The single block at the very heart of the fallen moon: glowing, molten. Eating it gives the molten might. */
    public static final RegistryObject<Block> MOLTEN_MOON_CHEESE = BLOCKS.register("molten_moon_cheese", () -> new Block(BlockBehaviour.Properties.of()
        .strength(1.5F, 6.0F)
        .mapColor(MapColor.COLOR_ORANGE)
        .sound(SoundType.HONEY_BLOCK)
        .lightLevel(state -> 15)
        .emissiveRendering((state, level, pos) -> true)));

    /** What eating molten moon cheese gives: the raised max health (see {@link MoltenMightEffect}); Strength is vanilla's own effect, applied alongside it. */
    public static final RegistryObject<MobEffect> MOLTEN_MIGHT = EFFECTS.register("molten_might", MoltenMightEffect::new);

    /** Eaten like a golden carrot (6 hunger, 14.4 saturation); right-click a block to place it, right-click in the air to eat it. */
    public static final RegistryObject<BlockItem> MOON_CHEESE_ITEM =
        ITEMS.register("moon_cheese", () -> new BlockItem(MOON_CHEESE.get(), new Item.Properties().food(Foods.GOLDEN_CARROT)));

    /**
     * Always edible, like a golden apple; gives Molten Might and Strength for {@code molten_minutes} and heals to
     * the new full health (both read live from {@link MoonConfig} each time it's eaten, inside {@link #MOLTEN_MIGHT}
     * and these effect suppliers, which run at the moment of eating rather than at registration time).
     */
    private static final FoodProperties MOLTEN_MOON_CHEESE_FOOD = new FoodProperties.Builder()
        .nutrition(4)
        .saturationMod(1.2F)
        .alwaysEat()
        .effect(() -> new MobEffectInstance(MOLTEN_MIGHT.get(), MoonConfig.tuning().moltenMinutes() * 60 * 20, 0), 1.0F)
        .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, MoonConfig.tuning().moltenMinutes() * 60 * 20, MoonConfig.tuning().moltenStrength() - 1), 1.0F)
        .build();
    public static final RegistryObject<BlockItem> MOLTEN_MOON_CHEESE_ITEM =
        ITEMS.register("molten_moon_cheese", () -> new BlockItem(MOLTEN_MOON_CHEESE.get(), new Item.Properties().food(MOLTEN_MOON_CHEESE_FOOD)));

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
    // SS-06 Luna.
    /** The alarm under "EARTH SYSTEM SHUT DOWN". */
    public static final RegistryObject<SoundEvent> MOON_ALARM = sound("luna_alarm");
    /** The moon cracking open in the sky. */
    public static final RegistryObject<SoundEvent> MOON_CRACK = sound("luna_crack");
    /** The long, rising roar of the fall. */
    public static final RegistryObject<SoundEvent> MOON_FALL = sound("luna_fall");
    /** Burning through the atmosphere. */
    public static final RegistryObject<SoundEvent> MOON_ENTRY = sound("luna_entry");
    /** The impact. */
    public static final RegistryObject<SoundEvent> MOON_IMPACT = sound("luna_impact");
    /** Grinding down into the crater and settling. */
    public static final RegistryObject<SoundEvent> MOON_SETTLE = sound("luna_settle");
    /** Debris and rubble raining down afterwards. */
    public static final RegistryObject<SoundEvent> MOON_DEBRIS = sound("luna_debris");

    /** Damage from the shock wave outside the crater (inside it, things are erased the way the other skills erase). */
    public static final ResourceKey<DamageType> WAKE = ResourceKey.create(Registries.DAMAGE_TYPE, HalleyAddon.id("halley_wake"));

    private HalleyContent() {
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HalleyAddon.id(name)));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(HalleyContent::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(MOON_CHEESE_ITEM);
            event.accept(MOLTEN_MOON_CHEESE_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(MOON_CHEESE_ITEM);
            event.accept(MOLTEN_MOON_CHEESE_ITEM);
        }
    }
}
