package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;


public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	/**
	 * Helper function for sub-goal 1:
	 * Extract a numeric constant value from an instruction, or null if not a constant.
	 */
	private Number getConstantValue(Instruction inst, ConstantPoolGen cpgen) {
		// get the value if a constant push instruction is passed as value is
		// directly encoded in instruction itself
		if (inst instanceof ConstantPushInstruction) {
			return ((ConstantPushInstruction) inst).getValue();
		} else if (inst instanceof LDC) { // constant from constant fpool for ints/floats
			Object val = ((LDC) inst).getValue(cpgen);
			if (val instanceof Number) {
				return (Number) val;
			}
		} else if (inst instanceof LDC2_W) { // wide constant for longs/doubles
			return ((LDC2_W) inst).getValue(cpgen);
		}
		return null;
	}

	/**
	 * Helper function for sub-goal 1:
	 * Attempt to fold a binary arithmetic operation on two constant operands.
	 * Returns the result, or null if the operation cannot be folded.
	 * It handles all 4 types (int, log, float, double) and its operations
	 * including comparsion operations. It returns null if the
	 * operation is not recognised or if the operation cause a divide by 0 error.
	 */
	private Number foldBinaryOp(Number val1, Number val2, Instruction op) {
		// integer arithmetic
		if (op instanceof IADD) return val1.intValue() + val2.intValue();
		if (op instanceof ISUB) return val1.intValue() - val2.intValue();
		if (op instanceof IMUL) return val1.intValue() * val2.intValue();
		if (op instanceof IDIV) {
			if (val2.intValue() == 0) return null;
			return val1.intValue() / val2.intValue();
		}
		if (op instanceof IREM) {
			if (val2.intValue() == 0) return null;
			return val1.intValue() % val2.intValue();
		}

		// long arithmetic
		if (op instanceof LADD) return val1.longValue() + val2.longValue();
		if (op instanceof LSUB) return val1.longValue() - val2.longValue();
		if (op instanceof LMUL) return val1.longValue() * val2.longValue();
		if (op instanceof LDIV) {
			if (val2.longValue() == 0L) return null;
			return val1.longValue() / val2.longValue();
		}
		if (op instanceof LREM) {
			if (val2.longValue() == 0L) return null;
			return val1.longValue() % val2.longValue();
		}

		// float arithmetic
		if (op instanceof FADD) return val1.floatValue() + val2.floatValue();
		if (op instanceof FSUB) return val1.floatValue() - val2.floatValue();
		if (op instanceof FMUL) return val1.floatValue() * val2.floatValue();
		if (op instanceof FDIV) return val1.floatValue() / val2.floatValue();
		if (op instanceof FREM) return val1.floatValue() % val2.floatValue();

		// double arithmetic
		if (op instanceof DADD) return val1.doubleValue() + val2.doubleValue();
		if (op instanceof DSUB) return val1.doubleValue() - val2.doubleValue();
		if (op instanceof DMUL) return val1.doubleValue() * val2.doubleValue();
		if (op instanceof DDIV) return val1.doubleValue() / val2.doubleValue();
		if (op instanceof DREM) return val1.doubleValue() % val2.doubleValue();

		// comparison operations (result is always int)
		if (op instanceof LCMP) {
			long l1 = val1.longValue(), l2 = val2.longValue();
			return (l1 > l2) ? 1 : (l1 == l2) ? 0 : -1;
		}
		if (op instanceof FCMPL || op instanceof FCMPG) {
			float f1 = val1.floatValue(), f2 = val2.floatValue();
			if (Float.isNaN(f1) || Float.isNaN(f2)) {
				return (op instanceof FCMPL) ? -1 : 1;
			}
			return (f1 > f2) ? 1 : (f1 == f2) ? 0 : -1;
		}
		if (op instanceof DCMPL || op instanceof DCMPG) {
			double d1 = val1.doubleValue(), d2 = val2.doubleValue();
			if (Double.isNaN(d1) || Double.isNaN(d2)) {
				return (op instanceof DCMPL) ? -1 : 1;
			}
			return (d1 > d2) ? 1 : (d1 == d2) ? 0 : -1;
		}

		return null;
	}

	/**
	 * Helper function for sub-goal 1:
	 * Create a push instruction for the folded result, matching the result type of the operation.
	 * It takes in a folded result and figures out what type of push instruction to emit based on original operation.
	 */
	private Instruction createPushInstruction(Number value, Instruction op, ConstantPoolGen cpgen) {
		// operations that produce int results - call pushInt
		if (op instanceof IADD || op instanceof ISUB || op instanceof IMUL ||
			op instanceof IDIV || op instanceof IREM ||
			op instanceof LCMP || op instanceof FCMPL || op instanceof FCMPG ||
			op instanceof DCMPL || op instanceof DCMPG) {
			return pushInt(value.intValue(), cpgen);
		}
		// operations that produce long results - call pushLong
		if (op instanceof LADD || op instanceof LSUB || op instanceof LMUL ||
			op instanceof LDIV || op instanceof LREM) {
			return pushLong(value.longValue(), cpgen);
		}
		// operations that produce float results - call pushFloat
		if (op instanceof FADD || op instanceof FSUB || op instanceof FMUL ||
			op instanceof FDIV || op instanceof FREM) {
			return pushFloat(value.floatValue(), cpgen);
		}
		// operations that produce double results - pushDouble
		if (op instanceof DADD || op instanceof DSUB || op instanceof DMUL ||
			op instanceof DDIV || op instanceof DREM) {
			return pushDouble(value.doubleValue(), cpgen);
		}
		return null;
	}

	// Helper instructions for sub-goal 1: handle push operations based on the data type
	private Instruction pushInt(int value, ConstantPoolGen cpgen) {
		// ICONST for -1 to 5, BIPUSH for byte-range values, SIPUSH for short-range and LDC for everything else
		if (value >= -1 && value <= 5) return new ICONST(value);
		if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return new BIPUSH((byte) value);
		if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return new SIPUSH((short) value);
		return new LDC(cpgen.addInteger(value));
	}

	private Instruction pushLong(long value, ConstantPoolGen cpgen) {
		// pushLong has only 2 as bytecode spec only defines LCONST_0 and LCONST_1 as special cases
		// LCONST for 0L/1L (baked directly into instruction hence no constant pool needed)
		if (value == 0L) return new LCONST(0);
		if (value == 1L) return new LCONST(1);

		// for everything else as it stores long value in constant pool and references it by index
		return new LDC2_W(cpgen.addLong(value));
	}

	private Instruction pushFloat(float value, ConstantPoolGen cpgen) {
		// use LDC for all float values to avoid edge cases with FCONST and negative zero
		return new LDC(cpgen.addFloat(value));
	}

	private Instruction pushDouble(double value, ConstantPoolGen cpgen) {
		// use LDC2_W for all double values to avoid edge cases with DCONST and negative zero
		return new LDC2_W(cpgen.addDouble(value));
	}


	/**
	* Helper function for sub-goal 1:
	*/
	private void safeDelete(InstructionList il, InstructionHandle from, InstructionHandle to, InstructionHandle redirectTo) {
		// tries to delete range of instructions from instruction list, but handles the case where some other instruction was pointing
		// to one of the deleted instructions where it will redirect those targets to `redirectTo` instead of crashing (TargetLostException)
		try {
			il.delete(from, to);
		} catch (TargetLostException e) {
			for (InstructionHandle target : e.getTargets()) {
				for (InstructionTargeter targeter : target.getTargeters()) {
					targeter.updateTarget(target, redirectTo);
				}
			}
		}
	}

	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		// Implement your optimisation here

		Method[] methods = cgen.getMethods();
		for (Method method : methods) {
			if (method.getCode() == null) continue;

			// build a MethodGen and get instruction list, before calling foldConstant
			MethodGen mg = new MethodGen(method, cgen.getClassName(), cpgen);
			InstructionList il = mg.getInstructionList();

			foldConstants(il, cpgen);

			// update method back to class
			mg.setMaxStack();
			mg.setMaxLocals();
			cgen.replaceMethod(method, mg.getMethod());
		}

		this.optimized = cgen.getJavaClass();
	}

	/**
	 * SUBGOAL 1 (Declan): Performs simple constant folding: find patterns of (const, const, arithmetic_op)
	 * and replace with the computed result. Repeats until no more folding is possible.
	 */
	private void foldConstants(InstructionList il, ConstantPoolGen cpgen) {
		boolean changed = true;

		// keep running until a full pass finds nothing to fold
		while (changed) {
			changed = false;
			InstructionHandle[] handles = il.getInstructionHandles();

			// keep scanning the instruction list looking for 3 instruction pattern (const, const, arithmetic operation)
			for (int i = 0; i < handles.length - 2; i++) {
				Number val1 = getConstantValue(handles[i].getInstruction(), cpgen);
				if (val1 == null) continue;

				Number val2 = getConstantValue(handles[i + 1].getInstruction(), cpgen);
				if (val2 == null) continue;

				Instruction arithInst = handles[i + 2].getInstruction();
				Number result = foldBinaryOp(val1, val2, arithInst);
				if (result == null) continue;

				Instruction newInst = createPushInstruction(result, arithInst, cpgen);
				if (newInst == null) continue;

				// we found the pattern 
				// replace the first instruction with the folded constant
				handles[i].setInstruction(newInst);
				// delete the second constant and the arithmetic operation
				safeDelete(il, handles[i + 1], handles[i + 2], handles[i]);
				changed = true;
				break; // restart scan since handles are now stale
			}
		}
	}


	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}
