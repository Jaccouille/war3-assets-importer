package org.example.core.model;

public final class ImportOptions {
    private final boolean createUnits;
    private final boolean placeUnits;
    private final boolean clearUnits;
    private final boolean clearAssets;
    private final String unitDefinition;

    public ImportOptions(boolean createUnits, boolean placeUnits, boolean clearUnits,
                         boolean clearAssets, String unitDefinition) {
        this.createUnits = createUnits;
        this.placeUnits = placeUnits;
        this.clearUnits = clearUnits;
        this.clearAssets = clearAssets;
        this.unitDefinition = unitDefinition;
    }

    public static ImportOptions defaults() {
        return new ImportOptions(true, true, false, false, "hfoo");
    }

    public boolean createUnits()    { return createUnits; }
    public boolean placeUnits()     { return placeUnits; }
    public boolean clearUnits()     { return clearUnits; }
    public boolean clearAssets()    { return clearAssets; }
    public String  unitDefinition() { return unitDefinition; }
}