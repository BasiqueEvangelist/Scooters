package hibi.scooters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hibi.scooters.recipes.ElectricScooterRecipe;
import hibi.scooters.recipes.KickScooterRecipe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class Common implements ModInitializer {

	public static final String MODID = "scooters";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	public static final Identifier KICK_SCOOTER_ID;
	public static final EntityType<ScooterEntity> KICK_SCOOTER_ENTITY;
	public static final Item KICK_SCOOTER_ITEM;
	public static final SpecialRecipeSerializer<KickScooterRecipe> KICK_SCOOTER_CRAFTING_SERIALIZER;

	public static final Identifier ELECTRIC_SCOOTER_ID;
	public static final EntityType<ElectricScooterEntity> ELECTRIC_SCOOTER_ENTITY;
	public static final Item ELECTRIC_SCOOTER_ITEM;
	public static final SpecialRecipeSerializer<ElectricScooterRecipe> ELECTRIC_SCOOTER_CRAFTING_SERIALIZER;

	public static final Identifier CHARGING_STATION_ID;
	public static final DockBlock CHARGING_STATION_BLOCK;
	public static final BlockEntityType<DockBlockEntity> CHARGING_STATION_BLOCK_ENTITY;

	public static final Item TIRE_ITEM;
	public static final Item RAW_TIRE_ITEM;

	public static final Identifier PACKET_INVENTORY_CHANGED_ID;
	public static final Identifier PACKET_THROTTLE_ID;
	public static final ScreenHandlerType<ScooterScreenHandler> SCOOTER_SCREEN_HANDLER = ScreenHandlerRegistry.registerExtended(new Identifier("scooters", "scooter"), ScooterScreenHandler::new);
	public static final TagKey<Block> ABRASIVE_BLOCKS = TagKey.of(Registry.BLOCK_KEY, new Identifier(MODID, "abrasive"));

	public static final SoundEvent SOUND_SCOOTER_ROLLING = new SoundEvent(new Identifier(MODID, "entity.roll"));
	public static final SoundEvent SOUND_SCOOTER_TIRE_POP = new SoundEvent(new Identifier(MODID, "entity.tire_pop"));
	public static final SoundEvent SOUND_CHARGER_CONNECT = new SoundEvent(new Identifier(MODID, "charger.connect"));
	public static final SoundEvent SOUND_CHARGER_DISCONNECT = new SoundEvent(new Identifier(MODID, "charger.disconnect"));

	@Override
	public void onInitialize() {

		// Kick Scooter //
		Registry.register(Registry.ENTITY_TYPE, KICK_SCOOTER_ID, KICK_SCOOTER_ENTITY);
		Registry.register(Registry.ITEM, KICK_SCOOTER_ID, KICK_SCOOTER_ITEM);
		RecipeSerializer.register(KICK_SCOOTER_ID.toString() + "_craft", KICK_SCOOTER_CRAFTING_SERIALIZER);

		// Electric Scooter //
		Registry.register(Registry.ENTITY_TYPE, ELECTRIC_SCOOTER_ID, ELECTRIC_SCOOTER_ENTITY);
		Registry.register(Registry.ITEM, ELECTRIC_SCOOTER_ID, ELECTRIC_SCOOTER_ITEM);
		RecipeSerializer.register(ELECTRIC_SCOOTER_ID.toString() + "_craft", ELECTRIC_SCOOTER_CRAFTING_SERIALIZER);

		// Charging Station  //
		Registry.register(Registry.BLOCK, CHARGING_STATION_ID, CHARGING_STATION_BLOCK);
		Registry.register(Registry.ITEM, CHARGING_STATION_ID, new BlockItem(CHARGING_STATION_BLOCK, new FabricItemSettings().group(ItemGroup.TRANSPORTATION)));
		Registry.register(Registry.BLOCK_ENTITY_TYPE, CHARGING_STATION_ID, CHARGING_STATION_BLOCK_ENTITY);

		// Tires //
		Registry.register(Registry.ITEM, new Identifier(MODID, "tire"), TIRE_ITEM);
		Registry.register(Registry.ITEM, new Identifier(MODID, "raw_tire"), RAW_TIRE_ITEM);

		// Networking and Misc //
		ServerPlayNetworking.registerGlobalReceiver(PACKET_THROTTLE_ID, (server, player, handler, buf, responseSender) -> {
			ElectricScooterEntity.updateThrottle(player.getWorld(),buf);
		});

		// Sounds //
		Registry.register(Registry.SOUND_EVENT, SOUND_SCOOTER_ROLLING.getId(), SOUND_SCOOTER_ROLLING);
		Registry.register(Registry.SOUND_EVENT, SOUND_SCOOTER_TIRE_POP.getId(), SOUND_SCOOTER_TIRE_POP);
		Registry.register(Registry.SOUND_EVENT, SOUND_CHARGER_CONNECT.getId(), SOUND_CHARGER_CONNECT);
		Registry.register(Registry.SOUND_EVENT, SOUND_CHARGER_DISCONNECT.getId(), SOUND_CHARGER_DISCONNECT);
	}

	static {
		KICK_SCOOTER_ID = new Identifier(MODID, "kick_scooter");
		KICK_SCOOTER_ENTITY = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ScooterEntity::new)
			.dimensions(EntityDimensions.fixed(0.8f, 0.8f))
			.trackRangeBlocks(10)
			.build();
		KICK_SCOOTER_ITEM = new ScooterItem(new FabricItemSettings()
			.group(ItemGroup.TRANSPORTATION)
			.maxCount(1)
		);
		KICK_SCOOTER_CRAFTING_SERIALIZER = new SpecialRecipeSerializer<KickScooterRecipe>(KickScooterRecipe::new);


		ELECTRIC_SCOOTER_ID = new Identifier(MODID, "electric_scooter");
		ELECTRIC_SCOOTER_ENTITY = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ElectricScooterEntity::new)
			.dimensions(EntityDimensions.fixed(0.8f, 0.8f))
			.trackRangeBlocks(10)
			.build();
		ELECTRIC_SCOOTER_ITEM = new ScooterItem(new FabricItemSettings()
			.group(ItemGroup.TRANSPORTATION)
			.maxCount(1)
		);
		ELECTRIC_SCOOTER_CRAFTING_SERIALIZER = new SpecialRecipeSerializer<ElectricScooterRecipe>(ElectricScooterRecipe::new);


		CHARGING_STATION_ID = new Identifier(MODID, "charging_station");
		CHARGING_STATION_BLOCK = new DockBlock(FabricBlockSettings
			.of(Material.METAL)
			.strength(4.0f)
		);
		CHARGING_STATION_BLOCK_ENTITY = FabricBlockEntityTypeBuilder.create(DockBlockEntity::new, CHARGING_STATION_BLOCK)
			.build(null);
		
		
		TIRE_ITEM = new Item(new FabricItemSettings()
			.group(ItemGroup.TRANSPORTATION)
			.maxDamage(640)
		);
		RAW_TIRE_ITEM = new Item(new FabricItemSettings()
			.group(ItemGroup.MATERIALS)
			.maxCount(16)
		);
		
	
		PACKET_INVENTORY_CHANGED_ID = new Identifier(MODID,"invchange");
		PACKET_THROTTLE_ID = new Identifier(MODID, "esctup");
	}
}
