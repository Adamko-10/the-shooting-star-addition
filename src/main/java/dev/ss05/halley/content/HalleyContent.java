package dev.ss05.halley.content;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.MoonConfig;
import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The addon's own blocks, sounds and damage type. Nothing here depends on The Shooting Star. */
public final class HalleyContent {
    /**
     * The comet's nucleus, left standing in the crater: glowing blue ice. Tough, but a pickaxe mines it and it drops
     * itself (like Gungnir's own blocks).
     */
    public static final Block COMET_HEART = block("comet_heart", BlockBehaviour.Properties.of()
        .strength(2.5F, 6.0F)
        .mapColor(MapColor.ICE)
        .sound(SoundType.AMETHYST)
        .lightLevel(state -> 15)
        .emissiveRendering((state, level, pos) -> true)
        .requiresCorrectToolForDrops()
        .isValidSpawn((state, level, pos, type) -> false)
        .pushReaction(PushReaction.BLOCK));

    /** The frozen streak the nucleus scored down the floor of the trench. Pickaxe-minable, drops itself. */
    public static final Block COMET_TRAIL = block("comet_trail", BlockBehaviour.Properties.of()
        .strength(1.0F, 6.0F)
        .mapColor(MapColor.COLOR_LIGHT_BLUE)
        .sound(SoundType.GLASS)
        .lightLevel(state -> 10)
        .emissiveRendering((state, level, pos) -> true)
        .friction(0.98F)
        .requiresCorrectToolForDrops()
        .isValidSpawn((state, level, pos, type) -> false)
        .pushReaction(PushReaction.BLOCK));

    /** What the fallen moon is made of (SA-02). Mined like soft stone; its item is eaten like a golden carrot. */
    public static final Block MOON_CHEESE = block("moon_cheese", BlockBehaviour.Properties.of()
        .strength(1.2F, 6.0F)
        .mapColor(MapColor.COLOR_YELLOW)
        .sound(SoundType.TUFF)
        // drawn self-lit (only how it looks, no light is cast), so a fallen moon still reads as a moon at night
        .emissiveRendering((state, level, pos) -> true));

    /**
     * Moon cheese with a hole in it: the eyes of the cheese, scattered all through the fallen moon (one block in eight
     * or so). Otherwise it is just moon cheese: mined, placed and eaten the same.
     */
    public static final Block HOLEY_MOON_CHEESE = block("holey_moon_cheese", BlockBehaviour.Properties.of()
        .strength(1.2F, 6.0F)
        .mapColor(MapColor.COLOR_YELLOW)
        .sound(SoundType.TUFF)
        .emissiveRendering((state, level, pos) -> true));

    /** The single block at the very heart of the fallen moon: glowing, molten. Eating it gives the molten might. */
    public static final Block MOLTEN_MOON_CHEESE = block("molten_moon_cheese", BlockBehaviour.Properties.of()
        .strength(1.5F, 6.0F)
        .mapColor(MapColor.COLOR_ORANGE)
        .sound(SoundType.HONEY_BLOCK)
        .lightLevel(state -> 15)
        .emissiveRendering((state, level, pos) -> true));

    /** What eating molten moon cheese gives: the raised max health (see {@link MoltenMightEffect}); Strength is vanilla's own effect, applied alongside it. */
    public static final Holder<MobEffect> MOLTEN_MIGHT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
        HalleyAddon.id("molten_might"), new MoltenMightEffect());

    /** The comet's heart and trail as items, so they can be mined and carried off like The Shooting Star's own blocks. */
    public static final Item COMET_HEART_ITEM = item("comet_heart", props -> new BlockItem(COMET_HEART, props));
    public static final Item COMET_TRAIL_ITEM = item("comet_trail", props -> new BlockItem(COMET_TRAIL, props));

    /**
     * Eaten like a golden carrot (6 hunger, 14.4 saturation); right-click a block to place it, right-click in the air to
     * eat it. Cheese, so eating any of it first clears your effects like milk does ({@link CheeseItem}).
     */
    public static final Item MOON_CHEESE_ITEM = item("moon_cheese", props -> new CheeseItem(MOON_CHEESE, props.food(Foods.GOLDEN_CARROT)));
    public static final Item HOLEY_MOON_CHEESE_ITEM = item("holey_moon_cheese", props -> new CheeseItem(HOLEY_MOON_CHEESE, props.food(Foods.GOLDEN_CARROT)));

    /**
     * Always edible, like a golden apple, and fills the hunger bar and its saturation to the top (nutrition 20 at
     * saturation modifier 0.5: 20 hunger, 20 saturation). Only that plain part lives in these food properties (vanilla's
     * own food effects are fixed when the item is made, before the config has loaded): Molten Might and Strength are
     * given by {@link MoltenMoonCheeseItem}, read live from {@link MoonConfig} at the moment it is eaten.
     */
    private static final FoodProperties MOLTEN_MOON_CHEESE_FOOD = new FoodProperties.Builder()
        .nutrition(20)
        .saturationModifier(0.5F)
        .alwaysEdible()
        .build();
    public static final Item MOLTEN_MOON_CHEESE_ITEM = item("molten_moon_cheese",
        props -> new MoltenMoonCheeseItem(MOLTEN_MOON_CHEESE, props.food(MOLTEN_MOON_CHEESE_FOOD)));

    // Sounds. The remote's own clicks (the cover and the button) are The Shooting Star's, everything after is ours.
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
    // Luna.
    /** The uplink locking on as the button goes down. */
    public static final SoundEvent MOON_UPLINK = sound("luna_uplink");
    /** The alarm under "EARTH SYSTEM SHUT DOWN". */
    public static final SoundEvent MOON_ALARM = sound("luna_alarm");
    /** The moon cracking open in the sky. */
    public static final SoundEvent MOON_CRACK = sound("luna_crack");
    /** The moon tearing loose from its place in the sky. */
    public static final SoundEvent MOON_TEAR = sound("luna_tear");
    /** The long, rising roar of the fall. */
    public static final SoundEvent MOON_FALL = sound("luna_fall");
    /** Burning through the atmosphere. */
    public static final SoundEvent MOON_ENTRY = sound("luna_entry");
    /** The ground rumbling and the air howling in the last seconds before it lands. */
    public static final SoundEvent MOON_RUMBLE = sound("luna_rumble");
    /** The impact. */
    public static final SoundEvent MOON_IMPACT = sound("luna_impact");
    /** The shock wave rolling over (a moment after the impact, later the further off you are). */
    public static final SoundEvent MOON_SHOCKWAVE = sound("luna_shockwave");
    /** Grinding down into the earth while the ground heaves up round it. */
    public static final SoundEvent MOON_SETTLE = sound("luna_settle");
    /** Aftershocks: the ground settling, rock sliding off the rim. */
    public static final SoundEvent MOON_AFTERSHOCK = sound("luna_aftershock");
    /** The molten seam round the moon hissing and spitting. */
    public static final SoundEvent MOON_SIZZLE = sound("luna_sizzle");
    /** Debris and rubble raining down afterwards. */
    public static final SoundEvent MOON_DEBRIS = sound("luna_debris");

    /** Damage from the shock wave outside the crater (inside it, things are erased the way the other skills erase). */
    public static final ResourceKey<DamageType> WAKE = ResourceKey.create(Registries.DAMAGE_TYPE, HalleyAddon.id("halley_wake"));

    private HalleyContent() {
    }

    /** Whether a block is one the fallen moon is made of: moon cheese, holey moon cheese or the molten heart. */
    public static boolean isMoonCheese(BlockState state) {
        return state.is(MOON_CHEESE) || state.is(HOLEY_MOON_CHEESE) || state.is(MOLTEN_MOON_CHEESE);
    }

    private static Block block(String name, BlockBehaviour.Properties properties) {
        return Registry.register(BuiltInRegistries.BLOCK, HalleyAddon.id(name), new Block(properties));
    }

    private static Item item(String name, Function<Item.Properties, Item> factory) {
        return Registry.register(BuiltInRegistries.ITEM, HalleyAddon.id(name), factory.apply(new Item.Properties()));
    }

    private static SoundEvent sound(String name) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, HalleyAddon.id(name), SoundEvent.createVariableRangeEvent(HalleyAddon.id(name)));
    }

    // The vanilla tab fields on CreativeModeTabs are private; their registry keys are these fixed ids.
    private static final ResourceKey<CreativeModeTab> FOOD_AND_DRINKS_TAB =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("food_and_drinks"));
    private static final ResourceKey<CreativeModeTab> NATURAL_BLOCKS_TAB =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("natural_blocks"));

    /** Registers everything (loading this class does it); called once while mods initialise. */
    public static void register() {
        HalleyAddon.LOG.debug("The addon's blocks and sounds are registered ({}, {})", COMET_HEART, MARK.getLocation());
        ItemGroupEvents.modifyEntriesEvent(FOOD_AND_DRINKS_TAB).register(entries -> {
            entries.accept(MOON_CHEESE_ITEM);
            entries.accept(HOLEY_MOON_CHEESE_ITEM);
            entries.accept(MOLTEN_MOON_CHEESE_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(NATURAL_BLOCKS_TAB).register(entries -> {
            entries.accept(COMET_HEART_ITEM);
            entries.accept(COMET_TRAIL_ITEM);
            entries.accept(MOON_CHEESE_ITEM);
            entries.accept(HOLEY_MOON_CHEESE_ITEM);
            entries.accept(MOLTEN_MOON_CHEESE_ITEM);
        });
    }

    /**
     * Molten moon cheese's item: eaten like the other cheese (your effects cleared like milk first, then the plain
     * hunger/saturation part), then Molten Might and Strength are given, read live from {@link MoonConfig} at the moment
     * it's eaten - not fixed when the item was made, so a server operator's config always takes effect.
     */
    public static final class MoltenMoonCheeseItem extends CheeseItem {
        public MoltenMoonCheeseItem(Block block, Item.Properties properties) {
            super(block, properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (!level.isClientSide()) {
                MoonConfig.Tuning tuning = MoonConfig.tuning();
                int duration = tuning.moltenMinutes() * 60 * 20;
                entity.addEffect(new MobEffectInstance(MOLTEN_MIGHT, duration, 0));
                entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, tuning.moltenStrength() - 1));
            }
            return result;
        }
    }
}
