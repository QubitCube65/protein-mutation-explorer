package org.example.project.model;

import javafx.geometry.Point3D;

/** Spatial placement data for rendering a bond cylinder (midpoint, length, rotation). */
public final class BondPlacement {
    private final Point3D midpoint;
    private final double length;
    private final double rotationAngleDegrees;
    private final Point3D rotationAxis;

    public BondPlacement(Point3D midpoint, double length, double rotationAngleDegrees, Point3D rotationAxis) {
        this.midpoint = midpoint;
        this.length = length;
        this.rotationAngleDegrees = rotationAngleDegrees;
        this.rotationAxis = rotationAxis;
    }

    public Point3D midpoint()              { return midpoint; }
    public double length()                 { return length; }
    public double rotationAngleDegrees()   { return rotationAngleDegrees; }
    public Point3D rotationAxis()          { return rotationAxis; }
}
