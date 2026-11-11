package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.StarOrreryCoreStructure;
import com.atir.molecularmanipulator.starcomputation.StarOrreryFoundation;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import guideme.compiler.Frontmatter;
import guideme.compiler.IdUtils;
import guideme.compiler.PageCompiler;
import guideme.compiler.ParsedGuidePage;
import guideme.libs.mdast.MdAstYamlFrontmatter;
import guideme.libs.mdast.mdx.model.MdxJsxAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstCode;
import guideme.libs.mdast.model.MdAstHeading;
import guideme.libs.mdast.model.MdAstImage;
import guideme.libs.mdast.model.MdAstInlineCode;
import guideme.libs.mdast.model.MdAstLink;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.libs.mdast.model.MdAstText;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Source/AST and resource-contract regressions, NOT a GuideME runtime rendering test.
 * Uses GuideME 21.1.1's real MDX parser and ID resolver. Standalone JUnit does not register
 * mod blocks/items or run scene/recipe compilers, texture upload, layout or rendering;
 * those still need the isolated in-game client check. Mod IDs are checked against their
 * own resource namespace (dependency JARs for AE2/ExtendedAE), never vanilla fallbacks.
 */
class GuideDocumentationTest {
    private static final String MOD = "molecularmanipulator";
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path GUIDE = RESOURCES.resolve("assets/" + MOD + "/ae2guide");
    private static final String INDEX = "omnisequence-index.md";
    private static final List<String> LANGUAGES = List.of("en_us", "zh_cn");
    // Explicit routes prevent deleting/renaming the same page in both languages from going unnoticed.
    private static final List<String> PAGE_PATHS = Stream.concat(Stream.of(INDEX), Stream.of(
            "assembler_matrix_sequence_rewrite_core", "black_and_white_holes", "building_access_terminal",
            "compact_singularity_hub", "event_horizon_singularity_hub", "folded_buildings", "simulation_lantern",
            "matter_fabrication_pattern_assembly", "matter_fabrication_ports", "matter_fabrication_research",
            "matter_fabrication_well", "molecular_auto_crafter", "molecular_sequence_rewrite_array", "nomai_materials",
            "omni_computation_core", "sequence_array_controller", "singularity_matter_duplication",
            "singularity_resource_collection", "star_computation_engine", "star_marrow_crystals",
            "star_sea_calculation_mainframe", "star_signal_flow", "transfinite_compute_nexus")
            .map(name -> "items-blocks-machines/" + name + ".md")).toList();
    private static final Set<String> NAVIGATION_ONLY = Set.of(INDEX,
            "items-blocks-machines/matter_fabrication_research.md",
            "items-blocks-machines/singularity_resource_collection.md");
    private static final List<String> DIAGRAMS = List.of("first-steps", "research-route", "me-crafting", "singularity-copy");
    private static final String CORE_LAYERS = "assets/structures/orrery-core-layers.snbt";
    private static final String FOUNDATION = "assets/structures/orrery-foundation.snbt";
    private static final String ORRERY_PAGE = "items-blocks-machines/star_computation_engine.md";
    private static final Set<String> MARKDOWN_TYPES = Set.of("root", "yamlFrontmatter", "heading", "paragraph", "text",
            "list", "listItem", "strong", "emphasis", "delete", "inlineCode", "code", "blockquote", "break",
            "thematicBreak", "link", "image", "table", "tableRow", "tableCell");
    // Technical identifiers, not ordinary prose such as "foundation", "quantum" or "prerequisites".
    private static final Pattern INTERNAL_RESEARCH = Pattern.compile(
            "(?i)(?<![a-z0-9_])(?:StarComputationRecipes|StarComputationResearchData|LEGACY_NODE_IDS|"
                    + "unlock_mask|unlocked_nodes|prerequisite_mask|prerequisiteMask|required_level|requiredLevel|"
                    + "information_cost|informationCost|recipe_ids|research_id|research_ids|parallel_multiplier|"
                    + "ae_foundation|eae_materials|quantum_materials|nomai_materials|star_marrow_materials|"
                    + "star_marrow_crystallization|sequence_array|omni_computation|event_horizon|stellar_fabrication|"
                    + "transfinite_fabrication|gui\\.molecularmanipulator\\.star_computation\\.node\\.[a-z_]+|"
                    + "molecularmanipulator:(?:foundation|material|quantum|terminal|transfinite))(?![a-z0-9_])");
    private static final Pattern LEGACY_NODE_CODE = Pattern.compile("foundation|material|quantum|terminal|transfinite");

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private record Page(String language, String path) {
        ResourceLocation id() { return localId(path); }
        Path file() { return GUIDE.resolve(language.equals("en_us") ? path : "_" + language + "/" + path); }
        @Override public String toString() { return language + "/" + path; }
    }

    private static Stream<Page> pages() {
        return LANGUAGES.stream().flatMap(language -> PAGE_PATHS.stream().map(path -> new Page(language, path)));
    }

    private static Stream<Arguments> diagrams() {
        return LANGUAGES.stream().flatMap(language -> DIAGRAMS.stream().map(name -> Arguments.of(language, name)));
    }

    @Test
    void allFortyEightExpectedRoutesExistWithoutUnpairedOrUnexpectedPages() throws Exception {
        var expected = new TreeSet<String>();
        pages().forEach(page -> expected.add(relative(page.file())));
        var actual = new TreeSet<String>();
        try (var files = Files.walk(GUIDE)) {
            files.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".md"))
                    .forEach(file -> actual.add(relative(file)));
        }
        assertEquals(48, expected.size());
        assertEquals(expected, actual, "Both languages must retain every guide entry point");
    }

    @Test
    void hotkeyEntryPointsHaveOneOwnerPerLanguage() {
        for (String language : LANGUAGES) {
            var owners = new HashMap<ResourceLocation, String>();
            for (String path : PAGE_PATHS) {
                var page = new Page(language, path);
                var parsed = assertDoesNotThrow(() -> parse(page), page.toString());
                for (var id : itemIds(parsed)) {
                    assertNull(owners.put(id, path), language + " duplicate hotkey page for " + id);
                }
            }
        }
    }

    @ParameterizedTest(name = "parse and references: {0}")
    @MethodSource("pages")
    void realMdxParseFrontmatterAndEveryReferenceAreValid(Page page) throws Exception {
        var parsed = parse(page);
        validateFrontmatter(page, parsed);
        audit(page, parsed);
        assertNavigationReachesIndex(page, parsed);
    }

    @ParameterizedTest(name = "bilingual entry points and figures: {0}")
    @MethodSource("pagePaths")
    void translationsHaveTheSameCanonicalEntryPointsAndIllustrations(String path) throws Exception {
        var en = new Page("en_us", path);
        var zh = new Page("zh_cn", path);
        var english = parse(en);
        var chinese = parse(zh);
        validateFrontmatter(en, english);
        validateFrontmatter(zh, chinese);
        assertEquals(english.getId(), chinese.getId(), "Language directories are not part of a page ID");
        var a = english.getFrontmatter().navigationEntry();
        var b = chinese.getFrontmatter().navigationEntry();
        assertEquals(a.parent(), b.parent(), "Translated navigation parent: " + path);
        assertEquals(a.iconItemId(), b.iconItemId(), "Translated navigation icon: " + path);
        assertEquals(a.position(), b.position(), "Translated navigation order: " + path);
        assertEquals(itemIds(english), itemIds(chinese), "Translated hotkey/item entry points: " + path);
        var enReferences = audit(en, english);
        var zhReferences = audit(zh, chinese);
        assertFalse(enReferences.figures.isEmpty(), "Every page must retain its illustrations: " + path);
        assertEquals(enReferences.figures, zhReferences.figures,
                "Figure/recipe/structure IDs differ (only localized image suffixes may differ): " + path);
    }

    private static Stream<String> pagePaths() { return PAGE_PATHS.stream(); }

    @ParameterizedTest(name = "published diagram: {0}/{1}")
    @MethodSource("diagrams")
    void allEightPublishedDiagramsAreDecodableAndReferencedInTheirLanguage(String language, String name) throws Exception {
        var id = localId("assets/diagrams/" + name + "-" + language + ".png");
        assertImage(id, "published " + id);
        var referenced = new HashSet<ResourceLocation>();
        for (String path : PAGE_PATHS) {
            var page = new Page(language, path);
            referenced.addAll(audit(page, parse(page)).images);
        }
        assertTrue(referenced.contains(id), "Orphan diagram: " + id + " is not used by any " + language + " page");
    }

    @Test
    void noGuideImageOrStructureIsAnUnreferencedAsset() throws Exception {
        var references = new HashSet<ResourceLocation>();
        for (var page : pages().toList()) {
            var audit = audit(page, parse(page));
            references.addAll(audit.images);
            references.addAll(audit.structures);
        }
        var assets = GUIDE.resolve("assets");
        assertTrue(Files.isDirectory(assets), "Missing formal guide asset directory: " + assets);
        try (var files = Files.walk(assets)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                var id = localId(relative(file));
                assertTrue(id.getPath().endsWith(".png") || id.getPath().endsWith(".snbt") || id.getPath().endsWith(".nbt"),
                        "Unreviewed guide asset type: " + file);
                assertTrue(references.contains(id), "Orphan guide asset: " + file);
            }
        }
    }

    @Test
    void coreLayerDiagramMatchesEveryRealCoreMemberAtItsExplodedPosition() throws Exception {
        var actual = readStructure(localId(CORE_LAYERS), CORE_LAYERS);
        assertEquals(new BlockPos(11, 1, 3), actual.size);
        var expected = new HashMap<BlockPos, String>();
        expected.put(new BlockPos(5, 0, 1), MOD + ":star_computation_engine_core");
        for (var member : StarOrreryCoreStructure.members()) {
            var offset = member.offset();
            // Original bottom/middle/top Y becomes X strips 0..2 / 4..6 / 8..10.
            var pos = new BlockPos(offset.getX() + 1 + 4 * (offset.getY() + 1), 0, offset.getZ() + 1);
            String block = switch (member.material()) {
                case CASING -> "star_computation_engine_casing";
                case OBSERVATION_PORT -> "star_computation_observation_port";
            };
            assertNull(expected.put(pos, MOD + ":" + block), "Duplicate production core member: " + pos);
        }
        assertEquals(27, expected.size());
        assertBlocksAtEveryPosition(expected, actual, CORE_LAYERS);
        for (int start : List.of(0, 4, 8)) {
            assertEquals(9, actual.blocks.keySet().stream().filter(p -> p.getX() >= start && p.getX() <= start + 2).count());
        }
        assertBothLanguagesImport(CORE_LAYERS);
    }

    @Test
    void foundationDiagramMatchesEveryRealFoundationMemberIncludingOpenSpacesAndMaterials() throws Exception {
        var actual = readStructure(localId(FOUNDATION), FOUNDATION);
        assertEquals(new BlockPos(37, 4, 37), actual.size);
        var expected = new HashMap<BlockPos, String>();
        for (var member : StarOrreryFoundation.members()) {
            var pos = member.offset().offset(18, 37, 18);
            String block = switch (member.material()) {
                case PLINTH -> "star_orrery_engraved_plinth";
                case TRIM -> "star_orrery_gilded_plinth";
            };
            assertNull(expected.put(pos, MOD + ":" + block), "Duplicate production foundation member: " + pos);
        }
        assertEquals(520, expected.values().stream().filter(id -> id.endsWith(":star_orrery_engraved_plinth")).count());
        assertEquals(408, expected.values().stream().filter(id -> id.endsWith(":star_orrery_gilded_plinth")).count());
        assertBlocksAtEveryPosition(expected, actual, FOUNDATION);
        assertBothLanguagesImport(FOUNDATION);
    }

    private static void assertBlocksAtEveryPosition(Map<BlockPos, String> expected, Structure actual, String context) {
        // Check the WHOLE volume, including all gaps/shaft cells; counts alone miss swaps, shifts and filled holes.
        for (var pos : BlockPos.betweenClosed(BlockPos.ZERO, actual.size.offset(-1, -1, -1))) {
            var state = actual.blocks.get(pos);
            assertEquals(expected.get(pos), state == null ? null : state.getString("Name"), context + " at " + pos);
            if (state != null) {
                var props = state.getCompound("Properties");
                assertNotEquals("true", props.getString("formed"), context + " must show physical blocks at " + pos);
                assertNotEquals("true", props.getString("invisible"), context + " invisible member at " + pos);
            }
        }
        assertEquals(expected.keySet(), actual.blocks.keySet(), context + " exact occupied positions");
    }

    private static void assertBothLanguagesImport(String path) throws Exception {
        for (String language : LANGUAGES) {
            var page = new Page(language, ORRERY_PAGE);
            assertTrue(audit(page, parse(page)).structures.contains(localId(path)), page + " must import " + path);
        }
    }

    private static ParsedGuidePage parse(Page page) throws Exception {
        assertTrue(Files.isRegularFile(page.file()), "Missing guide entry: " + page.file());
        return parse(page, Files.readString(page.file(), StandardCharsets.UTF_8));
    }

    private static ParsedGuidePage parse(Page page, String source) {
        // Deliberately pass a CANONICAL ID and a separate language, as ResourceGuide does.
        var parsed = PageCompiler.parse("guide-documentation-test", page.language, page.id(), source);
        assertEquals(page.id(), parsed.getId());
        assertEquals(page.language, parsed.getLanguage());
        assertFalse(nodes(parsed.getAstRoot()).stream().anyMatch(node -> node instanceof MdAstHeading heading
                && heading.toText().equals("PARSING ERROR")), page + ": " + parsed.getAstRoot().toText());
        return parsed;
    }

    private static void validateFrontmatter(Page page, ParsedGuidePage parsed) throws Exception {
        var rootChildren = parsed.getAstRoot().children();
        var yamlNodes = nodes(parsed.getAstRoot()).stream().filter(MdAstYamlFrontmatter.class::isInstance).toList();
        assertEquals(1, yamlNodes.size(), page + " must have exactly one YAML frontmatter");
        assertSame(rootChildren.getFirst(), yamlNodes.getFirst(), page + " frontmatter must be first");
        var yaml = (MdAstYamlFrontmatter) yamlNodes.getFirst();
        // parse() logs frontmatter failures and substitutes an empty value; explicitly fail that fallback.
        var strict = assertDoesNotThrow(() -> Frontmatter.parse(page.id(), yaml.value), page + " invalid frontmatter");
        assertEquals(strict, parsed.getFrontmatter(), page + " frontmatter silently fell back");
        var nav = strict.navigationEntry();
        assertNotNull(nav, page + " missing navigation entry");
        assertFalse(nav.title().isBlank(), page + " blank navigation title");
        assertPlayerText(nav.title(), page.toString());
        assertNotNull(nav.iconItemId(), page + " missing navigation icon");
        assertItem(nav.iconItemId(), page + " navigation icon");
        if (page.path.equals(INDEX)) {
            assertNull(nav.parent(), page + " root must be a navigation entry point");
        } else {
            assertNotNull(nav.parent(), page + " missing navigation parent");
            assertPageExists(nav.parent(), page.language, page + " navigation parent");
        }
        var ids = itemIds(parsed);
        assertEquals(NAVIGATION_ONLY.contains(page.path), ids.isEmpty(), page + " lost its hotkey/item entry points");
        for (var id : ids) assertItem(id, page + " item_ids");
        if (!ids.isEmpty()) assertTrue(ids.contains(nav.iconItemId()), page + " primary item is not indexed");
    }

    private static Set<ResourceLocation> itemIds(ParsedGuidePage page) {
        var raw = page.getFrontmatter().additionalProperties().get("item_ids");
        if (raw == null) return Set.of();
        assertInstanceOf(List.class, raw, page + " item_ids must be a list");
        var result = new TreeSet<ResourceLocation>();
        for (Object value : (List<?>) raw) {
            assertInstanceOf(String.class, value, page + " item_ids must contain strings");
            assertTrue(result.add(resolveId((String) value, page + " item_ids")), page + " duplicate item_id: " + value);
        }
        return result;
    }

    private static void assertNavigationReachesIndex(Page page, ParsedGuidePage parsed) throws Exception {
        var visited = new HashSet<ResourceLocation>();
        while (!parsed.getId().equals(localId(INDEX))) {
            assertTrue(visited.add(parsed.getId()), page + " navigation cycle: " + visited);
            var nav = parsed.getFrontmatter().navigationEntry();
            assertNotNull(nav, page + " parent lacks navigation");
            assertNotNull(nav.parent(), page + " disconnected navigation subtree at " + parsed.getId());
            assertEquals(MOD, nav.parent().getNamespace(), page + " entry must be reachable from this guide's index");
            assertPageExists(nav.parent(), page.language, page + " navigation ancestor");
            parsed = parse(new Page(page.language, nav.parent().getPath()));
        }
    }

    private static final class References {
        final Set<ResourceLocation> images = new HashSet<>();
        final Set<ResourceLocation> structures = new HashSet<>();
        final Map<String, Integer> figures = new TreeMap<>();
        final ParsedGuidePage parsed;
        References(ParsedGuidePage parsed) { this.parsed = parsed; }
        void figure(String kind, ResourceLocation id) {
            String canonical = id.toString().replaceAll("-(?:en_us|zh_cn)(?=\\.png$)", "-{language}");
            figures.merge(kind + " " + canonical, 1, Integer::sum);
        }
    }

    private static References audit(Page page, ParsedGuidePage parsed) throws Exception {
        var references = new References(parsed);
        auditNode(page, parsed.getAstRoot(), null, references);
        return references;
    }

    private static void auditNode(Page page, MdAstNode node, String parentTag, References references) throws Exception {
        String context = page + (node.position() == null ? "" : " (" + node.position() + ")");
        String childParent = null;
        if (node instanceof MdxJsxElementFields el) {
            var tag = el.name();
            assertNotNull(tag, context + " anonymous MDX fragments are not supported by the guide compiler");
            for (var attribute : el.attributes()) {
                assertInstanceOf(MdxJsxAttribute.class, attribute, context + " spread attributes cannot be audited");
                var attr = (MdxJsxAttribute) attribute;
                if (attr.hasStringValue() && Set.of("title", "fallbackText").contains(attr.name)) {
                    assertPlayerText(attr.getStringValue(), context);
                }
            }
            switch (tag) {
                case "BlockImage", "ItemImage", "ItemIcon", "RecipeFor" -> {
                    var id = resolveId(requiredString(el, "id", context), context);
                    if (tag.equals("BlockImage")) assertBlock(id, context); else assertItem(id, context);
                    if (tag.equals("ItemIcon")) assertEquals("ItemGrid", parentTag, context + " ItemIcon outside ItemGrid");
                    references.figure(tag, id);
                }
                case "ImportStructure" -> {
                    assertEquals("GameScene", parentTag, context + " ImportStructure outside GameScene");
                    var id = resolveLink(requiredString(el, "src", context), page.id(), context);
                    readStructure(id, context);
                    references.structures.add(id);
                    references.figure(tag, id);
                }
                case "FloatingImage" -> imageReference(page, requiredString(el, "src", context), context, references);
                case "a" -> {
                    if (el.hasAttribute("href")) assertLink(page, requiredString(el, "href", context), context, references.parsed);
                }
                case "IsometricCamera" -> assertEquals("GameScene", parentTag, context + " camera outside GameScene");
                case "Row", "SubPages" -> { }
                case "ItemGrid" -> {
                    for (var child : el.children()) {
                        assertTrue(child instanceof MdxJsxElementFields item && "ItemIcon".equals(item.name()),
                                context + " ItemGrid only accepts direct ItemIcon children");
                    }
                }
                case "GameScene" -> {
                    for (var child : el.children()) {
                        assertTrue(child instanceof MdxJsxElementFields scene
                                        && Set.of("ImportStructure", "IsometricCamera").contains(scene.name()),
                                context + " unreviewed/unsupported scene child: " + child.type());
                    }
                }
                // This is intentionally fail-closed. Audit new GuideME tags' resource semantics before adding them.
                default -> fail(context + " unknown or unaudited GuideME tag <" + tag + ">");
            }
            childParent = tag;
        } else {
            assertTrue(MARKDOWN_TYPES.contains(node.type()), context + " unsupported/unaudited Markdown node: " + node.type());
            if (node instanceof MdAstLink link) assertLink(page, link.url, context, references.parsed);
            if (node instanceof MdAstImage image) imageReference(page, image.url, context, references);
            if (node instanceof MdAstText text) assertPlayerText(text.value, context);
            if (node instanceof MdAstInlineCode code) {
                assertPlayerText(code.value, context);
                assertFalse(LEGACY_NODE_CODE.matcher(code.value).matches(), context + " raw legacy star-research node");
            }
            if (node instanceof MdAstCode code) assertPlayerText(code.value, context);
        }
        if (node instanceof MdAstParent<?> parent) {
            for (var child : parent.children()) auditNode(page, (MdAstNode) child, childParent, references);
        }
    }

    private static void assertPlayerText(String text, String context) {
        var match = INTERNAL_RESEARCH.matcher(text);
        assertFalse(match.find(), () -> context + " exposes a research implementation identifier: " + match.group());
    }

    private static String requiredString(MdxJsxElementFields element, String name, String context) {
        var attr = element.getAttribute(name);
        assertNotNull(attr, context + " <" + element.name() + "> missing " + name);
        assertTrue(attr.hasStringValue(), context + " " + name + " must be a literal string, not an expression");
        assertFalse(attr.getStringValue().isBlank(), context + " empty " + name);
        return attr.getStringValue();
    }

    private static void assertLink(Page page, String href, String context, ParsedGuidePage current) throws Exception {
        assertNotNull(href, context + " null link");
        assertFalse(href.isBlank(), context + " empty link");
        if (href.startsWith("https://") || href.startsWith("http://")) {
            var uri = assertDoesNotThrow(() -> java.net.URI.create(href), context + " malformed external link");
            assertNotNull(uri.getHost(), context + " external link has no host");
            return; // No HTTP requests: external availability is not a reproducible unit-test contract.
        }
        String[] parts = href.split("#", 2);
        String target = parts[0];
        var id = target.isEmpty() ? page.id() : resolveLink(target, page.id(), context);
        assertPageExists(id, page.language, context + " link " + href);
        if (parts.length == 2 && !parts[1].isEmpty()) {
            var destination = current;
            if (!id.equals(current.getId())) {
                String source = id.getNamespace().equals(MOD)
                        ? Files.readString(new Page(page.language, id.getPath()).file(), StandardCharsets.UTF_8)
                        : new String(resource("assets/" + id.getNamespace() + "/ae2guide/" + id.getPath(), context), StandardCharsets.UTF_8);
                destination = PageCompiler.parse("guide-documentation-test", page.language, id, source);
            }
            String anchor = parts[1];
            // AnchorIndexer uses lowercase + whitespace replacement, NOT GitHub's punctuation-stripping slugger.
            assertTrue(nodes(destination.getAstRoot()).stream().anyMatch(node ->
                    node instanceof MdAstHeading heading
                            && heading.toText().toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "-").equals(anchor)
                            || node instanceof MdxJsxElementFields el && "a".equals(el.name())
                            && anchor.equals(el.getAttributeString("name", ""))), context + " missing page anchor: " + href);
        }
    }

    private static void imageReference(Page page, String href, String context, References references) throws Exception {
        var id = resolveLink(href, page.id(), context);
        if (id.getPath().matches(".*-(en_us|zh_cn)\\.png")) {
            assertTrue(id.getPath().endsWith("-" + page.language + ".png"), context + " wrong image language: " + id);
        }
        assertImage(id, context);
        references.images.add(id);
        references.figure("image", id);
    }

    private static void assertImage(ResourceLocation id, String context) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(asset(id, context)));
        assertNotNull(image, context + " undecodable image " + id);
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0, context + " empty image " + id);
    }

    private static ResourceLocation localId(String path) { return ResourceLocation.fromNamespaceAndPath(MOD, path); }

    private static ResourceLocation resolveId(String value, String context) {
        assertNotNull(value, context + " null ID");
        assertFalse(value.isBlank(), context + " empty ID");
        return assertDoesNotThrow(() -> IdUtils.resolveId(value.trim(), MOD), context + " invalid ID: " + value);
    }

    private static ResourceLocation resolveLink(String value, ResourceLocation anchor, String context) {
        assertNotNull(value, context + " null resource link");
        assertFalse(value.isBlank(), context + " empty resource link");
        var id = assertDoesNotThrow(() -> IdUtils.resolveLink(value, anchor), context + " invalid resource link: " + value);
        assertCanonical(id, context);
        return id;
    }

    private static void assertCanonical(ResourceLocation id, String context) {
        assertFalse(Stream.of(id.getPath().split("/")).anyMatch(part -> part.startsWith("_") || part.equals("..") || part.equals(".")),
                context + " use canonical guide IDs, not language directories or unresolved traversal: " + id);
    }

    private static void assertPageExists(ResourceLocation id, String language, String context) throws Exception {
        assertCanonical(id, context);
        assertTrue(id.getPath().endsWith(".md"), context + " is not a page ID: " + id);
        if (id.getNamespace().equals(MOD)) {
            assertTrue(Files.isRegularFile(new Page(language, id.getPath()).file()), context + " missing " + language + " page: " + id);
        } else {
            resource("assets/" + id.getNamespace() + "/ae2guide/" + id.getPath(), context);
        }
    }

    private static void assertItem(ResourceLocation id, String context) throws Exception {
        if (id.getNamespace().equals("minecraft")) {
            assertTrue(BuiltInRegistries.ITEM.containsKey(id), context + " unknown vanilla item " + id);
        } else {
            resource("assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json", context + " item " + id);
        }
    }

    private static void assertBlock(ResourceLocation id, String context) throws Exception {
        if (id.getNamespace().equals("minecraft")) {
            assertTrue(BuiltInRegistries.BLOCK.containsKey(id), context + " unknown vanilla block " + id);
        } else {
            resource("assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json", context + " block " + id);
        }
    }

    private static byte[] asset(ResourceLocation id, String context) throws Exception {
        assertCanonical(id, context);
        return resource("assets/" + id.getNamespace() + "/ae2guide/" + id.getPath(), context);
    }

    private static byte[] resource(String path, String context) throws Exception {
        // Local sources win over stale processResources output. Other mods are looked up in the real test classpath.
        if (path.startsWith("assets/" + MOD + "/")) {
            var file = RESOURCES.resolve(path);
            assertTrue(Files.isRegularFile(file), context + " missing local resource: " + file);
            return Files.readAllBytes(file);
        }
        try (var in = GuideDocumentationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, context + " missing dependency resource: " + path);
            return in.readAllBytes();
        }
    }

    private record Structure(BlockPos size, Map<BlockPos, CompoundTag> blocks) { }

    private static Structure readStructure(ResourceLocation id, String context) throws Exception {
        return decodeStructure(id, asset(id, context), context);
    }

    private static Structure decodeStructure(ResourceLocation id, byte[] bytes, String context) throws Exception {
        CompoundTag nbt;
        if (id.getPath().endsWith(".snbt")) {
            // Exactly the conversion used by GuideME's ImportStructureElementCompiler, not TagParser alone.
            nbt = NbtUtils.snbtToStructure(new String(bytes, StandardCharsets.UTF_8));
        } else {
            assertTrue(id.getPath().endsWith(".nbt"), context + " unsupported structure type: " + id);
            nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        }
        var sizeTag = nbt.getList("size", Tag.TAG_INT);
        assertEquals(3, sizeTag.size(), context + " missing/invalid structure size");
        var size = new BlockPos(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        assertTrue(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0, context + " empty structure bounds");
        var palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        assertFalse(palette.isEmpty(), context + " missing structure palette");
        for (var entry : palette) {
            var state = (CompoundTag) entry;
            assertTrue(state.contains("Name", Tag.TAG_STRING), context + " palette state has no block ID");
            assertBlock(resolveId(state.getString("Name"), context), context + " palette");
        }
        assertTrue(nbt.contains("blocks", Tag.TAG_LIST), context + " missing block list");
        var blocks = new HashMap<BlockPos, CompoundTag>();
        for (var entry : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) entry;
            var coords = block.getList("pos", Tag.TAG_INT);
            assertEquals(3, coords.size(), context + " missing/invalid block position");
            var pos = new BlockPos(coords.getInt(0), coords.getInt(1), coords.getInt(2));
            assertTrue(pos.getX() >= 0 && pos.getX() < size.getX() && pos.getY() >= 0 && pos.getY() < size.getY()
                    && pos.getZ() >= 0 && pos.getZ() < size.getZ(), context + " out of bounds: " + pos);
            assertTrue(block.contains("state", Tag.TAG_INT), context + " missing palette index at " + pos);
            int index = block.getInt("state");
            assertTrue(index >= 0 && index < palette.size(), context + " bad palette index " + index + " at " + pos);
            assertNull(blocks.put(pos, palette.getCompound(index)), context + " duplicate block at " + pos);
        }
        assertFalse(blocks.isEmpty(), context + " empty imported structure");
        assertTrue(nbt.getList("entities", Tag.TAG_COMPOUND).isEmpty(), context + " diagrams must not depend on server-only entities");
        return new Structure(size, blocks);
    }

    private static List<MdAstNode> nodes(MdAstNode root) {
        var result = new ArrayList<MdAstNode>();
        result.add(root);
        if (root instanceof MdAstParent<?> parent) {
            for (var child : parent.children()) result.addAll(nodes((MdAstNode) child));
        }
        return result;
    }

    private static String relative(Path path) { return GUIDE.relativize(path).toString().replace('\\', '/'); }

    @ParameterizedTest(name = "fail closed: {0}")
    @ValueSource(strings = {
            "<UnknownGuideTag />",
            "<img src=\"assets/missing.png\" />",
            "<ItemImage />",
            "<ItemImage id={unknown} />",
            "<ItemImage id=\"molecularmanipulator:missing_guide_test_item\" />",
            "<BlockImage id=\"molecularmanipulator:missing_guide_test_block\" />",
            "<ItemImage id=\"ae2:missing_guide_test_item\" />",
            "<ItemImage id=\"extendedae:missing_guide_test_item\" />",
            "<ItemImage id=\"unknown_guide_test_mod:missing_item\" />",
            "<ItemImage id=\"minecraft:missing_guide_test_item\" />",
            "<ItemImage id=\"bad namespace:bad id\" />",
            "<ItemIcon id=\"minecraft:diamond\" />",
            "[missing](missing-guide-test-page.md)",
            "[localized](_zh_cn/omnisequence-index.md)",
            "[missing anchor](#missing-guide-test-anchor)",
            "![missing](assets/diagrams/missing-guide-test.png)",
            "<GameScene>\n  <ImportStructure src=\"assets/structures/missing-guide-test.snbt\" />\n</GameScene>",
            "Use `unlock_mask` and `required_level`.",
            "Use `molecularmanipulator:foundation`.",
            "<Row>"
    })
    void validatorRejectsUnknownTagsIdsResourcesAndResearchImplementationLeaks(String body) {
        var page = new Page("en_us", INDEX);
        assertThrows(AssertionError.class, () -> audit(page, parse(page, fixture(body))), body);
    }

    @Test
    void realSnbtConversionAndPositionChecksRejectMaterialSwapsEvenWithIdenticalCounts() throws Exception {
        String snbt = "{size:[3,1,1],entities:[],palette:[\"minecraft:stone\",\"minecraft:gold_block\"],"
                + "data:[{pos:[0,0,0],state:\"minecraft:stone\"},{pos:[2,0,0],state:\"minecraft:gold_block\"}]}";
        var structure = decodeStructure(localId("fixture.snbt"), snbt.getBytes(StandardCharsets.UTF_8), "SNBT fixture");
        var expected = Map.of(BlockPos.ZERO, "minecraft:stone", new BlockPos(2, 0, 0), "minecraft:gold_block");
        assertBlocksAtEveryPosition(expected, structure, "valid SNBT fixture");
        var swapped = new HashMap<>(structure.blocks);
        swapped.put(BlockPos.ZERO, structure.blocks.get(new BlockPos(2, 0, 0)));
        swapped.put(new BlockPos(2, 0, 0), structure.blocks.get(BlockPos.ZERO));
        assertThrows(AssertionError.class, () -> assertBlocksAtEveryPosition(expected,
                new Structure(structure.size, swapped), "same-count material swap"));
        var shifted = new HashMap<>(structure.blocks);
        shifted.put(new BlockPos(1, 0, 0), shifted.remove(BlockPos.ZERO));
        assertThrows(AssertionError.class, () -> assertBlocksAtEveryPosition(expected,
                new Structure(structure.size, shifted), "same-count position shift"));
    }

    @Test
    void validSelfAnchorsUseGuideMesActualNormalization() throws Exception {
        var page = new Page("en_us", INDEX);
        audit(page, parse(page, fixture("## Heading: two words\n\n[heading](#heading:-two-words)\n\n"
                + "<a name=\"explicit-anchor\" />\n\n[anchor](#explicit-anchor)")));
    }

    @Test
    void validDependencyIdsAreNotTreatedAsMissingLocalModels() throws Exception {
        var page = new Page("en_us", INDEX);
        audit(page, parse(page, fixture("<Row>\n<ItemImage id=\"ae2:engineering_processor\" />\n\n"
                + "<BlockImage id=\"extendedae:assembler_matrix_frame\" />\n</Row>")));
    }

    @ParameterizedTest(name = "invalid frontmatter: {0}")
    @ValueSource(strings = {
            "navigation: []", "navigation:\n  icon: minecraft:diamond", "navigation:\n  title: 123",
            "navigation:\n  title: Test\n  position: not_a_number"
    })
    void invalidFrontmatterCannotSilentlyPassTheParserFallback(String yaml) {
        var page = new Page("en_us", INDEX);
        assertThrows(AssertionError.class, () -> validateFrontmatter(page,
                parse(page, "---\n" + yaml + "\n---\n\n# Fixture\n")));
    }

    private static String fixture(String body) {
        return "---\nnavigation:\n  title: Fixture\n  icon: minecraft:diamond\n---\n\n# Fixture\n\n" + body + "\n";
    }
}
