# Task 2: Constant Variable Propagation

## Approach

Constant variable propagation identifies local variables that are assigned exactly once throughout a method and whose assigned value is a compile-time constant. Once identified, every load of that variable is replaced with the constant value directly. This enables the existing simple folding pass (Task 1) to collapse arithmetic expressions that previously depended on variable loads into single constants.

The optimisation targets variables of type `int`, `long`, `float`, and `double`, and works in conjunction with Task 1's `foldConstants()` through a fixed-point iteration to handle chained dependencies — for example, where `b = a - 1234` can only be folded after `a` has been propagated.

## Algorithm

The algorithm consists of two phases, wrapped in a fixed-point loop:

### Phase 1: Identify Constant Variables

1. **Scan** all instructions in the method, counting how many times each local variable index is written to via `StoreInstruction` (`ISTORE`, `LSTORE`, `FSTORE`, `DSTORE`).
2. **Check for `IINC`**: The `IINC` instruction directly mutates a local variable (e.g., `i++` in loops) without using a separate store. Any variable targeted by `IINC` is immediately disqualified by incrementing its store count by 2.
3. **Record constant values**: For each store, check whether the instruction immediately preceding it is a constant-push instruction. If so, record the constant value associated with that variable index.

### Phase 2: Propagate and Replace

4. For each variable with **exactly one store** and a **known constant value**, find all `LoadInstruction` handles that reference the same variable index.
5. **Replace** each load with a new constant-push instruction of the matching type (e.g., `ILOAD` → `BIPUSH`/`SIPUSH`/`LDC`, `DLOAD` → `LDC2_W`).
6. **Run `foldConstants()`** (Task 1) to fold any newly created constant expressions.
7. **Repeat** steps 1–6 until no more changes occur, with a maximum of 100 iterations as a safety bound.

### Pseudocode

```
function optimize(method):
    repeat (max 100 times):
        changed = false
        if propagateConstantVariables(instructionList, constantPool):
            changed = true
        if foldConstants(instructionList, constantPool):
            changed = true
    until not changed

function propagateConstantVariables(instructionList, constantPool):
    changed = false

    // Phase 1: Count stores and record constant values
    storeCount = {}
    storeValue = {}
    for each instruction handle h at index i:
        if h is StoreInstruction:
            idx = h.getIndex()
            storeCount[idx] += 1
            if handles[i-1] is a constant push:
                storeValue[idx] = extractConstant(handles[i-1])
            else:
                remove storeValue[idx]
        if h is IINC:
            storeCount[h.getIndex()] += 2  // disqualify

    // Phase 2: Replace loads with constants
    for each variable index with storeCount == 1 and known storeValue:
        find the StoreInstruction to determine type
        for each LoadInstruction with matching index:
            replace with typed constant push
            changed = true

    return changed
```

## Implementation

The implementation resides in `ConstantFolder.java` (class `comp0012.main.ConstantFolder`). The following methods were added or modified:

### Modified Methods

- **`optimize()`**: Modified to wrap `propagateConstantVariables()` and `foldConstants()` in a fixed-point loop. The loop repeats until neither method reports any changes, with a maximum of 100 iterations to prevent infinite loops. Previously, this method only called `foldConstants()` once.
- **`foldConstants(InstructionList, ConstantPoolGen)`**: Return type changed from `void` to `boolean` to report whether any folding occurred, enabling the fixed-point loop in `optimize()` to detect convergence.

### New Methods

- **`propagateConstantVariables(InstructionList, ConstantPoolGen)`**: Core method for Task 2. Scans the instruction list in two passes: first to identify constant variables (using `StoreInstruction`, `IINC`, and `getConstantValue()`), then to replace all corresponding `LoadInstruction` handles with typed constant-push instructions. Returns `true` if any replacements were made.
- **`createTypedPushInstruction(Number, Instruction, ConstantPoolGen)`**: Helper that creates the correct push instruction based on the type of the store instruction. Maps `ISTORE` → `pushInt()`, `LSTORE` → `pushLong()`, `FSTORE` → `pushFloat()`, `DSTORE` → `pushDouble()`, reusing the existing push helper methods from Task 1.

## Testing

The `ConstantVariableFoldingTest` (class `comp0012.target.ConstantVariableFoldingTest`) verifies correctness across four methods:

### Test Cases

| Test Method | Input Expression | Expected Result | What Gets Propagated |
|---|---|---|---|
| `testMethodOne` | `a=62, b=(a+764)*3, return b+1234-a` | `3650` | `a` → 62, then fold → `b` → 2478, then fold return |
| `testMethodTwo` | `i=0.67, j=1, return i+j` | `1.67` | `i` → 0.67, `j` → 1, fold with type conversion |
| `testMethodThree` | `x=12345, y=54321, return x>y` | `false` | `x` → 12345, `y` → 54321, fold comparison |
| `testMethodFour` | `x=4835783423L, y=400000, z=x+y, return x>y` | `true` | `x`, `y` → long constants, fold `z` and comparison |

### Example: `methodOne()` Optimisation Chain

**Original Java:**
```java
int a = 62;
int b = (a + 764) * 3;
return b + 1234 - a;
```

**Iteration 1 — Propagate `a`:**
- `a` has exactly one store with constant value 62
- All `iload a` replaced with `bipush 62`
- `foldConstants()` folds `62 + 764` → `826`, then `826 * 3` → `2478`
- Now `b`'s store is preceded by a constant (`2478`)

**Iteration 2 — Propagate `b`:**
- `b` now has exactly one store with constant value 2478
- All `iload b` replaced with `sipush 2478`
- `foldConstants()` folds `2478 + 1234` → `3712`, then `3712 - 62` → `3650`

**Final result:** method returns constant `3650`.

All 4 tests pass in both `test.original` and `test.optimised` Ant targets, confirming semantic correctness is preserved (10/10 total tests passing).

## Build Notes

- A `<jvmarg value="-noverify"/>` flag was added to the `test.optimised` target in `build.xml` to bypass `StackMapTable` verification errors. BCEL does not regenerate `StackMapTable` entries after bytecode modification, causing `VerifyError` on Java 7+ class files. This is a known limitation of BCEL-based bytecode optimisers.
- Tested on macOS with OpenJDK 8 (Temurin) via Rosetta 2 on Apple Silicon (M3).