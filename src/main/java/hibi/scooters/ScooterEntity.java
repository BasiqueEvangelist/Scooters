package hibi.scooters;

import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

public class ScooterEntity extends Entity
implements ExtendedScreenHandlerFactory,
InventoryChangedListener {

	public static final int SLOT_FRONT_TIRE = 0;
	public static final int SLOT_REAR_TIRE = 1;
	public static final String NBT_KEY_TIRES = "Tires";

	protected boolean keyW = false, keyA = false, keyS = false, keyD = false;
	protected float yawVelocity, yawAccel;
	protected int interpTicks;
	protected double x, y, z;
	protected double oldx, oldz;
	protected float yaw;

	protected double maxSpeed;
	protected double acceleration;
	protected double brakeForce;
	protected double baseInertia;
	protected double coastInertia;
	protected double tireMult;

	protected Item item;

	public boolean frontTire = true;
	public boolean rearTire = true;

	/**
	 * <ul>
	 * <li>Slot 0 - Front Tyre</li>
	 * <li>Slot 1 - Rear Tyre</li>
	 * <li>Slot 2 - Charged Battery</li>
	 * <li>Slot 3 - Discharged Battery</li>
	 * </ul>
	 */
	public SimpleInventory items;

	public ScooterEntity(EntityType<? extends ScooterEntity> type, World world) {
		super(type, world);
		this.stepHeight = 0.6f;
		this.maxSpeed = 0.4d;
		this.acceleration = 0.02d;
		this.brakeForce = 0.93d;
		this.baseInertia = 0.98d;
		this.coastInertia = 0.986d;
		this.yawAccel = 1.2f;
		this.item = Common.KICK_SCOOTER_ITEM;
		this.items = new SimpleInventory(2);
		this.items.addListener(this);
		this.oldx = this.getX();
		this.oldz = this.getZ();
	}

	/**
	 * Used by the scooter item to spawn a scooter entity.
	 * Defaults the inventory to two unenchant undamaged tires if there's no NBT.
	 */
	public static ScooterEntity create(EntityType<? extends ScooterEntity> type, ItemUsageContext context) {
		Vec3d pos = context.getHitPos();
		ScooterEntity out = type.create(context.getWorld());
		out.setPosition(pos);
		out.prevX = pos.x;
		out.prevY = pos.y;
		out.prevZ = pos.z;
		out.setYaw(context.getPlayerYaw());
		ItemStack stack = context.getStack();
		if(!stack.hasNbt()) {
			// TODO Potatoes from using an item in creative
			out.items.setStack(SLOT_FRONT_TIRE, Common.TIRE_ITEM.getDefaultStack());
			out.items.setStack(SLOT_REAR_TIRE, Common.TIRE_ITEM.getDefaultStack());
		}
		else {
			out.readCustomDataFromNbt(stack.getNbt());
		}
		return out;
	}

	/**
	 * Creates a spawn packet from a scooter.
	 * The entity data is used to keep the visuals synchronized, and is encoded bitwise.
	 */
	@Override
	public Packet<?> createSpawnPacket() {
		int tires = 0;
		// Bit 0 is used for displaying the front tire
		if(!this.items.getStack(SLOT_FRONT_TIRE).isEmpty()) tires |= 1;
		// Bit 1 is used for displaying the rear tire
		if(!this.items.getStack(SLOT_REAR_TIRE).isEmpty()) tires |= 2;
		return new EntitySpawnS2CPacket(this, tires);
	}

	/**
	 * Populates a scooter with data from a spawn packet using the entity data.
	 * See {@code createSpawnPacket} for info on the entity data.
	 */
	@Override
	public void onSpawnPacket(EntitySpawnS2CPacket packet) {
		super.onSpawnPacket(packet);
		int tires = packet.getEntityData();
		this.frontTire = (tires & 1) == 1;
		this.rearTire = (tires & 2) == 2;
	}

	@Override
	public void tick() {
		this.yawVelocity *= 0.8f;
		super.tick();
		this.interp();

		if(this.isLogicalSideForUpdatingMovement()) {
			if(this.world.isClient) {
				this.drive();
			}
			if(!this.hasNoGravity()) {
				this.setVelocity(this.getVelocity().add(0, -0.04, 0));
			}
			if(!this.hasPassengers()){
				// Add resistance to pushing the thing around
				this.setVelocity(this.getVelocity().multiply(0.7, 1, 0.7));
			}
			this.move(MovementType.SELF, this.getVelocity());
		}
		else {
			if(!this.world.isClient && this.hasPassengers()) {
				Entity e = this.getPrimaryPassenger();
				if(e instanceof PlayerEntity && !((ServerPlayerEntity)e).isCreative()) {
					double displx = this.oldx - this.getX();
					double displz = this.oldz - this.getZ();
					double displ = displx * displx + displz * displz;
					this.wearTear(displ);
				}
				this.oldx = this.getX();
				this.oldz = this.getZ();
			}
			// Assures the scooter doesn't slide around desynchronizedly
			this.setVelocity(Vec3d.ZERO);
		}
		// Enforce scooters not being ridable underwater
		if(this.hasPassengers() && this.isSubmergedInWater()) {
			this.removeAllPassengers();
		}
		this.checkBlockCollision();

		// Generic pushing code
		List<Entity> others = this.world.getOtherEntities(this, this.getBoundingBox(), EntityPredicates.canBePushedBy(this));
		if(!others.isEmpty()) {
			for (Entity e : others) {
				this.pushAwayFrom(e);
			}
		}
	}

	// More details on the technical reference
	protected void drive() {
		double speed = this.getVelocity().multiply(1, 0, 1).length();
		double inertia = this.tireMult;
		if(this.keyS) {
			inertia *= this.brakeForce * this.baseInertia;
		}
		else if(this.keyW && speed < this.maxSpeed) {
			speed += this.acceleration * this.tireMult;
			inertia *= this.baseInertia;
		}
		else {
			inertia *= this.coastInertia;
		}
		if(this.keyA) {
			this.yawVelocity -= this.yawAccel;
		}
		if(this.keyD) {
			this.yawVelocity += this.yawAccel;
		}
		this.setYaw(this.getYaw() + this.yawVelocity);
		this.setVelocity(
			MathHelper.sin(-this.getYaw() * 0.017453293f) * speed * inertia,
			this.getVelocity().y,
			MathHelper.cos(this.getYaw() * 0.017453293f) * speed * inertia);
	}

	/**
	 * Used to smooth movement clientside.
	 */
	protected void interp() {
		// Don't do any interp if we're the commanding side
		if(this.isLogicalSideForUpdatingMovement()) {
			this.interpTicks = 0;
			this.updateTrackedPosition(this.getX(), this.getY(), this.getZ());
			return;
		}

		if(this.interpTicks <= 0) return;

		// Proper interp logic
		double xoff = this.getX() + (this.x - this.getX()) / (double)this.interpTicks;
        double yoff = this.getY() + (this.y - this.getY()) / (double)this.interpTicks;
        double zoff = this.getZ() + (this.z - this.getZ()) / (double)this.interpTicks;
        float yawoff = (float) MathHelper.wrapDegrees(this.yaw - (double)this.getYaw());
		this.setYaw(this.getYaw() + yawoff / (float)this.interpTicks);
		this.setPosition(xoff, yoff, zoff);
		this.interpTicks--;
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.interpTicks = 10;
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if(this.hasPassengers()) return ActionResult.PASS;
		if(this.isTouchingWater()) return ActionResult.PASS;

		// If the player is sneaking then open the GUI
		if(player.shouldCancelInteraction()) {
			if(!this.world.isClient)
				((ServerPlayerEntity)player).openHandledScreen(this);
			return ActionResult.success(this.world.isClient);
		}

		if(!this.world.isClient) {
			// Prevent the player from riding the scooter if there's at least a tire missing
			if(this.items.getStack(SLOT_FRONT_TIRE).isEmpty() || this.items.getStack(SLOT_REAR_TIRE).isEmpty())
				return ActionResult.PASS;
			if(player.startRiding(this)) {
				// Update the rider so it's aware of the state of the tires
				this.updateClientScootersInventory((ServerPlayerEntity)player);
				return ActionResult.CONSUME;
			}
			return ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public boolean isPushable() {
		return true;
	}
	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean collides() {
		return !this.isRemoved();
	}

	@Override
	public Box getVisibilityBoundingBox() {
		return this.getBoundingBox();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if(this.isInvulnerableTo(source)) return false;
		if(this.world.isClient || this.isRemoved()) return true;
		
		// Don't do drops if the player is in creative mode, or their inventory will get cluttered
		boolean drops = !(source.getAttacker() instanceof PlayerEntity && ((PlayerEntity)source.getAttacker()).getAbilities().creativeMode);
		if(drops && this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS))
			this.dropStack(this.asItemStack());
		
		// Dump the rider if there's any
		this.removeAllPassengers();
		this.emitGameEvent(GameEvent.ENTITY_KILLED, source.getAttacker());
		this.discard();
		return true;
	}

	protected ItemStack asItemStack() {
		NbtCompound nbt = new NbtCompound();
		ItemStack out = this.item.getDefaultStack();
		this.writeCustomDataToNbt(nbt);
		out.setNbt(nbt);
		return out;
	}

	@Override
	public double getMountedHeightOffset() {
		return 0.45d;
	}

	/**
	 * Limit and wrap the yaw of the player's head so they can't look entirely backwards.
	 * The code here effectively reflects that of {@link net.minecraft.entity.vehicle.BoatEntity BoatEntity}'s.
	 * @param passenger The current passenger.
	 */
	@Override
	public void updatePassengerPosition(Entity passenger) {
		if(!this.hasPassenger(passenger)) return;
		passenger.setYaw(passenger.getYaw() + this.yawVelocity);
		passenger.setBodyYaw(this.getYaw());
		float f = MathHelper.wrapDegrees(passenger.getYaw() - this.getYaw());
        float g = MathHelper.clamp(f, -105.0f, 105.0f);
        passenger.prevYaw += g - f;
        passenger.setYaw(passenger.getYaw() + g - f);
        passenger.setHeadYaw(passenger.getYaw());
		super.updatePassengerPosition(passenger);
	}

	@Override
	public ItemStack getPickBlockStack() {
		// Get the item WITH the nbt so it's easy to carry around in creative.
		return this.asItemStack();
	}

	@Override
	protected MoveEffect getMoveEffect() {
		return MoveEffect.ALL;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(Common.SOUND_SCOOTER_ROLLING, 1f, 0.9f + this.random.nextFloat(0.1f));
	}
	
	@Override
	public Entity getPrimaryPassenger() {
		List<Entity> list = this.getPassengerList();
		return list.isEmpty() ? null : list.get(0);
	}

	/**
	 * Update the scooter on the state of the controls of the rider.
	 * This must be called to pass the player's inputs through to the vehicle so it actually has any effect.
	 * @param forward
	 * @param back
	 * @param left
	 * @param right
	 */
	public void setInputs(boolean forward, boolean back, boolean left, boolean right) {
		this.keyW = forward;
		this.keyA = left;
		this.keyS = back;
		this.keyD = right;
	}

	@Override
	public float getEyeHeight(EntityPose pose) {
		return 0.5f;
	}

	@Override
	protected void initDataTracker() {
	}

	/**
	 * Populates {@code this} from an NBT.
	 * @param nbt The NBT data to read from.
	 */
	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if(nbt.contains(NBT_KEY_TIRES, NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList(NBT_KEY_TIRES, NbtElement.COMPOUND_TYPE);
			this.items.setStack(SLOT_FRONT_TIRE, ItemStack.fromNbt(list.getCompound(0)));
			this.items.setStack(SLOT_REAR_TIRE, ItemStack.fromNbt(list.getCompound(1)));
		}
	}

	/**
	 * <p>
	 *   The NBT for a normal, kick scooter is the following:
	 * </p>
	 * <ul>
	 *   <li>{@code Tires}, {@link ItemStack}[2]</li>
	 * </ul>
	 * <hr>
	 * @param nbt The NBT data to write into.
	 */
	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		NbtList tiresNbt = new NbtList();

		// unrolled loop
		NbtCompound compound = new NbtCompound();
		ItemStack is = this.items.getStack(SLOT_FRONT_TIRE);
		if(!is.isEmpty())
			is.writeNbt(compound);
		tiresNbt.add(compound);
		// --- //
		compound = new NbtCompound();
		is = this.items.getStack(SLOT_REAR_TIRE);
		if(!is.isEmpty())
			is.writeNbt(compound);
		tiresNbt.add(compound);

		nbt.put(NBT_KEY_TIRES, tiresNbt);
	}

	@Override
	public ScreenHandler createMenu(int sid, PlayerInventory pi, PlayerEntity pe) {
		return new ScooterScreenHandler(sid, pi, this);
	}

	@Override
	public Text getDisplayName() {
		return this.hasCustomName()? this.getCustomName() : this.getDefaultName();
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
		buf.writeInt(this.getId());
	}

	/**
	 * Update a client scooter on their inventory, called in the callback registered in {@link ClientInit}.onInitializeClient.
	 * Updating a scooter's inventory is done so the visuals of the tires, the handling characteristics, and even the charge in an electric sctooter are synchronized.
	 * @param inv The inventory decoded from the packet.
	 */
	@Override
	public void onInventoryChanged(Inventory inv) {
		if(!this.world.isClient) return;
		this.items = (SimpleInventory) inv;
		this.tireMult = 1.0;
		// Unrolled loop
		ItemStack is = inv.getStack(SLOT_FRONT_TIRE);
		this.frontTire = !is.isEmpty();
		if(!this.frontTire || is.getDamage() == is.getMaxDamage())
			this.tireMult *= 0.85;
		// --- //
		is = inv.getStack(SLOT_REAR_TIRE);
		this.rearTire = !is.isEmpty();
		if(!this.rearTire || is.getDamage() == is.getMaxDamage())
			this.tireMult *= 0.85;
	}

	/**
	 * Cause a tick's worth of wear and tear on a scooter and its components, and also the rider.
	 * @param displ The displacement in a tick, squared.
	 */
	protected void wearTear(double displ) {
		this.damageTires(displ);
		ServerPlayerEntity p = (ServerPlayerEntity) this.getPrimaryPassenger();
		p.addExhaustion(0.028f * (float)displ);
	}

	/**
	 * Cause a tick's worth of wear and damage to the tires.
	 * @param displ
	 */
	protected void damageTires(double displ) {
		if(displ < 0.001225 || displ > 25) return; // 2.5km/h 0.7 m/s (nudging) < displacement < 360 km/h 100 m/s (possible desync)
		long offsetTime = this.world.getTime() + this.getId();
		if(offsetTime % 20 != 0) return;
		if(!this.onGround) return;
	
		// Cause less damage if the scooter is on a nice surface
		boolean abrasive = this.isOnAbrasive();
		if(!abrasive && offsetTime % 40 == 0) return;
	
		// Unrolled loop
		ItemStack stack = this.items.getStack(SLOT_FRONT_TIRE);
		int damage = abrasive? 2 : 1;
		boolean popped = stack.getDamage() == stack.getMaxDamage();
		boolean markDirty = false;
		if(this.random.nextDouble() < 0.8d && stack.isOf(Common.TIRE_ITEM) && stack.getDamage() < stack.getMaxDamage())
			stack.damage(damage, this.random, null);
		if(stack.getDamage() == stack.getMaxDamage() && !popped) {
			markDirty = true;
			this.playSound(Common.SOUND_SCOOTER_TIRE_POP, 0.7f, 1.5f);
		}
		// --- //
		stack = this.items.getStack(SLOT_REAR_TIRE);
		popped = stack.getDamage() == stack.getMaxDamage();
		if(this.random.nextDouble() < 0.8d && stack.isOf(Common.TIRE_ITEM) && stack.getDamage() < stack.getMaxDamage())
			stack.damage(damage, this.random, null);
		if(stack.getDamage() == stack.getMaxDamage() && !popped) {
			markDirty = true;
			this.playSound(Common.SOUND_SCOOTER_TIRE_POP, 1.0f, 0.7f);
		}

		// Update clients if any of the tires are popped
		if(markDirty)
			this.updateClientScootersInventory();
	}

	/**
	 * Checks if the scooter is on a block that's not nice to the tires.
	 * @return {@code true} if the scooter is on a block in {@code #scooters:abrasive}, {@code false} otherwise.
	 */
	protected boolean isOnAbrasive() {
		return !this.getLandingBlockState().isAir() && this.world.getBlockState(this.getVelocityAffectingPos()).isIn(Common.ABRASIVE_BLOCKS);
	}

	/**
	 * Update <i>all</i> players within tracking range of the scooter with its inventory contents.
	 * This is done so the visuals and also the handling characteristics are synced properly between the server, and clients.
	 */
	protected void updateClientScootersInventory() {
		PacketByteBuf buf = this.inventoryChangedPacket();
		for(ServerPlayerEntity player : PlayerLookup.tracking(this)) {
			ServerPlayNetworking.send(player, Common.PACKET_INVENTORY_CHANGED_ID, buf);
		}
	}

	/**
	 * Update <i>only</i> the client of the specified player with the inventory.
	 * This is done so to prevent a bug where the tires are popped but the scooter still rides fine.
	 * @param player The player to update about the inventory.
	 */
	protected void updateClientScootersInventory(ServerPlayerEntity player) {
		PacketByteBuf buf = this.inventoryChangedPacket();
		ServerPlayNetworking.send(player, Common.PACKET_INVENTORY_CHANGED_ID, buf);
	}

	/**
	 * Create a packet with the inventory data for a scooter, but not send it.
	 * @return A {@link PacketByteBuf} with the inventory of the scooter.
	 */
	protected PacketByteBuf inventoryChangedPacket() {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeInt(this.getId());
		List<ItemStack> contents = DefaultedList.ofSize(this.items.size(), ItemStack.EMPTY);
		for (int i = 0; i < contents.size(); ++i) {
			contents.set(i, this.items.getStack(i));
		}
		buf.writeCollection(contents, PacketByteBuf::writeItemStack);
		return buf;
	}
}
