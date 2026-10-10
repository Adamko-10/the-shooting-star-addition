package dev.ss05.halley.content;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.MoonConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The addon's own blocks, sounds and damage type. Nothing here depends on The Shooting Star. */
public final class HalleyContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HalleyAddon.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HalleyAddon.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HalleyAddon.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, HalleyAddon.MOD_ID);

    /**
     * The comet's nucleus, left standing in the crater: glowing blue ice. Tough (like Gungnir's own blocks, which
     * also require a pickaxe and drop themselves), but no longer unbreakable: a pickaxe mines it in a reasonable
     * time and it drops itself (see the loot table and the item below).
     */
    public static final DeferredBlock<Block> COMET_HEART = BLOCKS.registerSimpleBlock("comet_heart", BlockBehaviour.Properties.of()
        .strength(2.5F, 6.0F)
        .mapColor(MapColor.ICE)
        .sound(SoundType.AMETHYST)
        .lightLevel(state -> 15)
        .emissiveRendering((state, level, pos) -> true)
        .requiresCorrectToolForDrops()
        .isValidSpawn((state, level, pos, type) -> false)
        .pushReaction(PushReaction.BLOCK));

    /** The frozen streak the nucleus scored down the floor of the trench. Pickaxe-minable, drops itself. */
    public static final DeferredBlock<Block> COMET_TRAIL = BLOCKS.registerSimpleBlock("comet_trail", BlockBehaviour.Properties.of()
        .strength(1.0F, 6.0F)
        .mapColor(MapColor.COLOR_LIGHT_BLUE)
        .sound(SoundType.GLASS)
        .lightLevel(state -> 10)
        .emissiveRendering((state, level, pos) -> true)
        .friction(0.98F)
        .requiresCorrectToolForDrops()
        .isValidSpawn((state, level, pos, type) -> false)
        .pushReaction(PushReaction.BLOCK));

    /**
     * What the fallen moon is made of (SS-06). Mined like soft stone; its item is eaten like a golden carrot.
     * (Properties are refined in content; the item and its food live with the other moon cheese pieces.)
     */
    public static final DeferredBlock<Block> MOON_CHEESE = BLOCKS.registerSimpleBlock("moon_cheese", BlockBehaviour.Properties.of()
        .strength(1.2F, 6.0F)
        .mapColor(MapColor.COLOR_YELLOW)
        .sound(SoundType.TUFF)
        // drawn self-lit (only how it looks, no light is cast), so a fallen moon still reads as a moon at night
        .emissiveRendering((state, level, pos) -> true));

    /** The single block at the very heart of the fallen moon: glowing, molten. Eating it gives the molten might. */
    public static final DeferredBlock<Block> MOLTEN_MOON_CHEESE = BLOCKS.registerSimpleBlock("molten_moon_cheese", BlockBehaviour.Properties.of()
        .strength(1.5F, 6.0F)
        .mapColor(MapColor.COLOR_ORANGE)
        .sound(SoundType.HONEY_BLOCK)
        .lightLevel(state -> 15)
        .emissiveRendering((state, level, pos) -> true));

    /** What eating molten moon cheese gives: the raised max health (see {@link MoltenMightEffect}); Strength is vanilla's own effect, applied alongside it. */
    public static final DeferredHolder<MobEffect, MoltenMightEffect> MOLTEN_MIGHT = EFFECTS.register("molten_might", MoltenMightEffect::new);

    /** Eaten like a golden carrot (6 hunger, 14.4 saturation); right-click a block to place it, right-click in the air to eat it. */
    /** The comet's heart and trail as items, so they can be mined and carried off like The Shooting Star's own blocks. */
    public static final DeferredItem<BlockItem> COMET_HEART_ITEM = ITEMS.registerSimpleBlockItem(COMET_HEART, new Item.Properties());
    public static final DeferredItem<BlockItem> COMET_TRAIL_ITEM = ITEMS.registerSimpleBlockItem(COMET_TRAIL, new Item.Properties());

    public static final DeferredItem<BlockItem> MOON_CHEESE_ITEM =
        ITEMS.registerSimpleBlockItem(MOON_CHEESE, new Item.Properties().food(Foods.GOLDEN_CARROT));

    /**
     * Always edible, like a golden apple; gives Molten Might and Strength for {@code molten_minutes} and heals to
     * the new full health (both read live from {@link MoonConfig} each time it's eaten, inside {@link #MOLTEN_MIGHT}
     * and these effect suppliers, which run at the moment of eating rather than at registration time).
     */
    private static final FoodProperties MOLTEN_MOON_CHEESE_FOOD = new FoodProperties.Builder()
        .nutrition(4)
        .saturationModifier(1.2F)
        .alwaysEdible()
        .effect(() -> new MobEffectInstance(MOLTEN_MIGHT, MoonConfig.tuning().moltenMinutes() * 60 * 20, 0), 1.0F)
        .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, MoonConfig.tuning().moltenMinutes() * 60 * 20, MoonConfig.tuning().moltenStrength() - 1), 1.0F)
        .build();
    public static final DeferredItem<BlockItem> MOLTEN_MOON_CHEESE_ITEM =
        ITEMS.registerSimpleBlockItem(MOLTEN_MOON_CHEESE, new Item.Properties().food(MOLTEN_MOON_CHEESE_FOOD));

    // Sounds. The remote's own clicks (the cover and the button) are The Shooting Star's, everything after is SS-05's.
    public static final DeferredHolder<SoundEvent, SoundEvent> MARK = sound("halley_mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> COUNTDOWN = sound("halley_countdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGHT = sound("halley_sight");
    public static final DeferredHolder<SoundEvent, SoundEvent> APPROACH = sound("halley_approach");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOM = sound("halley_boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOUCHDOWN = sound("halley_touchdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLOUGH = sound("halley_plough");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = sound("halley_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST = sound("halley_frost");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM = sound("halley_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> EVAC = sound("halley_evac");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUSK = sound("halley_dusk");
    public static final DeferredHolder<SoundEvent, SoundEvent> BURST = sound("halley_burst");
    // SS-06 Luna.
    /** The alarm under "EARTH SYSTEM SHUT DOWN". */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_ALARM = sound("luna_alarm");
    /** The moon cracking open in the sky. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_CRACK = sound("luna_crack");
    /** The long, rising roar of the fall. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_FALL = sound("luna_fall");
    /** Burning through the atmosphere. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_ENTRY = sound("luna_entry");
    /** The impact. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_IMPACT = sound("luna_impact");
    /** Grinding down into the crater and settling. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_SETTLE = sound("luna_settle");
    /** Debris and rubble raining down afterwards. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_DEBRIS = sound("luna_debris");

    /** Damage from the shock wave outside the crater (inside it, things are erased the way the other skills erase). */
    public static final ResourceKey<DamageType> WAKE = ResourceKey.create(Registries.DAMAGE_TYPE, HalleyAddon.id("halley_wake"));

    private HalleyContent() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HalleyAddon.id(name)));
    }

    public static void register(IEventBus modBus) {
        // before it was named, the addon's id was ss05_halley: worlds played with that build keep their comet hearts
        BLOCKS.addAlias(ResourceLocation.fromNamespaceAndPath("ss05_halley", "comet_heart"), COMET_HEART.getId());
        BLOCKS.addAlias(ResourceLocation.fromNamespaceAndPath("ss05_halley", "comet_trail"), COMET_TRAIL.getId());
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, HalleyContent::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(MOON_CHEESE_ITEM);
            event.accept(MOLTEN_MOON_CHEESE_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(COMET_HEART_ITEM);
            event.accept(COMET_TRAIL_ITEM);
            event.accept(MOON_CHEESE_ITEM);
            event.accept(MOLTEN_MOON_CHEESE_ITEM);
        }
    }
}
