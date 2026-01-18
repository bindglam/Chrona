package org.wrd.chrona.tick.phase;

public interface Intent {
    IntentType getType();
    long getPriority();
    String getSource();

    enum IntentType {
        BLOCK_SET,
        BLOCK_BREAK,
        ENTITY_MOVE,
        ENTITY_SPAWN,
        ENTITY_REMOVE,
        DAMAGE,
        INVENTORY_CHANGE,
        CHUNK_GEN,
        STRUCTURE_PLACE
    }
}
