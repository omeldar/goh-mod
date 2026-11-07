package com.omeldar.goh;

/**
 * One waypoint on the server's shared list. Plain fields so Gson can carry it both to disk
 * and over the wire unchanged.
 */
public final class SharedWaypoint {
    public String name = "";
    public int x;
    public int y;
    public int z;
    public String dimension = "minecraft:overworld";
    /** Who shared it first. Only they, or the server owner, may overwrite or remove it. */
    public String owner = "";
    public String ownerUuid = "";
    /** Who wrote the current version, which is the owner unless the server owner stepped in. */
    public String updatedBy = "";
    public String updatedAt = "";

    public SharedWaypoint copy() {
        SharedWaypoint w = new SharedWaypoint();
        w.name = name;
        w.x = x;
        w.y = y;
        w.z = z;
        w.dimension = dimension;
        w.owner = owner;
        w.ownerUuid = ownerUuid;
        w.updatedBy = updatedBy;
        w.updatedAt = updatedAt;
        return w;
    }

    public String where() {
        String dim = dimension.startsWith("minecraft:") ? dimension.substring(10) : dimension;
        return x + " " + y + " " + z + (dim.equals("overworld") ? "" : " (" + dim + ")");
    }
}
