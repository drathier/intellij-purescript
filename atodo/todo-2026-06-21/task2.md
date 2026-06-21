# Task 2 — Don't call resolve() per-usage in the syntax annotator

Priority: P0
Files: `PSSyntaxHighlightAnnotator.kt`, `Module.kt`

## Goal

Make `PSSyntaxHighlightAnnotator` not call `element.reference.resolve()` for
every `PSExpressionIdentifier` usage site. A 2k-line file might have ~500
identifier usages but only ~80 unique names. Currently we call resolve 500
times per highlighting pass, each traversing the full import chain.

## Approach: two-tier resolution

### Tier 1 — Cheap, per-usage, no cross-file work

Inline the cheap local-scope + top-level check directly in the annotator.
This is essentially extracting lines 47-51 of `ExpressionIdentifierReference.resolve()`
and combining with a check against `Module.cache.valueGroups`.

```kotlin
is PSExpressionIdentifier -> {
    val ref = resolveHighlighting(module, element)  // new function
    when (ref) {
        is ValueDeclarationGroup -> apply highlighting (global or local)
        is VarBinder -> apply PARAMETER highlighting
        else -> null  // not found locally, need Tier 2
    }
}
```

`resolveHighlighting` does:
1. Walk parent `ValueNamespace`, check names against `valueNames`
   (same as `ExpressionIdentifierReference.resolve()` lines 47-51)
2. If not found, check `module.cache.valueGroups` (top-level names)
3. Return null if still not found (means it's imported or truly unresolved)

This is purely within the current file — no cross-file work, no import traversal.

### Tier 2 — Cached, per-name, cross-file

For names not resolved by Tier 1, cache the `resolve()` result by name string.

A `HashMap<String, PsiNamedElement?>` on `Module.Cache` keyed by identifier name.

- First miss for name `"foo"`: call `element.reference.resolve()`, store in cache
- Subsequent misses for `"foo"`: read from cache (any `PSExpressionIdentifier` with same name)

Cleared on every `cache = Cache()` in `subtreeChanged()` (same lifecycle as other
module caches — no new invalidation logic needed).

**Shadowing caveat**: If a `let` binding shadows an imported name, and the
let-bound usage is encountered first, both usages would get the let-bound
coloring (LOCAL_VARIABLE). PureScript warns about shadowing, so this is very
rare, and it self-corrects on the next pass (after `subtreeChanged()`).

---

## Implementation steps

### Step 1: Add cached-highlight-resolve map to Module.Cache

File: `src/main/kotlin/org/purescript/module/Module.kt`

In the `Cache` inner class, add one field:
```kotlin
val highlightResolveCache by lazy { mutableMapOf<String, PsiNamedElement?>() }
```

That's it. The existing `cache = Cache()` in `subtreeChanged()` clears it.
No other changes to Module.kt.

### Step 2: Rewrite PSSyntaxHighlightAnnotator.annotate()

File: `src/main/kotlin/org/purescript/highlighting/PSSyntaxHighlightAnnotator.kt`

Replace the `element.reference.resolve()` call with the two-tier approach:

```kotlin
is PSExpressionIdentifier -> {
    val resolved = resolveForHighlighting(element)
    when (resolved) {
        is ValueDeclarationGroup -> holder.newSilentAnnotation(INFORMATION)
            .textAttributes(if (resolved.isTopLevel) GLOBAL_VARIABLE else LOCAL_VARIABLE).create()
        is VarBinder -> holder.newSilentAnnotation(INFORMATION)
            .textAttributes(PARAMETER).create()
    }
}
```

Where `resolveForHighlighting` is a new method:

```kotlin
private fun resolveForHighlighting(element: PSExpressionIdentifier): PsiNamedElement? {
    val name = element.name
    if (element.qualifiedIdentifier.moduleName?.name != null) return null  // qualified refs skip fast path

    // Tier 1: local scope (same-module, same-file)
    val local = element
        .parentsOfType<ValueNamespace>(withSelf = false)
        .flatMap { it.valueNames }
        .takeWhile { it.containingFile == element.containingFile }
        .firstOrNull { it.name == name }
    if (local != null) return local

    // Tier 1: top-level declarations in same module
    val module = element.module
    val topLevel = module.cache.valueGroups.firstOrNull { it.name == name }
    if (topLevel != null) return topLevel

    // Tier 2: cached cross-file resolve
    return module.cache.highlightResolveCache.getOrPut(name) {
        element.reference.resolve()
    }
}
```

### Step 3: Clean up imports

Remove unused imports from `PSSyntaxHighlightAnnotator.kt`:
- `ValueDecl` (no longer needed if not used elsewhere in the file)
- `Signature` (no longer needed)
- `org.purescript.psi.util.parents` (if only used by `parentOfType`)

## Verification

1. Open a PureScript file — all identifiers should be colored immediately
   (before: 1-2s delay for correct coloring after scroll)
2. Check that local variables, parameters, top-level names, imported names,
   and qualified names all get the correct coloring
3. Check that `subtreeChanged()` clears the cache — edit the file, verify
   coloring is still correct
4. Monitor CPU — should drop significantly on idle

## Expected impact

- Resolve calls per pass: ~500 → ~80 (84% reduction)
- Scrolling highlight delay: 1-2s → ~160-320ms (warm caches for most names)
- Sustained idle CPU: major reduction (annotator was 50% of the 256%)
