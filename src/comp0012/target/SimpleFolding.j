; Jasmin Java assembler code that assembles the SimpleFolding example class

.source Type1.j
.class public comp0012/target/SimpleFolding
.super java/lang/Object

.method public <init>()V
	aload_0
	invokenonvirtual java/lang/Object/<init>()V
	return
.end method

.method public simple()V
	.limit stack 3

	getstatic java/lang/System/out Ljava/io/PrintStream;
	ldc 67
	ldc 12345
    iadd
    invokevirtual java/io/PrintStream/println(I)V
	return
.end method

; Dead store elimination test:
; x is assigned 100 then immediately overwritten with 200 before ever being read.
; The first ldc+istore is a dead store and should be eliminated by the optimiser.
; The method should return 200.
.method public deadStore()I
	.limit stack 1
	.limit locals 2

	ldc 100
	istore_1        ; dead store: x = 100, never read before next store
	ldc 200
	istore_1        ; x = 200
	iload_1
	ireturn
.end method

; Constant branch dead code test:
; Condition (0 != 0) is always false, so the if-branch body is unreachable dead code.
; After branch simplification removes the always-false branch, dead code elimination
; removes the unreachable instructions. The method should return 42.
.method public alwaysFalseBranchDeadCode()I
	.limit stack 1
	.limit locals 2

	ldc 0
	istore_1        ; flag = 0
	iload_1
	ifne TrueBranch ; 0 != 0 is always false -> dead code follows
	ldc 42
	ireturn
TrueBranch:
	ldc 99          ; unreachable dead code
	ireturn
.end method