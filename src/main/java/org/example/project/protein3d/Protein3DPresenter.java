package org.example.project.protein3d;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.collections.SetChangeListener;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import org.example.project.model.*;

import java.util.*;

/**
 * Renders a ProteinStructure in a JavaFX SubScene.
 * Supports balls, bond cylinders, and a ribbon representation.
 * Interaction: mouse drag to rotate, scroll to zoom, arrow keys to rotate, +/- to zoom.
 * Clicking an atom selects its residue (synced with the sequence editor).
 * Multi-residue selections are highlighted with one neon-green sphere per selected CA.
 */
public final class Protein3DPresenter {

    private static final double CAMERA_START_Z = -1000.0;
    private static final double CAMERA_MIN_Z   = -12000.0;
    private static final double CAMERA_MAX_Z   = -50.0;
    private static final double CA_CB_DISTANCE = 1.53; // Å, used for virtual Cβ on Glycine

    private final StackPane subSceneContainer;
    private final Slider atomRadiusSlider;
    private final Slider bondRadiusSlider;
    private final CheckBox atomsVisibleCheckBox;
    private final CheckBox bondsVisibleCheckBox;
    private final CheckBox ribbonVisibleCheckBox;
    private final CheckBox wireframeCheckBox;
    private final Label structureInfoLabel;

    private final ResidueSelectionModel selectionModel;

    private final BondCalculator  bondCalculator  = new BondCalculator();
    private final GeometryService geometryService = new GeometryService();
    private final CPKColorService cpkColorService = new CPKColorService();
    private final VdwRadiusService vdwRadiusService = new VdwRadiusService();

    private final Group moleculeRoot  = new Group();
    private final Group worldRoot     = new Group();
    private final Translate centerTranslate = new Translate();
    private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final SubScene subScene;

    private final List<MeshView> ribbonMeshViews = new ArrayList<>();

    // All atom spheres for each residue - used to highlight by material-swap
    private final Map<Integer, List<Sphere>> residueSpheres = new HashMap<>();
    // Original CPK material per sphere - restored when selection changes
    private final Map<Sphere, PhongMaterial> originalMaterials = new HashMap<>();

    // Neon-green highlight material (shared, set lazily to avoid pre-FX-init issues)
    private PhongMaterial highlightMat;

    // Maps each atom Sphere node → 0-based residue index for accurate 3D pick-based selection
    private final Map<javafx.scene.Node, Integer> atomToResidueIdx = new HashMap<>();

    // Mouse state: track press origin to distinguish click from drag
    private double pressStartX, pressStartY, lastMouseX, lastMouseY;
    private static final double CLICK_THRESHOLD_PX = 5.0;

    public Protein3DPresenter(StackPane subSceneContainer,
                              Slider atomRadiusSlider, Slider bondRadiusSlider,
                              CheckBox atomsVisibleCheckBox, CheckBox bondsVisibleCheckBox,
                              CheckBox ribbonVisibleCheckBox, CheckBox wireframeCheckBox,
                              Button zoomInButton, Button zoomOutButton,
                              Label structureInfoLabel,
                              ResidueSelectionModel selectionModel) {

        this.subSceneContainer    = subSceneContainer;
        this.atomRadiusSlider     = atomRadiusSlider;
        this.bondRadiusSlider     = bondRadiusSlider;
        this.atomsVisibleCheckBox = atomsVisibleCheckBox;
        this.bondsVisibleCheckBox = bondsVisibleCheckBox;
        this.ribbonVisibleCheckBox = ribbonVisibleCheckBox;
        this.wireframeCheckBox    = wireframeCheckBox;
        this.structureInfoLabel   = structureInfoLabel;
        this.selectionModel       = selectionModel;

        moleculeRoot.getTransforms().addAll(centerTranslate, rotateX, rotateY);
        worldRoot.getChildren().addAll(moleculeRoot, ambientLight(), pointLight());

        subScene = new SubScene(worldRoot, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#0D1117"));
        subScene.setCamera(camera);
        subScene.widthProperty() .bind(subSceneContainer.widthProperty());
        subScene.heightProperty().bind(subSceneContainer.heightProperty());
        subSceneContainer.getChildren().add(subScene);

        camera.setNearClip(0.1);
        camera.setFarClip(10000.0);
        camera.setTranslateZ(CAMERA_START_Z);

        zoomInButton .setOnAction(e -> zoomBy(0.75));
        zoomOutButton.setOnAction(e -> zoomBy(1.33));

        subScene.setFocusTraversable(true);
        subScene.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKey);

        subScene.setOnMousePressed(e -> {
            pressStartX = e.getSceneX();
            pressStartY = e.getSceneY();
            lastMouseX  = e.getSceneX();
            lastMouseY  = e.getSceneY();
            subScene.requestFocus();
        });

        // Use getPickResult() for accurate 3D ray-cast selection.
        // Guard with distance check so drag-rotation never triggers a selection.
        subScene.setOnMouseClicked(e -> {
            subScene.requestFocus();
            double dist = Math.hypot(e.getSceneX() - pressStartX, e.getSceneY() - pressStartY);
            if (dist < CLICK_THRESHOLD_PX && e.getPickResult() != null) {
                javafx.scene.Node hit = e.getPickResult().getIntersectedNode();
                Integer idx = hit != null ? atomToResidueIdx.get(hit) : null;
                if (idx != null) selectionModel.toggle(idx);
            }
        });
        subScene.setOnMouseDragged(e -> {
            rotateY.setAngle(rotateY.getAngle() - (e.getSceneX() - lastMouseX) * 0.4);
            rotateX.setAngle(rotateX.getAngle() + (e.getSceneY() - lastMouseY) * 0.4);
            lastMouseX = e.getSceneX();
            lastMouseY = e.getSceneY();
        });
        subScene.setOnScroll(e -> zoomBy(e.getDeltaY() > 0 ? 0.85 : 1.15));

        wireframeCheckBox.selectedProperty().addListener((obs, old, sel) -> {
            DrawMode mode = sel ? DrawMode.LINE : DrawMode.FILL;
            ribbonMeshViews.forEach(mv -> mv.setDrawMode(mode));
        });

        // Wireframe only affects the ribbon, so it can only be toggled while the
        // ribbon is shown. Disable it otherwise, and clear it when ribbon is hidden.
        wireframeCheckBox.disableProperty().bind(ribbonVisibleCheckBox.selectedProperty().not());
        ribbonVisibleCheckBox.selectedProperty().addListener((obs, old, sel) -> {
            if (!sel) wireframeCheckBox.setSelected(false);
        });

        atomsVisibleCheckBox.disableProperty().bind(
            atomsVisibleCheckBox.selectedProperty()
                .and(bondsVisibleCheckBox.selectedProperty().not())
                .and(ribbonVisibleCheckBox.selectedProperty().not()));
        bondsVisibleCheckBox.disableProperty().bind(
            bondsVisibleCheckBox.selectedProperty()
                .and(atomsVisibleCheckBox.selectedProperty().not())
                .and(ribbonVisibleCheckBox.selectedProperty().not()));
        ribbonVisibleCheckBox.disableProperty().bind(
            ribbonVisibleCheckBox.selectedProperty()
                .and(atomsVisibleCheckBox.selectedProperty().not())
                .and(bondsVisibleCheckBox.selectedProperty().not()));

        selectionModel.selectedIndicesProperty().addListener(
            (SetChangeListener<Integer>) change -> updateHighlight());

        // Lazy-initialise here so JavaFX is definitely ready
        highlightMat = new PhongMaterial(Color.web("#39FF14")); // radioactive neon green
        highlightMat.setSpecularColor(Color.WHITE);
        highlightMat.setSpecularPower(128);
    }

    /** Replaces the currently displayed structure (null clears the view). */
    private java.util.function.Consumer<ProteinStructure> structureListener;

    /** Notified whenever a structure is shown (or cleared with {@code null}); used for statistics. */
    public void setStructureListener(java.util.function.Consumer<ProteinStructure> listener) {
        this.structureListener = listener;
    }

    public void showStructure(ProteinStructure structure) {
        if (structureListener != null) structureListener.accept(structure);
        moleculeRoot.getChildren().clear();
        ribbonMeshViews.clear();
        atomToResidueIdx.clear();
        residueSpheres.clear();
        originalMaterials.clear();

        if (structure == null || structure.getAtomCount() == 0) {
            centerTranslate.setX(0); centerTranslate.setY(0); centerTranslate.setZ(0);
            structureInfoLabel.setText("");
            return;
        }

        Point3D centroid = geometryService.centroid(structure.getAtoms());
        centerTranslate.setX(-centroid.getX());
        centerTranslate.setY(-centroid.getY());
        centerTranslate.setZ(-centroid.getZ());

        Group ballStick = new Group();
        Map<String, ResidueGroup> residueGroups = new LinkedHashMap<>();
        for (AminoAcid aa : structure.getAminoAcids()) {
            ResidueGroup rg = new ResidueGroup();
            residueGroups.put(residueKey(aa), rg);
            ballStick.getChildren().add(rg.root);
        }

        List<AminoAcid> aminoAcids = structure.getAminoAcids();
        for (int i = 0; i < aminoAcids.size(); i++) {
            AminoAcid aa = aminoAcids.get(i);
            final int residueSeqIdx = i; // 0-based index into sequence
            ResidueGroup rg = residueGroups.get(residueKey(aa));
            if (rg == null) continue;
            for (Atom atom : aa.getAtoms()) {
                Sphere sphere = atomSphere(atom);
                // Store CPK material for restore-on-deselect
                PhongMaterial cpkMat = (PhongMaterial) sphere.getMaterial();
                originalMaterials.put(sphere, cpkMat);
                residueSpheres.computeIfAbsent(residueSeqIdx, k -> new ArrayList<>()).add(sphere);
                // Register in pick map - selection handled centrally in subScene.setOnMouseClicked
                atomToResidueIdx.put(sphere, residueSeqIdx);
                rg.atoms.getChildren().add(sphere);
            }
        }

        List<Bond> bonds = bondCalculator.detectBonds(structure.getAtoms());
        for (Bond bond : bonds) {
            ResidueGroup rg = residueGroups.get(
                residueKey(bond.getFirst().getChainId(), bond.getFirst().getResidueSequenceNumber()));
            if (rg != null) rg.bonds.getChildren().add(bondCylinder(bond));
        }

        Group ribbon = buildRibbon(structure);
        ribbon.visibleProperty().bind(ribbonVisibleCheckBox.selectedProperty());

        moleculeRoot.getChildren().addAll(ballStick, ribbon);
        updateHighlight(); // apply any pre-existing selection via material swap
        autofitCamera(structure, centroid);

        structureInfoLabel.setText(structure.getAtomCount() + " atoms · "
            + bonds.size() + " bonds · "
            + structure.getResidueCount() + " residues");

        subScene.requestFocus();
    }

    // ── Ribbon ────────────────────────────────────────────────────────────

    private Group buildRibbon(ProteinStructure structure) {
        Group g = new Group();
        List<AminoAcid> residues = structure.getAminoAcids();
        for (int i = 0; i < residues.size() - 1; i++) {
            AminoAcid r1 = residues.get(i), r2 = residues.get(i + 1);
            if (r1.getChainId() != r2.getChainId()) continue;
            Optional<Point3D> ca1 = findAtom(r1, "CA"), cb1 = virtualOrRealCb(r1);
            Optional<Point3D> ca2 = findAtom(r2, "CA"), cb2 = virtualOrRealCb(r2);
            if (ca1.isEmpty() || cb1.isEmpty() || ca2.isEmpty() || cb2.isEmpty()) continue;
            MeshView mv = ribbonSegment(ca1.get(), cb1.get(), ca2.get(), cb2.get());
            ribbonMeshViews.add(mv);
            g.getChildren().add(mv);
        }
        return g;
    }

    private MeshView ribbonSegment(Point3D ca1, Point3D cb1, Point3D ca2, Point3D cb2) {
        Point3D opp1 = ca1.multiply(2).subtract(cb1);
        Point3D opp2 = ca2.multiply(2).subtract(cb2);

        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(
            (float)cb1.getX(),  (float)cb1.getY(),  (float)cb1.getZ(),
            (float)ca1.getX(),  (float)ca1.getY(),  (float)ca1.getZ(),
            (float)opp1.getX(), (float)opp1.getY(), (float)opp1.getZ(),
            (float)opp2.getX(), (float)opp2.getY(), (float)opp2.getZ(),
            (float)ca2.getX(),  (float)ca2.getY(),  (float)ca2.getZ(),
            (float)cb2.getX(),  (float)cb2.getY(),  (float)cb2.getZ()
        );
        mesh.getTexCoords().addAll(0f, 0f);
        mesh.getFaces().addAll(
            0,0, 1,0, 4,0,  0,0, 4,0, 5,0,  1,0, 2,0, 3,0,  1,0, 3,0, 4,0,
            4,0, 1,0, 0,0,  5,0, 4,0, 0,0,  3,0, 2,0, 1,0,  4,0, 3,0, 1,0
        );
        mesh.getFaceSmoothingGroups().addAll(1, 1, 1, 1, 2, 2, 2, 2);

        PhongMaterial mat = new PhongMaterial(Color.web("#2E7D9A"));
        mat.setSpecularColor(Color.WHITE);
        mat.setSpecularPower(32);

        MeshView mv = new MeshView(mesh);
        mv.setMaterial(mat);
        mv.setCullFace(CullFace.BACK);
        mv.setDrawMode(wireframeCheckBox.isSelected() ? DrawMode.LINE : DrawMode.FILL);
        return mv;
    }

    private Optional<Point3D> virtualOrRealCb(AminoAcid residue) {
        Optional<Point3D> cb = findAtom(residue, "CB");
        if (cb.isPresent()) return cb;
        Optional<Point3D> n = findAtom(residue, "N"), ca = findAtom(residue, "CA"), c = findAtom(residue, "C");
        if (n.isEmpty() || ca.isEmpty() || c.isEmpty()) return Optional.empty();
        Point3D u = ca.get().subtract(n.get()).normalize();
        Point3D v = c.get().subtract(ca.get()).normalize();
        Point3D dir = u.add(v).add(u.crossProduct(v).normalize()).normalize();
        return Optional.of(ca.get().add(dir.multiply(CA_CB_DISTANCE)));
    }

    private Optional<Point3D> findAtom(AminoAcid residue, String atomName) {
        for (Atom a : residue.getAtoms()) {
            if (a.getName().trim().equalsIgnoreCase(atomName))
                return Optional.of(new Point3D(a.getX(), a.getY(), a.getZ()));
        }
        return Optional.empty();
    }

    // ── Atom sphere / bond cylinder factories ─────────────────────────────

    private Sphere atomSphere(Atom atom) {
        Sphere sphere = new Sphere();
        sphere.setTranslateX(atom.getX());
        sphere.setTranslateY(atom.getY());
        sphere.setTranslateZ(atom.getZ());
        sphere.setCullFace(CullFace.BACK);
        sphere.setMaterial(new PhongMaterial(cpkColorService.colorFor(atom)));
        sphere.visibleProperty().bind(atomsVisibleCheckBox.selectedProperty());
        sphere.managedProperty().bind(sphere.visibleProperty());
        DoubleBinding r = Bindings.createDoubleBinding(
            () -> atomRadiusSlider.getValue() * vdwRadiusService.radiusFor(atom),
            atomRadiusSlider.valueProperty());
        sphere.radiusProperty().bind(r);
        return sphere;
    }

    private Cylinder bondCylinder(Bond bond) {
        Atom a1 = bond.getFirst(), a2 = bond.getSecond();
        BondPlacement p = geometryService.placement(a1, a2);
        double r1vdw = vdwRadiusService.radiusFor(a1);
        double r2vdw = vdwRadiusService.radiusFor(a2);

        Cylinder cyl = new Cylinder();
        cyl.setDrawMode(DrawMode.FILL);
        cyl.setCullFace(CullFace.NONE);
        PhongMaterial mat = new PhongMaterial(Color.web("#9E9E9E"));
        mat.setSpecularColor(Color.WHITE);
        cyl.setMaterial(mat);

        cyl.heightProperty().bind(Bindings.createDoubleBinding(
            () -> Math.max(0.01, p.length() - atomRadiusSlider.getValue() * (r1vdw + r2vdw)),
            atomRadiusSlider.valueProperty()));
        cyl.radiusProperty().bind(bondRadiusSlider.valueProperty());
        cyl.visibleProperty().bind(bondsVisibleCheckBox.selectedProperty());
        cyl.managedProperty().bind(cyl.visibleProperty());
        cyl.getTransforms().addAll(
            new Translate(p.midpoint().getX(), p.midpoint().getY(), p.midpoint().getZ()),
            new Rotate(p.rotationAngleDegrees(), p.rotationAxis())
        );
        return cyl;
    }

    // ── Selection highlight ────────────────────────────────────────────────

    /**
     * Highlights every atom of each selected residue by swapping its material to
     * neon green.  All other atoms are restored to their original CPK colors.
     * This means clicking any atom of an amino acid turns the entire residue green.
     */
    private void updateHighlight() {
        // 1. Restore all atom spheres to their CPK colors
        originalMaterials.forEach((sphere, mat) -> sphere.setMaterial(mat));
        // 2. Apply neon-green to every atom of every selected residue
        for (int seqIdx : selectionModel.selectedIndicesProperty()) {
            List<Sphere> spheres = residueSpheres.get(seqIdx);
            if (spheres != null) spheres.forEach(s -> s.setMaterial(highlightMat));
        }
    }

    // ── Interaction ────────────────────────────────────────────────────────

    private void zoomBy(double factor) {
        double newZ = camera.getTranslateZ() * factor;
        camera.setTranslateZ(Math.max(CAMERA_MIN_Z, Math.min(CAMERA_MAX_Z, newZ)));
        subScene.requestFocus();
    }

    private void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case LEFT  -> { rotateY.setAngle(rotateY.getAngle() + 5); e.consume(); }
            case RIGHT -> { rotateY.setAngle(rotateY.getAngle() - 5); e.consume(); }
            case UP    -> { rotateX.setAngle(rotateX.getAngle() - 5); e.consume(); }
            case DOWN  -> { rotateX.setAngle(rotateX.getAngle() + 5); e.consume(); }
            case PLUS, EQUALS -> { zoomBy(0.85); e.consume(); }
            case MINUS        -> { zoomBy(1.15); e.consume(); }
            default -> {}
        }
    }

    // ── Lights ─────────────────────────────────────────────────────────────

    private static AmbientLight ambientLight() {
        AmbientLight l = new AmbientLight(Color.color(0.45, 0.48, 0.55));
        l.setLightOn(true);
        return l;
    }

    private static PointLight pointLight() {
        PointLight l = new PointLight(Color.color(1.0, 0.97, 0.92));
        l.setTranslateX(-400); l.setTranslateY(-400); l.setTranslateZ(-800);
        return l;
    }

    // ── Camera ─────────────────────────────────────────────────────────────

    private void autofitCamera(ProteinStructure structure, Point3D centroid) {
        double maxDist = 0;
        for (Atom a : structure.getAtoms()) {
            double dx = a.getX() - centroid.getX();
            double dy = a.getY() - centroid.getY();
            double dz = a.getZ() - centroid.getZ();
            maxDist = Math.max(maxDist, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        double camDist = maxDist / (0.60 * Math.tan(Math.toRadians(15)));
        camera.setTranslateZ(Math.max(CAMERA_MIN_Z, Math.min(CAMERA_MAX_Z, -camDist)));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String residueKey(AminoAcid aa) {
        return residueKey(aa.getChainId(), aa.getSequenceNumber());
    }
    private static String residueKey(char chain, int seq) { return chain + ":" + seq; }

    private static final class ResidueGroup {
        final Group root  = new Group();
        final Group bonds = new Group();
        final Group atoms = new Group();
        ResidueGroup() { root.getChildren().addAll(bonds, atoms); }
    }
}
