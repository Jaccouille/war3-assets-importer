package com.hiveworkshop.war3assetsimporter.core.model;

/**
 * A single unit placement read from {@code war3mapUnits.doo}, already converted
 * to normalised image coordinates [0..1] and a facing angle in degrees.
 *
 * @param normX    normalised X position in the map preview image  [0 = west, 1 = east]
 * @param normY    normalised Y position in the map preview image  [0 = north, 1 = south]
 * @param angleDeg facing angle in degrees, WC3 convention (0 = east, 90 = north)
 */
public record ExistingUnit(double normX, double normY, double angleDeg) {
}
