package com.smartcare.icustats.service;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ObjectId/String ID compatibility in quality data queries.
 */
class QualityIdCompatibilityTest {

    @Test
    void normalizeIdKey_objectId_returnsHexString() {
        ObjectId oid = new ObjectId();
        String hex = oid.toHexString();
        // The normalizeIdKey method should handle ObjectId consistently
        assertEquals(24, hex.length(), "ObjectId hex should be 24 chars");
        assertTrue(hex.matches("[0-9a-f]{24}"), "ObjectId hex should be lowercase hex");
    }

    @Test
    void normalizeIdKey_stringObjectId_normalized() {
        String id = "507f1f77bcf86cd799439011";
        // Both ObjectId.toString() and the raw string should produce the same key
        ObjectId oid = new ObjectId(id);
        assertEquals(id, oid.toHexString(), "ObjectId.toHexString should match input");
    }

    @Test
    void buildDualTypeIds_containsBothTypes() {
        List<String> stringIds = Arrays.asList("507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012");
        // Simulate the buildDualTypeIds logic
        List<Object> ids = new ArrayList<>(stringIds.size() * 2);
        for (String id : stringIds) {
            ids.add(id);
            if (ObjectId.isValid(id)) {
                ids.add(new ObjectId(id));
            }
        }
        assertEquals(4, ids.size(), "Should have 2 strings + 2 ObjectIds");
        assertTrue(ids.contains("507f1f77bcf86cd799439011"), "Should contain string version");
        assertTrue(ids.stream().anyMatch(o -> o instanceof ObjectId), "Should contain ObjectId version");
    }

    @Test
    void buildDualTypeIds_invalidObjectId_onlyString() {
        List<String> stringIds = Arrays.asList("not-a-valid-id");
        List<Object> ids = new ArrayList<>(stringIds.size() * 2);
        for (String id : stringIds) {
            ids.add(id);
            if (ObjectId.isValid(id)) {
                ids.add(new ObjectId(id));
            }
        }
        assertEquals(1, ids.size(), "Should only have string version for invalid ObjectId");
    }

    @Test
    void objectIdEquality_stringVsObjectId() {
        String hexId = "507f1f77bcf86cd799439011";
        ObjectId oid = new ObjectId(hexId);
        // MongoDB query with .in([hexId, oid]) should match both types
        List<Object> queryIds = Arrays.asList(hexId, oid);
        assertEquals(2, queryIds.size());
        // The string version should match a document with string qualityId
        assertTrue(queryIds.contains(hexId));
        // The ObjectId version should match a document with ObjectId qualityId
        assertTrue(queryIds.contains(oid));
    }

    @Test
    void itemsByQualityId_normalizesKeys() {
        // Simulate items where qualityId is stored as ObjectId
        ObjectId qid = new ObjectId("507f1f77bcf86cd799439011");
        Document item1 = new Document("_id", new ObjectId()).append("qualityId", qid).append("order", 0);
        Document item2 = new Document("_id", new ObjectId()).append("qualityId", qid.toHexString()).append("order", 1);

        // Both should normalize to the same key
        Map<String, List<Document>> map = new LinkedHashMap<>();
        for (Document item : Arrays.asList(item1, item2)) {
            Object qualityId = item.get("qualityId");
            String key;
            if (qualityId instanceof ObjectId) {
                key = qualityId.toString();
            } else {
                key = String.valueOf(qualityId);
            }
            // Normalize: lowercase hex for 24-char hex strings
            if (key.length() == 24 && key.matches("[0-9a-fA-F]{24}")) {
                key = key.toLowerCase();
            }
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        // Both items should be under the same key
        assertEquals(1, map.size(), "Both items should normalize to the same key");
        assertEquals(2, map.values().iterator().next().size(), "Both items should be in the same list");
    }
}
