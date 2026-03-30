# Task 1: Simple Folding

## Approach

Simple folding is a peephole optimisation that evaluates constant arithmetic expressions at compile time rather than at runtime. The Java compiler (`javac`) already performs this for Java source code, but the provided `SimpleFolding.j` (Jasmin assembly) produces bytecode with un-optimised constant expressions. Our optimiser scans the bytecode instruction stream for patterns where two constant values are pushed onto the operand stack followed by a binary arithmetic operation, and replaces the three instructions with a single instruction that pushes the pre-computed result.

This applies to all numeric types: `int`, `long`, `float`, and `double`.

## Algorithm

The algorithm operates as a fixed-point iteration over each method's instruction list:

1. **For each method** in the class, obtain the instruction list via BCEL's `MethodGen`.
2. **Scan** the instruction handles sequentially, looking for three consecutive instructions matching the pattern:
   - Instruction at position `i`: a constant-push instruction (e.g., `ICONST`, `BIPUSH`, `SIPUSH`, `LDC`, `LDC2_W`, `LCONST`, `FCONST`, `DCONST`)
   - Instruction at position `i+1`: another constant-push instruction
   - Instruction at position `i+2`: a binary arithmetic or comparison instruction (e.g., `IADD`, `ISUB`, `IMUL`, `IDIV`, `IREM`, and the equivalent for `long`, `float`, `double`, plus `LCMP`, `FCMPL`, `FCMPG`, `DCMPL`, `DCMPG`)
3. **Compute** the result of the operation on the two constant values at compile time.
4. **Replace** the first instruction with a new constant-push instruction holding the computed result, and **delete** the second constant and the arithmetic instruction.
5. **Repeat** from step 2 until no more folding is possible (fixed-point), which handles chained expressions like `a + b + c` where `a`, `b`, `c` are all constants.

### Pseudocode

```
function foldConstants(instructionList, constantPool):
    repeat:
        changed = false
        handles = instructionList.getHandles()
        for i = 0 to len(handles) - 3:
            val1 = extractConstant(handles[i])
            val2 = extractConstant(handles[i+1])
            if val1 != null AND val2 != null:
                op = handles[i+2]
                result = evaluate(val1, op, val2)
                if result != null:
                    handles[i].setInstruction(pushConstant(result))
                    delete handles[i+1] and handles[i+2]
                    changed = true
                    break  // restart scan
    until not changed
```

## Implementation

The implementation resides in `ConstantFolder.java` (class `comp0012.main.ConstantFolder`). The key methods are:

- **`optimize()`**: Entry point. Iterates over all methods in the class, creates a `MethodGen` for each, runs `foldConstants()`, then replaces the method with the optimised version.
- **`foldConstants(InstructionList, ConstantPoolGen)`**: Implements the fixed-point peephole scan described above. Scans for the (const, const, binary_op) pattern and replaces it with the folded result.
- **`getConstantValue(Instruction, ConstantPoolGen)`**: Extracts the numeric value from a constant-push instruction. Handles `ConstantPushInstruction` (covers `ICONST_*`, `BIPUSH`, `SIPUSH`, `FCONST_*`, `DCONST_*`, `LCONST_*`), `LDC` (int/float from constant pool), and `LDC2_W` (long/double from constant pool).
- **`foldBinaryOp(Number, Number, Instruction)`**: Evaluates the binary operation at compile time. Supports all arithmetic operations (`ADD`, `SUB`, `MUL`, `DIV`, `REM`) for `int`, `long`, `float`, `double`, and comparison operations (`LCMP`, `FCMPL`, `FCMPG`, `DCMPL`, `DCMPG`). Guards against division by zero for integer types.
- **`createPushInstruction(Number, Instruction, ConstantPoolGen)`**: Creates the most efficient push instruction for the result value. For `int`, uses `ICONST` (-1 to 5), `BIPUSH` (byte range), `SIPUSH` (short range), or `LDC` (full int range). For `long`, uses `LCONST` (0 or 1) or `LDC2_W`. For `float`/`double`, uses `LDC`/`LDC2_W` via the constant pool.
- **`safeDelete(InstructionList, InstructionHandle, InstructionHandle, InstructionHandle)`**: Deletes instructions from the list, handling `TargetLostException` by redirecting any branch targets to the folded instruction.

## Testing

The `SimpleFoldingTest` (class `comp0012.target.SimpleFoldingTest`) verifies correctness by:

1. Instantiating the `SimpleFolding` class (compiled from `SimpleFolding.j` via Jasmin).
2. Calling `simple()`, which pushes two constants (`67` and `12345`), adds them, and prints the result.
3. Capturing `System.out` and asserting the output equals `"12412\n"`.

### Bytecode before optimisation (`SimpleFolding.simple()`):

```
getstatic     java/lang/System.out
ldc           67
ldc           12345
iadd
invokevirtual java/io/PrintStream.println(I)V
return
```

### Bytecode after optimisation:

```
getstatic     java/lang/System.out
sipush        12412
invokevirtual java/io/PrintStream.println(I)V
return
```

The three instructions (`ldc 67`, `ldc 12345`, `iadd`) are folded into a single `sipush 12412`, since 12412 fits in the signed 16-bit range used by `SIPUSH`. The optimised code produces identical output while executing fewer instructions.

Both `test.original` and `test.optimised` Ant targets pass all tests (10/10), confirming the optimisation preserves semantic correctness.
