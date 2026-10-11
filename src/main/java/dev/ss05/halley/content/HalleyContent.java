package dev.ss05.halley.content;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.MoonConfig;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The addon's own blocks, sounds and damage type. Nothing here depends on The Shooting Star. */
public final class HalleyContent {
    /**
     * The comet's nucleus, left standing in the crater: glowing blue ice. Tough, but a pickaxe mines it and it drops
     * itself (like Gungnir's own blocks).
     */
    public static final Block COMET_HEART = block("comet_heart", p -> p
        .strength(2.5F, 6.0F)
        .mapColor(MapColor.ICE)
        .sound(SoundType.AMETHYST)
        .lightLevel(state -> 15)
        .emissiveRendering((state, level, pos) -> true)
        .requiresCorrectToolForDrops()
        .isValidSpawn((state, level, pos, type) -> false)
        .pushReaction(PushReaction.BLOCK));

    /** The frozen streak the nucleus scored down the floor of the trench. Pickaxe-minable, drops itself. */
    public static final Block COMET_TRAIL = block("comet_trail", p -> p
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
    public static final Block MOON_CHEESE = block("moon_cheese", p -> p
        .strength(1.2F, 6.0F)
        .mapColor(MapColor.COLOR_YELLOW)
        .sound(SoundType.TUFF)
        // drawn self-lit (only how it looks, no light is cast), so a fallen moon still reads as a moon at night
        .emissiveRendering((state, level, pos) -> true));

    /** The single block at the very heart of the fallen moon: glowing, molten. Eating it gives the molten might. */
    public static final Block MOLTEN_MOON_CHEESE = block("molten_moon_cheese", p -> p
        .strength(1.5F, 6.0F)
        .mapColor(MapColor.COLOR_ORANGE)
        .sound(SoundType.HONEY_BLOCK)
        .lightLevel(state -> 15)
        .emissiveRendering((state, level, pos) -> true));

    /** What eating molten moon cheese gives: the raised max health (see {@link MoltenMightEffect}); Strength is vanilla's own effect, applied alongside it. */
    public static final Holder<MobEffect> MOLTEN_MIGHT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
        ResourceKey.create(Registries.MOB_EFFECT, HalleyAddon.id("molten_might")), new MoltenMightEffect());

    /** Eaten like a golden carrot (6 hunger, 14.4 saturation); right-click a block to place it, right-click in the air to eat it. */
    /** The comet's heart and trail as items, so they can be mined and carried off like The Shooting Star's own blocks. */
    public static final Item COMET_HEART_ITEM = item("comet_heart", key -> new BlockItem(COMET_HEART, new Item.Properties().setId(key)));
    public static final Item COMET_TRAIL_ITEM = item("comet_trail", key -> new BlockItem(COMET_TRAIL, new Item.Properties().setId(key)));

    public static final Item MOON_CHEESE_ITEM = item("moon_cheese", key -> new BlockItem(MOON_CHEESE, new Item.Properties().setId(key).food(Foods.GOLDEN_CARROT)));

    /**
     * Always edible, like a golden apple; gives Molten Might and Strength for {@code molten_minutes} and heals to
     * the new full health (the healing and the raised max health are read live from {@link MoonConfig} every time
     * the effect (re)starts, inside {@link MoltenMightEffect}; the duration and the Strength level below are fixed
     * when this item is built, which happens once the common config has already loaded).
     */
    private static Consumable moltenConsumable() {
        MoonConfig.Tuning tuning = MoonConfig.tuning();
        int ticks = tuning.moltenMinutes() * 60 * 20;
        return Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
            new MobEffectInstance(MOLTEN_MIGHT, ticks, 0),
            new MobEffectInstance(MobEffects.STRENGTH, ticks, tuning.moltenStrength() - 1)))).build();
    }

    public static final Item MOLTEN_MOON_CHEESE_ITEM = item("molten_moon_cheese", key -> new BlockItem(MOLTEN_MOON_CHEESE, new Item.Properties().setId(key)
        .food(new FoodProperties.Builder().nutrition(4).saturationModifier(1.2F).alwaysEdible().build(), moltenConsumable())));

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
    // SS-06 Luna.
    /** The alarm under "EARTH SYSTEM SHUT DOWN". */
    public static final SoundEvent MOON_ALARM = sound("luna_alarm");
    /** The moon cracking open in the sky. */
    public static final SoundEvent MOON_CRACK = sound("luna_crack");
    /** The long, rising roar of the fall. */
    public static final SoundEvent MOON_FALL = sound("luna_fall");
    /** Burning through the atmosphere. */
    public static final SoundEvent MOON_ENTRY = sound("luna_entry");
    /** The impact. */
    public static final SoundEvent MOON_IMPACT = sound("luna_impact");
    /** Grinding down into the crater and settling. */
    public static final SoundEvent MOON_SETTLE = sound("luna_settle");
    /** Debris and rubble raining down afterwards. */
    public static final SoundEvent MOON_DEBRIS = sound("luna_debris");

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

    private static Item item(String name, Function<ResourceKey<Item>, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HalleyAddon.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(key));
    }

    // The vanilla tab fields on CreativeModeTabs are private in 26.3; their registry keys are still these fixed ids.
    private static final ResourceKey<CreativeModeTab> FOOD_AND_DRINKS_TAB =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("food_and_drinks"));
    private static final ResourceKey<CreativeModeTab> NATURAL_BLOCKS_TAB =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("natural_blocks"));

    /** Registers everything (loading this class does it); called once while mods initialise. */
    public static void register() {
        HalleyAddon.LOG.debug("SS-05 Halley's blocks and sounds are registered ({}, {})", COMET_HEART, MARK.location());
        ItemGroupEvents.modifyEntriesEvent(FOOD_AND_DRINKS_TAB).register(output -> {
            output.accept(MOON_CHEESE_ITEM);
            output.accept(MOLTEN_MOON_CHEESE_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(NATURAL_BLOCKS_TAB).register(output -> {
            output.accept(COMET_HEART_ITEM);
            output.accept(COMET_TRAIL_ITEM);
            output.accept(MOON_CHEESE_ITEM);
            output.accept(MOLTEN_MOON_CHEESE_ITEM);
        });
    }
}
