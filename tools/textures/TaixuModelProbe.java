import com.atir.molecularmanipulator.client.render.TaixuCoreEffects;
import com.atir.molecularmanipulator.client.render.ctm.MatterConnectedModel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/** Native model/face baking plus a hidden GL preview, without loading a player world.
 * The diagnostic renderer uses baked Minecraft vertices and the real effect geometry;
 * its fixed light rig is not a screenshot of a running Minecraft client.
 */
public final class TaixuModelProbe {
    static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");
    static final Path OUT = Path.of("build/taixu-palette");
    static final Map<String, Sprite> SPRITES = new HashMap<>();
    static final List<String> NAMES = List.of("taixu_creation_nexus", "taixu_jade_casing", "taixu_gilded_block",
            "taixu_pillar", "taixu_ring_track", "taixu_glass", "taixu_conduit", "taixu_collection_node",
            "taixu_genesis_core", "taixu_stabilizer", "taixu_crystal_spire", "taixu_resource_port", "taixu_jade_stairs", "taixu_jade_slab");
    static ZipFile vanilla;
    record Quad(BakedQuad baked, Sprite sprite, boolean emissive) {}

    static final class Sprite extends TextureAtlasSprite implements AutoCloseable {
        final int texture;
        final NativeImage image;
        Sprite(String name, NativeImage image) {
            super(ResourceLocation.parse("minecraft:textures/atlas/blocks.png"),
                    new SpriteContents(ResourceLocation.parse(name), new FrameSize(16, 16), image, ResourceMetadata.EMPTY), 2048, 2048, 0, 0);
            this.image = image;
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            upload(0);
        }
        void upload(int frame) {
            ByteBuffer bytes = MemoryUtil.memAlloc(16*16*4);
            try {
                int row = (frame % (image.getHeight()/16))*16;
                for (int y=0; y<16; y++) for (int x=0; x<16; x++) {
                    int rgba = image.getPixelRGBA(x, y+row);
                    bytes.put((byte)rgba).put((byte)(rgba>>>8)).put((byte)(rgba>>>16)).put((byte)(rgba>>>24));
                }
                bytes.flip();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 16, 16, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
            } finally { MemoryUtil.memFree(bytes); }
        }
        public void close() { GL11.glDeleteTextures(texture); contents().close(); }
    }

    static JsonObject modelJson(String name) throws Exception {
        if (!name.contains(":")) name = "minecraft:" + name;
        JsonObject child;
        if (name.startsWith("minecraft:")) {
            try (var in = vanilla.getInputStream(vanilla.getEntry("assets/minecraft/models/"+name.substring(10)+".json"))) {
                child = JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } else {
            child = JsonParser.parseString(Files.readString(ASSETS.resolve("models/"+name.substring(name.indexOf(':')+1)+".json"))).getAsJsonObject();
        }
        if (child.has("parent")) {
            var parent = modelJson(child.get("parent").getAsString());
            if (!child.has("elements") && parent.has("elements")) child.add("elements", parent.get("elements"));
            JsonObject textures = parent.has("textures") ? parent.getAsJsonObject("textures").deepCopy() : new JsonObject();
            if (child.has("textures")) child.getAsJsonObject("textures").entrySet().forEach(e -> textures.add(e.getKey(), e.getValue()));
            child.add("textures", textures);
            child.remove("parent");
        }
        return child;
    }

    static Sprite sprite(String name) throws Exception {
        if (SPRITES.containsKey(name)) return SPRITES.get(name);
        var path = ASSETS.resolve("textures/"+name.substring(name.indexOf(':')+1)+".png");
        try (var in = Files.newInputStream(path)) {
            var result = new Sprite(name, NativeImage.read(in));
            SPRITES.put(name, result);
            return result;
        }
    }

    static List<Quad> bake(String name, BlockModelRotation rotation) throws Exception {
        var model = BlockModel.fromString(modelJson("molecularmanipulator:block/"+name).toString());
        var bakery = new FaceBakery();
        List<Quad> quads = new ArrayList<>();
        for (var part : model.getElements()) for (var entry : part.faces.entrySet()) {
            var face = entry.getValue();
            var texture = sprite(model.getMaterial(face.texture()).texture().toString());
            var quad = bakery.bakeQuad(part.from, part.to, face, texture, entry.getKey(), rotation, part.rotation, part.shade);
            boolean emissive = face.faceData().blockLight() > 0;
            if (emissive) {
                int expected = face.faceData().blockLight() * 16 | face.faceData().skyLight() * 16 << 16;
                if (quad.getVertices()[6] != expected) throw new AssertionError("Native emissive baking failed: " + name);
                if (quad.hasAmbientOcclusion()) throw new AssertionError("Glow unexpectedly has AO: " + name);
            }
            quads.add(new Quad(quad, texture, emissive));
        }
        if (quads.isEmpty()) throw new AssertionError("Empty model " + name);
        return quads;
    }

    static void camera(int x, int y, int size) {
        GL11.glViewport(x,y,size,size);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
        GL11.glOrtho(-0.95,0.95,-0.95,0.95,-10,10);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
        GL11.glRotatef(27,1,0,0); GL11.glRotatef(225,0,1,0);
        GL11.glTranslatef(-0.5F,-0.5F,-0.5F);
    }

    static void draw(List<Quad> quads, boolean night) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST); GL11.glAlphaFunc(GL11.GL_GREATER, 0.01F);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        for (var q : quads) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, q.sprite.texture);
            float shade = q.emissive ? 1 : (night ? 0.14F : switch (q.baked.getDirection()) {
                case UP -> 1.0F; case DOWN -> 0.5F; case EAST, WEST -> 0.78F; default -> 0.9F;
            });
            GL11.glColor4f(shade,shade,shade,1);
            GL11.glBegin(GL11.GL_QUADS);
            int[] vertices = q.baked.getVertices();
            for (int i=0;i<4;i++) {
                int k=i*8;
                GL11.glTexCoord2f(q.sprite.getUOffset(Float.intBitsToFloat(vertices[k+4])),
                        q.sprite.getVOffset(Float.intBitsToFloat(vertices[k+5])));
                GL11.glVertex3f(Float.intBitsToFloat(vertices[k]),Float.intBitsToFloat(vertices[k+1]),Float.intBitsToFloat(vertices[k+2]));
            }
            GL11.glEnd();
        }
        GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    static final class EffectsConsumer implements VertexConsumer {
        float x,y,z;
        public VertexConsumer addVertex(float x,float y,float z) { this.x=x;this.y=y;this.z=z; return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) {
            GL11.glColor4f(r/255F,g/255F,b/255F,a/255F); GL11.glVertex3f(x,y,z); return this;
        }
        public VertexConsumer setUv(float u,float v) { throw new AssertionError(); }
        public VertexConsumer setUv1(int u,int v) { throw new AssertionError(); }
        public VertexConsumer setUv2(int u,int v) { throw new AssertionError(); }
        public VertexConsumer setNormal(float x,float y,float z) { throw new AssertionError(); }
    }

    static void effect(float ticks) {
        GL11.glPushMatrix(); GL11.glTranslatef(0.5F,0.5F,0.5F);
        for (boolean glow : new boolean[]{false,true}) {
            GL11.glDepthMask(!glow);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, glow ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBegin(GL11.GL_QUADS);
            TaixuCoreEffects.render(new PoseStack(), new EffectsConsumer(), ticks, 0, true, glow);
            GL11.glEnd();
        }
        GL11.glDepthMask(true); GL11.glPopMatrix();
    }

    static void save(String name, int w, int h) throws Exception {
        var buffer = MemoryUtil.memAlloc(w*h*4);
        try {
            GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
            var image = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
                int k=(y*w+x)*4;
                image.setRGB(x,h-1-y,(buffer.get(k)&255)<<16|(buffer.get(k+1)&255)<<8|(buffer.get(k+2)&255));
            }
            ImageIO.write(image,"png",OUT.resolve(name).toFile());
        } finally { MemoryUtil.memFree(buffer); }
    }

    static void connectedPreview(Map<String,List<Quad>> models) throws Exception {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        var names = List.of("taixu_jade_casing", "taixu_glass", "taixu_ring_track", "taixu_conduit", "taixu_creation_nexus", "taixu_gilded_block");
        for (int panel = 0; panel < names.size(); panel++) {
            var name = names.get(panel);
            var source = models.get(name);
            var faces = new java.util.EnumMap<net.minecraft.core.Direction,List<BakedQuad>>(net.minecraft.core.Direction.class);
            for(var dir : net.minecraft.core.Direction.values()) faces.put(dir,source.stream()
                    .map(Quad::baked).filter(q->q.getDirection()==dir).toList());
            var baked = new net.minecraft.client.resources.model.SimpleBakedModel(List.of(),faces,true,true,true,
                    source.getFirst().sprite,net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS,
                    net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY);
            var connected = new MatterConnectedModel(baked, state->baked, loc->SPRITES.get(loc.toString()));
            var state = (name.equals("taixu_glass") ? net.minecraft.world.level.block.Blocks.GLASS
                    : net.minecraft.world.level.block.Blocks.STONE).defaultBlockState();
            java.util.function.Function<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> sample =
                    p-> p.getX()>=0 && p.getX()<3 && p.getY()>=0 && p.getY()<3 && p.getZ()==0
                    ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            for (int pass=0;pass<2;pass++) {
                GL11.glViewport(panel*220,pass==0?400:40,220,300);
                GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
                GL11.glOrtho(-0.15,3.15,-0.75,3.75,-10,10);
                GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
                GL11.glRotatef(180,0,1,0); GL11.glTranslatef(-3,0,0);
                for (int x=0;x<3;x++) for (int y=0;y<3;y++) {
                    var pos = new net.minecraft.core.BlockPos(x,y,0);
                    long masks = MatterConnectedModel.connectionMasks(sample,pos,state, s->baked);
                    if (x==1 && y==1 && (masks>>>(net.minecraft.core.Direction.NORTH.ordinal()*8)&255)==0)
                        throw new AssertionError("No CTM joins on center tile: "+name);
                    var quads = pass==0 ? faces.get(net.minecraft.core.Direction.NORTH)
                            : connected.getQuads(state,net.minecraft.core.Direction.NORTH,net.minecraft.util.RandomSource.create(0),
                                    MatterConnectedModel.modelData(masks),null);
                    GL11.glPushMatrix(); GL11.glTranslatef(x,y,0);
                    draw(quads.stream().map(q->new Quad(q,SPRITES.get(q.getSprite().contents().name().toString()),q.getVertices()[6]!=0)).toList(),false);
                    GL11.glPopMatrix();
                }
            }
            // Items must remain native single-block models even if supplied world model data.
            if(connected.getQuads(null,net.minecraft.core.Direction.NORTH,net.minecraft.util.RandomSource.create(0),
                    MatterConnectedModel.modelData(255L << (net.minecraft.core.Direction.NORTH.ordinal()*8)),null).size()
                    != faces.get(net.minecraft.core.Direction.NORTH).size()) throw new AssertionError("Item model changed");
        }
        save("taixu-connected-raw.png",1320,740);
        System.out.println("TAIXU_CTM_NATIVE_PASS panels=6 worldMasks=true itemsUnchanged=true");
    }

    static void structurePreview() throws Exception {
        var cache = new HashMap<String, List<Quad>>();
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        for (int view = 0; view < 2; view++) {
            GL11.glViewport(view * 700, 0, 700, 800);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
            GL11.glOrtho(-75, 75, -86, 86, -500, 500);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL11.glRotatef(18, 1, 0, 0); GL11.glRotatef(215, 0, 1, 0); GL11.glTranslatef(0, -64, 0);
            for (var part : com.atir.molecularmanipulator.blockentity.TaixuStructure.parts()) {
                String block = NAMES.get(part.type().ordinal());
                String cacheKey = block + "/" + part.direction() + "/" + part.upper();
                var quads = cache.get(cacheKey);
                if (quads == null) {
                    var properties = Map.of("facing", part.direction().getName(), "axis", part.direction().getAxis().getName(),
                            "half", part.upper() ? "top" : "bottom", "type", part.upper() ? "top" : "bottom", "shape", "straight");
                    var variants = JsonParser.parseString(Files.readString(ASSETS.resolve("blockstates/" + block + ".json")))
                            .getAsJsonObject().getAsJsonObject("variants");
                    for (var entry : variants.entrySet()) {
                        boolean matches = true;
                        if (!entry.getKey().isEmpty()) for (var property : entry.getKey().split(",")) {
                            var pair = property.split("=");
                            if (!pair[1].equals(properties.get(pair[0]))) matches = false;
                        }
                        if (!matches) continue;
                        var v = entry.getValue().getAsJsonObject();
                        quads = bake(v.get("model").getAsString().replace("molecularmanipulator:block/", ""),
                                BlockModelRotation.by(v.has("x") ? v.get("x").getAsInt() : 0, v.has("y") ? v.get("y").getAsInt() : 0));
                        cache.put(cacheKey, quads); break;
                    }
                    if (quads == null) throw new AssertionError("No native model for blueprint part " + cacheKey);
                }
                GL11.glPushMatrix(); GL11.glTranslatef(part.x(), part.y(), part.z());
                draw(quads, view == 1); GL11.glPopMatrix();
            }
            GL11.glPushMatrix(); GL11.glTranslatef(.5F, 64.5F, .5F);
            GL11.glDepthMask(false); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); GL11.glBegin(GL11.GL_QUADS);
            com.atir.molecularmanipulator.client.TaixuRenderer.renderEffects(new PoseStack(), new EffectsConsumer(), 120, true);
            GL11.glEnd(); GL11.glDepthMask(true); GL11.glPopMatrix();
        }
        save("taixu-structure-native.png", 1400, 800);
        System.out.println("TAIXU_STRUCTURE_NATIVE_PASS parts=" + com.atir.molecularmanipulator.blockentity.TaixuStructure.parts().size());
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(1400, 800, "Taixu asset verification", 0, 0);
        if (window == 0) throw new AssertionError("Hidden context unavailable");
        try (var resources = new ZipFile("build/moddev/artifacts/neoforge-21.1.220-client-extra-aka-minecraft-resources.jar")) {
            vanilla = resources;
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities(); RenderSystem.initRenderThread();
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL); GL11.glEnable(GL11.GL_BLEND);
            GL11.glClearColor(0.045F,0.07F,0.11F,1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            Map<String,List<Quad>> models = new HashMap<>();
            for (int i=0;i<NAMES.size();i++) {
                var name = NAMES.get(i);
                var quads = bake(name,BlockModelRotation.X0_Y0); models.put(name,quads);
                camera((i%7)*200,600-(i/7)*200,200); draw(quads,false);
                if (name.equals("taixu_genesis_core")) effect(0);
                camera((i%7)*200,200-(i/7)*200,200); draw(quads,true);
                if (name.equals("taixu_genesis_core")) effect(0);
            }
            save("taixu-models-raw.png",1400,800);
            connectedPreview(models);
            structurePreview();
            int variants = 0;
            try(var files=Files.list(ASSETS.resolve("blockstates"))) {
                for (var file : files.filter(p->p.getFileName().toString().startsWith("taixu_")).toList()) {
                    var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("variants");
                    for(var entry : json.entrySet()) {
                        var v=entry.getValue().getAsJsonObject();
                        String name=v.get("model").getAsString().replace("molecularmanipulator:block/","");
                        bake(name,BlockModelRotation.by(v.has("x")?v.get("x").getAsInt():0,v.has("y")?v.get("y").getAsInt():0));
                        variants++;
                    }
                }
            }
            for(int frame=0;frame<48;frame++) {
                SPRITES.values().forEach(s->s.upload(0));
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
                camera(0,0,480); draw(models.get("taixu_genesis_core"),false); effect(frame*5);
                save(String.format("core-%02d.png",frame),480,480);
            }
            if(GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error");
            System.out.println("TAIXU_MODELS_PASS blocks="+models.size()+" variants="+variants+" textures="+SPRITES.size()+" nativeFaceBaking=true");
        } finally {
            SPRITES.values().forEach(Sprite::close);
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }
}
