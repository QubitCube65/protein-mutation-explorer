package org.example.project.model;

import javafx.geometry.Point3D;
import java.util.List;

/** 3D geometry helpers: centroid and bond-cylinder placement. */
public final class GeometryService {

    public Point3D centroid(List<Atom> atoms) {
        if (atoms.isEmpty()) return new Point3D(0, 0, 0);
        double sx = 0, sy = 0, sz = 0;
        for (Atom a : atoms) { sx += a.getX(); sy += a.getY(); sz += a.getZ(); }
        int n = atoms.size();
        return new Point3D(sx / n, sy / n, sz / n);
    }

    /** Returns the placement (midpoint, length, rotation) needed to draw a cylinder for the bond. */
    public BondPlacement placement(Atom from, Atom to) {
        Point3D p1 = new Point3D(from.getX(), from.getY(), from.getZ());
        Point3D p2 = new Point3D(to.getX(),   to.getY(),   to.getZ());
        Point3D mid = p1.midpoint(p2);
        double len = p1.distance(p2);

        Point3D dir  = p2.subtract(p1).normalize();
        // JavaFX Cylinder is aligned along Y by default, so we rotate from Y to bond direction.
        Point3D yAx  = new Point3D(0, 1, 0);
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dir.dotProduct(yAx)))));

        Point3D axis = (Math.abs(angle) < 0.01 || Math.abs(angle - 180) < 0.01)
            ? new Point3D(1, 0, 0)
            : yAx.crossProduct(dir).normalize();

        return new BondPlacement(mid, len, angle, axis);
    }
}
