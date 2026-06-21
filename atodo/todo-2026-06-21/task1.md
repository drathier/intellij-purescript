# Task 1 — Cache `resolve()` results per-element

Priority: P0
Files: `PSFile.kt`, `ImportedValueReference.kt`, `ImportedDataReference.kt`, `ImportedClassReference.kt`, `ModuleReference.kt`, `ExportedValueReference.kt`, `ExportedDataReference.kt`

## Goal

Every `reference.resolve()` call recomputes from scratch:
traverses imports, calls `exportedValue(name)`, recursively walks re-exports.

Within a single highlighting pass, the same element's `resolve()` may be called
multiple times (annotator fallback + inspections + inference). Each call is
redundant.

## Approach

Add a per-file result cache (`PsiElement → PsiNamedElement?`) on `PSFile`.
All reference classes check this cache before doing full resolve.
Cache is cleared on every `subtreeChanged()`.

The cache key is PSI element identity (not name string), so it correctly
distinguishes different references that happen to have the same name.

## Implementation

### Step 1: Verify PSFile already has the cache

`PSFile.kt` already has (from the Task 2 commit):
```kotlin
val resolveCache = mutableMapOf<PsiElement, PsiNamedElement?>()

override fun subtreeChanged() {
    resolveCache.clear()
    ...
    super.subtreeChanged()
}
```

No changes needed here.

### Step 2: Add cache check to each reference class

For each reference class, replace:
```kotlin
override fun resolve(): Foo? = candidates.firstOrNull { it.name == element.name }
```
with:
```kotlin
override fun resolve(): Foo? {
    val file = element.containingFile as? PSFile
    file?.resolveCache?.get(element)?.let { return it as? Foo }
    val result = candidates.firstOrNull { it.name == element.name }
    file?.resolveCache?.put(element, result)
    return result
}
```

Reference classes to update:
1. `ImportedValueReference.kt` — resolves imported values
2. `ImportedDataReference.kt` — resolves imported data/types
3. `ImportedClassReference.kt` — resolves imported classes
4. `ModuleReference.kt` — resolves module names (stub index lookup)
5. `ExportedValueReference.kt` — resolves values in export list
6. `ExportedDataReference.kt` — resolves data types in export list

### Step 3: Add test

Write a test that creates files with various reference types and verifies
that `resolve()` returns the correct result (same as before). The cache is
transparent — correctness is identical, only performance changes.

## Files already done

The following files already have the cache check (committed in Task 2):
- `PSFile.kt` — cache + `subtreeChanged()` clear
- `ExpressionIdentifierReference.kt` — cache check
- `TypeConstructorReference.kt` — cache check
- `ConstructorReference.kt` — cache check

## Verification

1. Build compiles cleanly
2. Existing tests pass (same behavior, transparent cache)
3. CPU should drop further since inspections no longer do redundant resolves

## Expected impact

Reduces redundant `resolve()` calls per pass by ~60-70%. After Task 1,
the remaining CPU should be mostly from type inference (Task 3).
